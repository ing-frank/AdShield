package com.adshield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.adshield.AdShieldApp
import com.adshield.MainActivity
import com.adshield.R
import com.adshield.domain.model.ProtectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * VPN local de AD Shield.
 *
 * Solo se enruta hacia la VPN una dirección: la del servidor DNS virtual. Así, lo único
 * que llega a este servicio son las consultas DNS de las aplicaciones; el resto del
 * tráfico (páginas, vídeos, mensajes) sigue por la conexión normal y nunca pasa por aquí.
 *
 * Para cada consulta se mira el nombre de dominio:
 *  - bloqueado  → se responde localmente con 0.0.0.0 / ::
 *  - permitido  → se reenvía al DNS de la red y la respuesta se devuelve a la aplicación
 *
 * No hay servidor externo propio, no se descifra HTTPS y no se instalan certificados.
 */
class AdShieldVpnService : VpnService() {

    private val container by lazy { (application as AdShieldApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appByUid = ConcurrentHashMap<Int, String>()
    private lateinit var connectivity: ConnectivityManager

    @Volatile
    private var tunnel: Tunnel? = null

    @Volatile
    private var upstream: List<InetAddress> = FALLBACK_DNS

    @Volatile
    private var pool: ThreadPoolExecutor? = null

    @Volatile
    private var showCounter = true

    @Volatile
    var protectionRunning: Boolean = false
        private set

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        connectivity = getSystemService(ConnectivityManager::class.java)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown(null)
            return START_NOT_STICKY
        }
        startProtection()
        // START_STICKY: si Android mata el proceso, vuelve a crear el servicio y la protección.
        return START_STICKY
    }

    /** El usuario desconectó la VPN desde los ajustes de Android u otra VPN tomó el control. */
    override fun onRevoke() {
        shutdown(null)
    }

    override fun onDestroy() {
        if (protectionRunning) shutdown(null)
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun stopProtection() = shutdown(null)

    // ------------------------------------------------------------------ inicio / parada

    private fun startProtection() {
        synchronized(this) {
            if (protectionRunning) return
            protectionRunning = true
        }
        container.vpnManager.update(VpnState(ProtectionState.STARTING))
        startForegroundSafely()

        scope.launch {
            try {
                if (VpnService.prepare(this@AdShieldVpnService) != null) {
                    throw IllegalStateException("Permiso de VPN no concedido")
                }
                val settings = container.settingsRepository.settings.first()
                showCounter = settings.notificationCounter
                container.ruleLoader.ensureLoaded(settings.mode)

                refreshUpstream()
                registerNetworkCallback()
                pool = ThreadPoolExecutor(
                    FORWARD_THREADS, FORWARD_THREADS, 30, TimeUnit.SECONDS,
                    LinkedBlockingQueue(FORWARD_QUEUE), ThreadPoolExecutor.DiscardOldestPolicy()
                ).apply { allowCoreThreadTimeOut(true) }

                val descriptor = establishTunnel()
                if (!protectionRunning) {
                    descriptor.close()
                    return@launch
                }
                tunnel = Tunnel(descriptor).also { it.start() }
                container.trafficRecorder.start()
                startNotificationUpdates()
                container.vpnManager.update(
                    VpnState(ProtectionState.ACTIVE, since = System.currentTimeMillis())
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar la protección", e)
                shutdown(e.message ?: "No se pudo iniciar la VPN")
            }
        }
    }

    /** Recrea el túnel con la configuración actual sin dejar un hueco sin protección. */
    fun reconfigure() {
        if (!protectionRunning) return
        scope.launch {
            try {
                val descriptor = establishTunnel()
                val old = tunnel
                tunnel = Tunnel(descriptor).also { it.start() }
                old?.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo reconfigurar la VPN", e)
                shutdown(e.message ?: "No se pudo reconfigurar la VPN")
            }
        }
    }

    @Synchronized
    private fun shutdown(error: String?) {
        protectionRunning = false
        notificationJob?.cancel()
        notificationJob = null
        networkCallback?.let { callback ->
            try {
                connectivity.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo anular el registro de red", e)
            }
        }
        networkCallback = null
        tunnel?.stop()
        tunnel = null
        pool?.shutdownNow()
        pool = null
        container.trafficRecorder.stop()
        container.vpnManager.update(
            if (error != null) VpnState(ProtectionState.ERROR, error) else VpnState()
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun establishTunnel(): ParcelFileDescriptor {
        val builder = Builder()
            .setSession("AD Shield")
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, 24)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)   // única ruta: solo el DNS virtual entra en la VPN
            .setBlocking(true)
            .setConfigureIntent(mainPendingIntent())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        // AD Shield no se filtra a sí mismo (así puede descargar sus listas).
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "No se pudo excluir la propia app", e)
        }
        for (excluded in container.appRuleRepository.excludedPackages()) {
            try {
                builder.addDisallowedApplication(excluded)
            } catch (e: PackageManager.NameNotFoundException) {
                // La app fue desinstalada; se ignora la regla.
            }
        }
        return builder.establish()
            ?: throw IllegalStateException("Android no permitió crear la interfaz VPN")
    }

    // ------------------------------------------------------------------ DNS de la red

    @Suppress("DEPRECATION")
    private fun refreshUpstream() {
        val found = ArrayList<Pair<Int, InetAddress>>()
        try {
            for (network in connectivity.allNetworks) {
                val caps = connectivity.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                var score = 0
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 2
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) score += 1
                connectivity.getLinkProperties(network)?.dnsServers?.forEach { found.add(score to it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron leer los DNS de la red", e)
        }
        // Primero los DNS de la red activa; los públicos solo como respaldo.
        upstream = (found.sortedByDescending { it.first }.map { it.second } + FALLBACK_DNS).distinct()
    }

    private fun registerNetworkCallback() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshUpstream()
            override fun onLost(network: Network) = refreshUpstream()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refreshUpstream()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivity.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo observar la red", e)
        }
    }

    /**
     * Intenta saber qué aplicación hizo la consulta (Android 10+). Android no siempre
     * lo permite; en ese caso el evento queda sin aplicación asociada.
     */
    private fun resolveApp(datagram: UdpDatagram): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val uid = connectivity.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(datagram.srcIp), datagram.srcPort),
                InetSocketAddress(InetAddress.getByAddress(datagram.dstIp), datagram.dstPort)
            )
            if (uid == Process.INVALID_UID) return null
            appByUid[uid] ?: packageManager.getPackagesForUid(uid)?.firstOrNull()?.also { appByUid[uid] = it }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ túnel

    private inner class Tunnel(private val descriptor: ParcelFileDescriptor) {
        private val pipe: Array<FileDescriptor> = Os.pipe()
        private val output = FileOutputStream(descriptor.fileDescriptor)
        private val thread = Thread({ run() }, "adshield-tunnel")

        @Volatile
        private var stopped = false

        fun start() = thread.start()

        fun stop() {
            stopped = true
            try {
                Os.write(pipe[1], byteArrayOf(1), 0, 1) // despierta al hilo bloqueado en poll()
            } catch (e: Exception) {
                // El hilo ya terminó y cerró la tubería.
            }
        }

        private fun run() {
            val input = FileInputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(BUFFER_SIZE)
            val pollTunnel = StructPollfd().apply {
                fd = descriptor.fileDescriptor
                events = OsConstants.POLLIN.toShort()
            }
            val pollStop = StructPollfd().apply {
                fd = pipe[0]
                events = OsConstants.POLLIN.toShort()
            }
            val pollSet = arrayOf(pollTunnel, pollStop)
            var failure: String? = null
            try {
                while (!stopped) {
                    pollTunnel.revents = 0
                    pollStop.revents = 0
                    try {
                        Os.poll(pollSet, -1)
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EINTR) continue
                        throw e
                    }
                    if (pollStop.revents.toInt() != 0) break
                    val ready = pollTunnel.revents.toInt()
                    if (ready and OsConstants.POLLIN != 0) {
                        val length = input.read(buffer)
                        if (length < 0) throw IOException("La interfaz VPN se cerró")
                        if (length > 0) handlePacket(buffer, length)
                    } else if (ready != 0) {
                        throw IOException("Android cerró la interfaz VPN")
                    }
                }
            } catch (e: Exception) {
                if (!stopped) failure = e.message ?: "Error de lectura en la VPN"
            } finally {
                closeQuietly { descriptor.close() }
                closeQuietly { Os.close(pipe[0]) }
                closeQuietly { Os.close(pipe[1]) }
            }
            if (failure != null && tunnel === this) shutdown(failure)
        }

        private fun handlePacket(buffer: ByteArray, length: Int) {
            val datagram = DnsPacket.parseIpv4Udp(buffer, length) ?: return
            if (datagram.dstPort != DNS_PORT) return

            val question = DnsPacket.parseQuestion(datagram.payload)
            if (question != null) {
                val result = container.engine.check(question.name)
                container.trafficRecorder.record(question.name, result, resolveApp(datagram))
                if (result.isBlocked) {
                    reply(datagram, DnsPacket.buildBlockedResponse(datagram.payload, question))
                    return
                }
            }
            val executor = pool ?: return
            try {
                executor.execute { forward(datagram, question) }
            } catch (e: RejectedExecutionException) {
                // El servicio se está deteniendo.
            }
        }

        /** Reenvía la consulta al DNS real por un socket que no pasa por la VPN. */
        private fun forward(datagram: UdpDatagram, question: DnsQuestion?) {
            for (server in upstream.take(MAX_UPSTREAM_TRIES)) {
                if (stopped) return
                try {
                    DatagramSocket().use { socket ->
                        if (!protect(socket)) throw IOException("No se pudo proteger el socket")
                        socket.soTimeout = UPSTREAM_TIMEOUT_MS
                        socket.connect(server, DNS_PORT)
                        socket.send(DatagramPacket(datagram.payload, datagram.payload.size))
                        val responseBuffer = ByteArray(MAX_DNS_RESPONSE)
                        val response = DatagramPacket(responseBuffer, responseBuffer.size)
                        socket.receive(response)
                        reply(datagram, responseBuffer.copyOf(response.length))
                    }
                    return
                } catch (e: Exception) {
                    // Se prueba el siguiente servidor.
                }
            }
            // Ningún servidor respondió: se avisa a la app para que no espere en vano.
            if (question != null) reply(datagram, DnsPacket.buildServerFailure(datagram.payload, question))
        }

        private fun reply(query: UdpDatagram, payload: ByteArray) {
            val packet = DnsPacket.wrapUdpIpv4(query.dstIp, query.dstPort, query.srcIp, query.srcPort, payload)
            synchronized(output) {
                try {
                    output.write(packet)
                } catch (e: IOException) {
                    // El túnel se cerró mientras se respondía.
                }
            }
        }
    }

    private inline fun closeQuietly(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Ya estaba cerrado.
        }
    }

    // ------------------------------------------------------------------ notificación

    private fun startForegroundSafely() {
        try {
            val notification = buildNotification(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo pasar a primer plano", e)
        }
    }

    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            launch {
                container.settingsRepository.settings.collect { showCounter = it.notificationCounter }
            }
            val manager = getSystemService(NotificationManager::class.java)
            var shown: Pair<Long, Boolean>? = null
            while (isActive) {
                val current = container.trafficRecorder.sessionBlocked.value to showCounter
                if (current != shown) {
                    manager.notify(NOTIFICATION_ID, buildNotification(current.first))
                    shown = current
                }
                delay(NOTIFICATION_REFRESH_MS)
            }
        }
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(blocked: Long): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Protección", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indica que AD Shield está filtrando"
                setShowBadge(false)
            }
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AdShieldVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (showCounter) "Bloqueos en esta sesión: $blocked" else "Filtrando publicidad y rastreadores"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("AD Shield · Protección activa")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent())
            .addAction(0, "Detener protección", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.adshield.vpn.START"
        const val ACTION_STOP = "com.adshield.vpn.STOP"

        private const val TAG = "AdShieldVpn"
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_REFRESH_MS = 15_000L

        private const val VPN_ADDRESS = "10.215.173.1"
        private const val VPN_DNS = "10.215.173.2"
        private const val DNS_PORT = 53
        private const val MTU = 9000
        private const val BUFFER_SIZE = 32767
        private const val MAX_DNS_RESPONSE = 4096
        private const val UPSTREAM_TIMEOUT_MS = 3000
        private const val MAX_UPSTREAM_TRIES = 3
        private const val FORWARD_THREADS = 8
        private const val FORWARD_QUEUE = 512

        /** DNS públicos usados solo si la red no informa de ninguno (Cloudflare y Quad9). */
        private val FALLBACK_DNS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(9, 9, 9, 9))
        )

        /** Referencia al servicio en ejecución, para detenerlo sin lanzar otro Intent. */
        @Volatile
        var instance: AdShieldVpnService? = null
            private set
    }
}
