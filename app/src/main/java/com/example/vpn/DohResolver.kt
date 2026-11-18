package com.example.vpn

import android.util.LruCache
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DohResolver(
  private val dohUrl: String,
  private val bootstrapIp: String,
  private val protectSocket: (Socket) -> Boolean,
) {
  private data class CacheItem(
    val responseBytes: ByteArray,
    val expiresAtMs: Long,
  )

  private val cache = LruCache<String, CacheItem>(512)
  private val dnsMessageMediaType = "application/dns-message".toMediaType()

  private val protectedSocketFactory = object : SocketFactory() {
    private val defaultFactory = getDefault()

    override fun createSocket(): Socket =
      defaultFactory.createSocket().also { protectSocket(it) }

    override fun createSocket(host: String?, port: Int): Socket =
      defaultFactory.createSocket(host, port).also { protectSocket(it) }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
      defaultFactory.createSocket(host, port, localHost, localPort).also { protectSocket(it) }

    override fun createSocket(host: InetAddress?, port: Int): Socket =
      defaultFactory.createSocket(host, port).also { protectSocket(it) }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
      defaultFactory.createSocket(address, port, localAddress, localPort).also { protectSocket(it) }
  }

  private val bootstrapDns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
      return when (hostname.lowercase()) {
        "cloudflare-dns.com", "one.one.one.one" ->
          listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
        "dns.google", "dns.google.com" ->
          listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
        "dns.quad9.net" ->
          listOf(InetAddress.getByName("9.9.9.9"), InetAddress.getByName("149.112.112.112"))
        else -> {
          val fallback = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull()
          if (!fallback.isNullOrEmpty()) fallback else listOf(InetAddress.getByName(bootstrapIp))
        }
      }
    }
  }

  private val client = OkHttpClient.Builder()
    .socketFactory(protectedSocketFactory)
    .dns(bootstrapDns)
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .writeTimeout(6, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  fun resolveRawDns(queryPayload: ByteArray, domain: String, queryType: String): ByteArray {
    if (queryPayload.size < 12) return queryPayload

    val cacheKey = "${domain.lowercase()}|$queryType"
    val now = System.currentTimeMillis()
    val cached = synchronized(cache) { cache.get(cacheKey) }
    if (cached != null && cached.expiresAtMs > now) {
      val patched = cached.responseBytes.copyOf()
      // Copy original transaction ID (bytes 0..1)
      patched[0] = queryPayload[0]
      patched[1] = queryPayload[1]
      return patched
    }

    val request = Request.Builder()
      .url(dohUrl)
      .post(queryPayload.toRequestBody(dnsMessageMediaType))
      .header("Accept", "application/dns-message")
      .header("Content-Type", "application/dns-message")
      .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("DoH HTTP ${response.code}")
      }
      val bytes = response.body?.bytes() ?: throw IOException("Empty DoH body")
      if (bytes.size >= 12 && domain.isNotBlank()) {
        synchronized(cache) {
          cache.put(cacheKey, CacheItem(bytes, now + 45_000L))
        }
      }
      return bytes
    }
  }

  fun resolveHostAddresses(hostname: String): List<InetAddress> {
    val cleanHost = hostname.trim()
    if (cleanHost.isEmpty()) return emptyList()

    // Check if already an IP literal
    if (cleanHost.all { it.isDigit() || it == '.' } || cleanHost.contains(':')) {
      return runCatching { listOf(InetAddress.getByName(cleanHost)) }.getOrDefault(emptyList())
    }

    val query = DnsPacketCodec.buildDnsQuery(cleanHost, queryType = 1)
    val response = runCatching { resolveRawDns(query, cleanHost, "A") }.getOrNull()
    if (response != null) {
      val ips = DnsPacketCodec.extractResolvedAddresses(response)
      if (ips.isNotEmpty()) {
        return ips.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
      }
    }
    return runCatching { InetAddress.getAllByName(cleanHost).toList() }.getOrDefault(emptyList())
  }
}
