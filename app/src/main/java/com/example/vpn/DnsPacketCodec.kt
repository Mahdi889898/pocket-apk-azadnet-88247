package com.example.vpn

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class ParsedDnsPacket(
  val srcIp: ByteArray,
  val dstIp: ByteArray,
  val srcPort: Int,
  val dstPort: Int,
  val ipId: Int,
  val dnsPayload: ByteArray,
  val transactionId: Int,
  val queryDomain: String,
  val queryTypeName: String,
)

object DnsPacketCodec {

  fun parseIpUdpDns(packet: ByteArray, length: Int): ParsedDnsPacket? {
    if (length < 20) return null
    val version = (packet[0].toInt() ushr 4) and 0x0F
    if (version != 4) return null

    val ihl = (packet[0].toInt() and 0x0F) * 4
    if (ihl < 20 || length < ihl + 8 + 12) return null

    val protocol = packet[9].toInt() and 0xFF
    if (protocol != 17) return null // UDP only

    val totalLength = readUInt16(packet, 2)
    val effectiveLen = minOf(length, totalLength)
    if (effectiveLen < ihl + 8 + 12) return null

    val srcIp = packet.copyOfRange(12, 16)
    val dstIp = packet.copyOfRange(16, 20)
    val ipId = readUInt16(packet, 4)

    val srcPort = readUInt16(packet, ihl)
    val dstPort = readUInt16(packet, ihl + 2)
    if (dstPort != 53) return null

    val udpLen = readUInt16(packet, ihl + 4)
    val dnsStart = ihl + 8
    val dnsEnd = minOf(effectiveLen, ihl + udpLen)
    if (dnsEnd - dnsStart < 12) return null

    val dnsPayload = packet.copyOfRange(dnsStart, dnsEnd)
    val transactionId = readUInt16(dnsPayload, 0)
    val (qName, qType) = parseQuestion(dnsPayload)

    return ParsedDnsPacket(
      srcIp = srcIp,
      dstIp = dstIp,
      srcPort = srcPort,
      dstPort = dstPort,
      ipId = ipId,
      dnsPayload = dnsPayload,
      transactionId = transactionId,
      queryDomain = qName,
      queryTypeName = qType,
    )
  }

  fun buildIpUdpDnsResponse(request: ParsedDnsPacket, dnsResponsePayload: ByteArray): ByteArray {
    val ipHeaderLen = 20
    val udpHeaderLen = 8
    val totalLen = ipHeaderLen + udpHeaderLen + dnsResponsePayload.size
    val out = ByteArray(totalLen)

    // IPv4 Header (20 bytes)
    out[0] = 0x45.toByte()
    out[1] = 0x00.toByte()
    writeUInt16(out, 2, totalLen)
    writeUInt16(out, 4, request.ipId xor 0x5A5A)
    out[6] = 0x40.toByte() // Don't Fragment
    out[7] = 0x00.toByte()
    out[8] = 64.toByte() // TTL
    out[9] = 17.toByte() // UDP
    // Checksum bytes 10..11 left 0 before computation
    // Swap source and destination IPs
    System.arraycopy(request.dstIp, 0, out, 12, 4)
    System.arraycopy(request.srcIp, 0, out, 16, 4)

    val ipChecksum = computeChecksum(out, 0, ipHeaderLen)
    writeUInt16(out, 10, ipChecksum)

    // UDP Header (8 bytes)
    val udpStart = ipHeaderLen
    val udpLen = udpHeaderLen + dnsResponsePayload.size
    writeUInt16(out, udpStart, request.dstPort)
    writeUInt16(out, udpStart + 2, request.srcPort)
    writeUInt16(out, udpStart + 4, udpLen)
    // Checksum bytes udpStart+6..udpStart+7 left 0 before computation

    // DNS Payload
    System.arraycopy(dnsResponsePayload, 0, out, udpStart + udpHeaderLen, dnsResponsePayload.size)

    val udpChecksum = computeUdpChecksum(
      srcIp = request.dstIp,
      dstIp = request.srcIp,
      udpPacket = out,
      udpOffset = udpStart,
      udpLength = udpLen,
    )
    writeUInt16(out, udpStart + 6, udpChecksum)

    return out
  }

  fun buildServFailResponse(queryPayload: ByteArray): ByteArray {
    if (queryPayload.size < 12) return queryPayload
    val resp = queryPayload.copyOf()
    // Set QR=1 (response), preserve RD, set RCODE=2 (SERVFAIL)
    val flags0 = resp[2].toInt() and 0xFF
    resp[2] = (flags0 or 0x80).toByte()
    resp[3] = 0x82.toByte() // RA=1, RCODE=2
    // Zero answer/authority/additional counts
    resp[6] = 0
    resp[7] = 0
    resp[8] = 0
    resp[9] = 0
    resp[10] = 0
    resp[11] = 0
    return resp
  }

  fun buildDnsQuery(domain: String, queryType: Int = 1, transactionId: Int = 0x1A2B): ByteArray {
    val labels = domain.trimEnd('.').split('.').filter { it.isNotEmpty() }
    val qNameSize = labels.sumOf { 1 + it.toByteArray(Charsets.UTF_8).size } + 1
    val out = ByteArray(12 + qNameSize + 4)
    writeUInt16(out, 0, transactionId)
    out[2] = 0x01.toByte() // Standard query + RD=1
    out[3] = 0x00.toByte()
    writeUInt16(out, 4, 1) // QDCOUNT = 1

    var pos = 12
    for (label in labels) {
      val bytes = label.toByteArray(Charsets.UTF_8)
      val len = minOf(bytes.size, 63)
      out[pos++] = len.toByte()
      System.arraycopy(bytes, 0, out, pos, len)
      pos += len
    }
    out[pos++] = 0x00
    writeUInt16(out, pos, queryType)
    writeUInt16(out, pos + 2, 1) // IN class
    return out
  }

  fun extractResolvedAddresses(dnsResponse: ByteArray): List<String> {
    if (dnsResponse.size < 12) return emptyList()
    val qdCount = readUInt16(dnsResponse, 4)
    val anCount = readUInt16(dnsResponse, 6)
    if (anCount == 0) return emptyList()

    var offset = 12
    // Skip questions
    repeat(qdCount) {
      offset = skipName(dnsResponse, offset)
      if (offset < 0 || offset + 4 > dnsResponse.size) return emptyList()
      offset += 4
    }

    val results = mutableListOf<String>()
    repeat(anCount) {
      offset = skipName(dnsResponse, offset)
      if (offset < 0 || offset + 10 > dnsResponse.size) return results
      val type = readUInt16(dnsResponse, offset)
      val rdLength = readUInt16(dnsResponse, offset + 8)
      val rDataStart = offset + 10
      if (rDataStart + rdLength > dnsResponse.size) return results
      if (type == 1 && rdLength == 4) {
        val addr = Inet4Address.getByAddress(dnsResponse.copyOfRange(rDataStart, rDataStart + 4))
        addr.hostAddress?.let { results.add(it) }
      } else if (type == 28 && rdLength == 16) {
        val addr = Inet6Address.getByAddress(dnsResponse.copyOfRange(rDataStart, rDataStart + 16))
        addr.hostAddress?.let { results.add(it) }
      }
      offset = rDataStart + rdLength
    }
    return results
  }

  private fun parseQuestion(dns: ByteArray): Pair<String, String> {
    if (dns.size < 13) return "" to "A"
    val qdCount = readUInt16(dns, 4)
    if (qdCount == 0) return "" to "A"

    val sb = StringBuilder()
    var pos = 12
    var jumps = 0
    while (pos < dns.size && jumps < 64) {
      val len = dns[pos].toInt() and 0xFF
      if (len == 0) {
        pos++
        break
      }
      if ((len and 0xC0) == 0xC0) {
        pos += 2
        break
      }
      pos++
      if (pos + len > dns.size) break
      if (sb.isNotEmpty()) sb.append('.')
      sb.append(String(dns, pos, len, Charsets.UTF_8))
      pos += len
      jumps++
    }

    val qType = if (pos + 2 <= dns.size) readUInt16(dns, pos) else 1
    val qTypeName = when (qType) {
      1 -> "A"
      28 -> "AAAA"
      65 -> "HTTPS"
      16 -> "TXT"
      5 -> "CNAME"
      else -> "T$qType"
    }
    return sb.toString() to qTypeName
  }

  private fun skipName(dns: ByteArray, startOffset: Int): Int {
    var pos = startOffset
    var steps = 0
    while (pos < dns.size && steps < 128) {
      val len = dns[pos].toInt() and 0xFF
      if (len == 0) return pos + 1
      if ((len and 0xC0) == 0xC0) return pos + 2
      pos += 1 + len
      steps++
    }
    return -1
  }

  private fun readUInt16(data: ByteArray, offset: Int): Int {
    return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
  }

  private fun writeUInt16(data: ByteArray, offset: Int, value: Int) {
    data[offset] = ((value ushr 8) and 0xFF).toByte()
    data[offset + 1] = (value and 0xFF).toByte()
  }

  private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
    var sum = 0L
    var i = offset
    val end = offset + length
    while (i + 1 < end) {
      sum += (((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)).toLong()
      i += 2
    }
    if (i < end) {
      sum += ((data[i].toInt() and 0xFF) shl 8).toLong()
    }
    while ((sum ushr 16) != 0L) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }
    return (sum.inv() and 0xFFFF).toInt()
  }

  private fun computeUdpChecksum(
    srcIp: ByteArray,
    dstIp: ByteArray,
    udpPacket: ByteArray,
    udpOffset: Int,
    udpLength: Int,
  ): Int {
    var sum = 0L
    sum += (((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)).toLong()
    sum += (((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)).toLong()
    sum += (((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)).toLong()
    sum += (((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)).toLong()
    sum += 17L // UDP protocol
    sum += udpLength.toLong()

    var i = udpOffset
    val end = udpOffset + udpLength
    while (i + 1 < end) {
      sum += (((udpPacket[i].toInt() and 0xFF) shl 8) or (udpPacket[i + 1].toInt() and 0xFF)).toLong()
      i += 2
    }
    if (i < end) {
      sum += ((udpPacket[i].toInt() and 0xFF) shl 8).toLong()
    }
    while ((sum ushr 16) != 0L) {
      sum = (sum and 0xFFFF) + (sum ushr 16)
    }
    val finalChecksum = (sum.inv() and 0xFFFF).toInt()
    return if (finalChecksum == 0) 0xFFFF else finalChecksum
  }
}
