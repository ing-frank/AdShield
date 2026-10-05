package com.adshield.data.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import com.adshield.data.database.StatisticsDao
import com.adshield.domain.repository.SystemStatusProvider
import com.adshield.vpn.VpnManager

class AndroidSystemStatus(
    context: Context,
    private val statisticsDao: StatisticsDao,
    private val vpnManager: VpnManager
) : SystemStatusProvider {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    @Suppress("DEPRECATION")
    private fun physicalNetworks() = connectivity.allNetworks.filter { network ->
        val caps = connectivity.getNetworkCapabilities(network)
        caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun internetAvailable(): Boolean = try {
        physicalNetworks().any { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
    } catch (e: Exception) {
        false
    }

    override fun vpnPermissionGranted(): Boolean = try {
        VpnService.prepare(appContext) == null
    } catch (e: Exception) {
        false
    }

    override fun strictPrivateDnsHost(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            physicalNetworks().firstNotNullOfOrNull { network ->
                val link = connectivity.getLinkProperties(network)
                if (link != null && link.isPrivateDnsActive) link.privateDnsServerName else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun serviceRunning(): Boolean = vpnManager.isServiceRunning()

    override suspend fun databaseWorking(): Boolean = try {
        statisticsDao.count()
        true
    } catch (e: Exception) {
        false
    }
}
