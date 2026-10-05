package com.adshield.data.blocklist

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Descarga una lista a un archivo temporal. Solo HTTPS y con tamaño máximo. */
class BlocklistDownloader {

    @Throws(IOException::class)
    fun download(url: String, target: File) {
        if (!url.startsWith("https://")) throw IOException("Solo se admiten direcciones HTTPS")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "ADShield/1.0 (Android)")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("El servidor respondió HTTP $code")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("La lista supera el tamaño máximo permitido")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BYTES = 40L * 1024 * 1024
    }
}
