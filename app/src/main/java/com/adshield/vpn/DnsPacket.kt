package com.adshield.vpn

/** Datagrama UDP/IPv4 leído de la interfaz TUN. */
class UdpDatagram(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray
)

/** Primera pregunta de una consulta DNS. [end] es el offset donde termina la sección de pregunta. */
class DnsQuestion(val name: String, val type: Int, val qclass: Int, val end: Int)

/**
 * Lectura y construcción de paquetes. Solo se interpreta lo mínimo necesario:
 * cabeceras IPv4/UDP y el nombre consultado en DNS. Nunca se inspecciona otro tráfico.
 */
object DnsPacket {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val CLASS_IN = 1
    private const val PROTOCOL_UDP = 17
    private const val BLOCK_TTL_SECONDS = 60

    private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int): Int = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }

    /** Devuelve el datagrama si [buffer] contiene un paquete IPv4/UDP completo y no fragmentado. */
    fun parseIpv4Udp(buffer: ByteArray, length: Int): UdpDatagram? {
        if (length < 28 || length > buffer.size) return null
        if ((u8(buffer, 0) shr 4) != 4) return null
        val ihl = (u8(buffer, 0) and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null
        if (u8(buffer, 9) != PROTOCOL_UDP) return null
        // Bit "more fragments" o desplazamiento de fragmento distinto de cero → no se procesa.
        if ((u16(buffer, 6) and 0x3FFF) != 0) return null
        val totalLength = u16(buffer, 2)
        if (totalLength > length || totalLength < ihl + 8) return null
        val udpLength = u16(buffer, ihl + 4)
        if (udpLength < 8 || ihl + udpLength > totalLength) return null
        return UdpDatagram(
            srcIp = buffer.copyOfRange(12, 16),
            dstIp = buffer.copyOfRange(16, 20),
            srcPort = u16(buffer, ihl),
            dstPort = u16(buffer, ihl + 2),
            payload = buffer.copyOfRange(ihl + 8, ihl + udpLength)
        )
    }

    /** Extrae la primera pregunta de una consulta DNS. Devuelve null si no es una consulta válida. */
    fun parseQuestion(payload: ByteArray): DnsQuestion? {
        if (payload.size < 17) return null
        if ((u8(payload, 2) and 0x80) != 0) return null // es una respuesta, no una consulta
        if (u16(payload, 4) < 1) return null
        val name = StringBuilder()
        var pos = 12
        while (true) {
            if (pos >= payload.size) return null
            val len = u8(payload, pos)
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) != 0) return null // compresión: no se espera en preguntas
            if (pos + 1 + len > payload.size) return null
            if (name.isNotEmpty()) name.append('.')
            for (i in 1..len) {
                val c = u8(payload, pos + i)
                name.append(if (c in 'A'.code..'Z'.code) (c + 32).toChar() else c.toChar())
            }
            if (name.length > 253) return null
            pos += 1 + len
        }
        if (pos + 4 > payload.size) return null
        return DnsQuestion(name.toString(), u16(payload, pos), u16(payload, pos + 2), pos + 4)
    }

    private fun responseHeader(query: ByteArray, question: DnsQuestion, size: Int, rcode: Int, answers: Int): ByteArray {
        val out = ByteArray(size)
        System.arraycopy(query, 0, out, 0, question.end)
        out[2] = (0x80 or (u8(query, 2) and 0x79)).toByte() // QR=1, conserva opcode y RD
        out[3] = (0x80 or (rcode and 0x0F)).toByte()        // RA=1 + código de respuesta
        put16(out, 4, 1)
        put16(out, 6, answers)
        put16(out, 8, 0)
        put16(out, 10, 0)
        return out
    }

    /**
     * Respuesta para un dominio bloqueado: 0.0.0.0 para consultas A, :: para AAAA
     * y una respuesta vacía sin error para cualquier otro tipo.
     */
    fun buildBlockedResponse(query: ByteArray, question: DnsQuestion): ByteArray {
        val rdLength = when {
            question.qclass != CLASS_IN -> 0
            question.type == TYPE_A -> 4
            question.type == TYPE_AAAA -> 16
            else -> 0
        }
        if (rdLength == 0) return responseHeader(query, question, question.end, 0, 0)

        val out = responseHeader(query, question, question.end + 12 + rdLength, 0, 1)
        val p = question.end // los bytes de la dirección quedan en cero (0.0.0.0 o ::)
        out[p] = 0xC0.toByte()   // puntero al nombre de la pregunta (offset 12)
        out[p + 1] = 0x0C
        put16(out, p + 2, question.type)
        put16(out, p + 4, CLASS_IN)
        put16(out, p + 6, 0)
        put16(out, p + 8, BLOCK_TTL_SECONDS)
        put16(out, p + 10, rdLength)
        return out
    }

    /** Respuesta SERVFAIL, usada cuando ningún servidor DNS responde. */
    fun buildServerFailure(query: ByteArray, question: DnsQuestion): ByteArray =
        responseHeader(query, question, question.end, 2, 0)

    /** Envuelve [payload] en cabeceras IPv4 + UDP listas para escribir en la interfaz TUN. */
    fun wrapUdpIpv4(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 28 + payload.size
        val out = ByteArray(total)
        out[0] = 0x45
        put16(out, 2, total)
        out[8] = 64
        out[9] = PROTOCOL_UDP.toByte()
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        put16(out, 10, ipChecksum(out, 20))
        put16(out, 20, srcPort)
        put16(out, 22, dstPort)
        put16(out, 24, 8 + payload.size)
        // Checksum UDP = 0: en IPv4 significa "no calculado" y es válido.
        System.arraycopy(payload, 0, out, 28, payload.size)
        return out
    }

    fun ipChecksum(header: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            sum += u16(header, i)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
