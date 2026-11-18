package com.example

import com.example.data.AppCategory
import com.example.data.InstalledAppsInspector
import com.example.data.TelegramProxyParser
import com.example.vpn.DnsPacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DnsAndProxyUnitTest {

  @Test
  fun `dns query roundtrip wraps and parses ip udp packet`() {
    val dnsQuery = DnsPacketCodec.buildDnsQuery("api.telegram.org", queryType = 1, transactionId = 0x4242)
    val ipLen = 20 + 8 + dnsQuery.size
    val packet = ByteArray(ipLen)
    packet[0] = 0x45.toByte()
    packet[2] = ((ipLen ushr 8) and 0xFF).toByte()
    packet[3] = (ipLen and 0xFF).toByte()
    packet[9] = 17.toByte()
    // Src IP 10.120.0.1
    packet[12] = 10; packet[13] = 120; packet[14] = 0; packet[15] = 1
    // Dst IP 10.120.0.2
    packet[16] = 10; packet[17] = 120; packet[18] = 0; packet[19] = 2
    // Src port 51234 -> Dst port 53
    packet[20] = (51234 ushr 8).toByte(); packet[21] = (51234 and 0xFF).toByte()
    packet[22] = 0; packet[23] = 53
    val udpLen = 8 + dnsQuery.size
    packet[24] = ((udpLen ushr 8) and 0xFF).toByte(); packet[25] = (udpLen and 0xFF).toByte()
    System.arraycopy(dnsQuery, 0, packet, 28, dnsQuery.size)

    val parsed = DnsPacketCodec.parseIpUdpDns(packet, packet.size)
    assertNotNull(parsed)
    assertEquals("api.telegram.org", parsed!!.queryDomain)
    assertEquals("A", parsed.queryTypeName)
    assertEquals(0x4242, parsed.transactionId)

    val servFail = DnsPacketCodec.buildServFailResponse(parsed.dnsPayload)
    val responsePacket = DnsPacketCodec.buildIpUdpDnsResponse(parsed, servFail)
    assertEquals(10.toByte(), responsePacket[12])
    assertEquals(2.toByte(), responsePacket[15])
    assertEquals(1.toByte(), responsePacket[19])
    assertEquals(53, ((responsePacket[20].toInt() and 0xFF) shl 8) or (responsePacket[21].toInt() and 0xFF))
  }

  @Test
  fun `telegram proxy parser parses mtproto and socks links`() {
    val mtproto = TelegramProxyParser.parse("https://t.me/proxy?server=1.2.3.4&port=443&secret=dd0011223344")
    assertNotNull(mtproto)
    assertTrue(mtproto!!.isMtproto)
    assertEquals("1.2.3.4", mtproto.server)
    assertEquals(443, mtproto.port)
    assertEquals("dd0011223344", mtproto.secret)

    val socks = TelegramProxyParser.parse("tg://socks?server=127.0.0.1&port=10808")
    assertNotNull(socks)
    assertEquals(false, socks!!.isMtproto)
    assertEquals(10808, socks.port)

    assertNull(TelegramProxyParser.parse("https://example.com/notproxy"))
  }

  @Test
  fun `domain classifier distinguishes telegram and instagram`() {
    assertEquals(AppCategory.TELEGRAM, InstalledAppsInspector.classifyDomain("api.telegram.org"))
    assertEquals(AppCategory.INSTAGRAM, InstalledAppsInspector.classifyDomain("i.instagram.com"))
    assertEquals(AppCategory.INSTAGRAM, InstalledAppsInspector.classifyDomain("scontent.cdninstagram.com"))
    assertEquals(AppCategory.SYSTEM, InstalledAppsInspector.classifyDomain("example.com"))
  }
}
