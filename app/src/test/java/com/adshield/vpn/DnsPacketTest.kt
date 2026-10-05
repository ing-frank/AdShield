package com.adshield.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {

    private fun query(name: String, type: Int, withEdns: Boolean = false): ByteArray {
        val out = ArrayList<Byte>()
        fun b(vararg v: Int) = v.forEach { out.add(it.toByte()) }
        b(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, if (withEdns) 1 else 0)
        name.split('.').forEach { label ->
            b(label.length)
            label.forEach { b(it.code) }
        }
        b(0, type shr 8, type and 0xFF, 0, 1)
        if (withEdns) b(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0)
        return out.toByteArray()
    }

    private val src = byteArrayOf(10, 0, 0, 2)
    private val dst = byteArrayOf(10, 0, 0, 1)

    @Test
    fun parsesQuestionAndLowercasesName() {
        val q = DnsPacket.parseQuestion(query("Ads.Example.COM", DnsPacket.TYPE_A))
        assertNotNull(q)
        assertEquals("ads.example.com", q!!.name)
        assertEquals(DnsPacket.TYPE_A, q.type)
        assertEquals(1, q.qclass)
    }

    @Test
    fun rejectsResponsesAndTruncatedPackets() {
        val response = query("example.com", 1).also { it[2] = (it[2].toInt() or 0x80).toByte() }
        assertNull(DnsPacket.parseQuestion(response))
        assertNull(DnsPacket.parseQuestion(query("example.com", 1).copyOf(20)))
        assertNull(DnsPacket.parseQuestion(ByteArray(5)))
    }

    @Test
    fun rejectsCompressedNamesInQuestion() {
        val bad = query("example.com", 1).also { it[12] = 0xC0.toByte() }
        assertNull(DnsPacket.parseQuestion(bad))
    }

    @Test
    fun blockedResponseForAIsZeroAddress() {
        val q = query("ads.example.com", DnsPacket.TYPE_A, withEdns = true)
        val question = DnsPacket.parseQuestion(q)!!
        val r = DnsPacket.buildBlockedResponse(q, question)
        assertEquals(0x12, r[0].toInt())
        assertEquals(0x34, r[1].toInt())
        assertEquals(0x81, r[2].toInt() and 0xFF)   // QR + RD
        assertEquals(0x80, r[3].toInt() and 0xFF)   // RA, NOERROR
        assertEquals(1, r[7].toInt())               // ANCOUNT
        assertEquals(0, r[11].toInt())              // ARCOUNT: se descarta EDNS
        assertEquals(question.end + 16, r.size)
        assertTrue(r.copyOfRange(r.size - 4, r.size).all { it.toInt() == 0 })
        assertEquals(4, r[r.size - 5].toInt())      // RDLENGTH
    }

    @Test
    fun blockedResponseForAaaaHasSixteenZeroBytes() {
        val q = query("ads.example.com", DnsPacket.TYPE_AAAA)
        val question = DnsPacket.parseQuestion(q)!!
        val r = DnsPacket.buildBlockedResponse(q, question)
        assertEquals(question.end + 28, r.size)
        assertEquals(1, r[7].toInt())
    }

    @Test
    fun blockedResponseForOtherTypesIsEmptyNoError() {
        val q = query("ads.example.com", 65)
        val question = DnsPacket.parseQuestion(q)!!
        val r = DnsPacket.buildBlockedResponse(q, question)
        assertEquals(question.end, r.size)
        assertEquals(0, r[7].toInt())
        assertEquals(0, r[3].toInt() and 0x0F)
    }

    @Test
    fun serverFailureSetsRcodeTwo() {
        val q = query("example.com", 1)
        val r = DnsPacket.buildServerFailure(q, DnsPacket.parseQuestion(q)!!)
        assertEquals(2, r[3].toInt() and 0x0F)
    }

    @Test
    fun wrapAndParseRoundTrip() {
        val payload = query("example.com", 1)
        val packet = DnsPacket.wrapUdpIpv4(src, 40000, dst, 53, payload)
        val parsed = DnsPacket.parseIpv4Udp(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(40000, parsed!!.srcPort)
        assertEquals(53, parsed.dstPort)
        assertTrue(parsed.srcIp.contentEquals(src))
        assertTrue(parsed.dstIp.contentEquals(dst))
        assertTrue(parsed.payload.contentEquals(payload))
    }

    @Test
    fun wrappedHeaderHasValidChecksum() {
        val packet = DnsPacket.wrapUdpIpv4(src, 1234, dst, 53, ByteArray(40))
        // Sumar la cabecera incluyendo su checksum debe dar 0.
        assertEquals(0, DnsPacket.ipChecksum(packet, 20))
    }

    @Test
    fun ignoresNonUdpIpv6AndFragments() {
        val packet = DnsPacket.wrapUdpIpv4(src, 1234, dst, 53, ByteArray(20))
        assertNull(DnsPacket.parseIpv4Udp(packet.copyOf().also { it[9] = 6 }, packet.size))
        assertNull(DnsPacket.parseIpv4Udp(packet.copyOf().also { it[0] = 0x60 }, packet.size))
        assertNull(DnsPacket.parseIpv4Udp(packet.copyOf().also { it[6] = 0x20 }, packet.size))
        assertNull(DnsPacket.parseIpv4Udp(packet, 10))
    }
}
