package com.example.vpn

import android.util.Base64
import com.example.data.AppCategory
import com.example.data.AzadNetSettings
import com.example.data.BypassMode
import com.example.data.DnsLogEntry
import com.example.data.InstalledAppsInspector
import com.example.data.UpstreamProxyType
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalProxyServer(
  private val settings: AzadNetSettings,
  private val dohResolver: DohResolver,
  private val protectSocket: (Socket) -> Boolean,
) {
  private val running = AtomicBoolean(false)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var httpServerSocket: ServerSocket? = null
  private var socksServerSocket: ServerSocket? = null

  var httpPort: Int = 0
    private set

  var socksPort: Int = 0
    private set

  fun start(): Pair<Int, Int> {
    if (running.getAndSet(true)) return httpPort to socksPort

    val loopback = InetAddress.getByName("127.0.0.1")
    httpServerSocket = ServerSocket(0, 64, loopback).also { httpPort = it.localPort }
    socksServerSocket = ServerSocket(0, 64, loopback).also { socksPort = it.localPort }

    scope.launch { acceptLoop(httpServerSocket!!, isSocks = false) }
    scope.launch { acceptLoop(socksServerSocket!!, isSocks = true) }

    return httpPort to socksPort
  }

  fun stop() {
    running.set(false)
    runCatching { httpServerSocket?.close() }
    runCatching { socksServerSocket?.close() }
    scope.cancel()
  }

  private fun acceptLoop(server: ServerSocket, isSocks: Boolean) {
    while (running.get() && !server.isClosed) {
      try {
        val client = server.accept()
        scope.launch {
          if (isSocks) handleSocksClient(client) else handleHttpClient(client)
        }
      } catch (_: IOException) {
        if (!running.get()) break
      }
    }
  }

  private fun handleHttpClient(client: Socket) {
    client.use { clientSock ->
      runCatching {
        clientSock.tcpNoDelay = true
        clientSock.soTimeout = 30_000
        val input = BufferedInputStream(clientSock.getInputStream())
        val output = clientSock.getOutputStream()

        val headerText = readHttpHeaders(input) ?: return
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 3) return

        val method = parts[0].uppercase()
        val target = parts[1]

        if (method == "CONNECT") {
          val (host, port) = parseHostPort(target, defaultPort = 443) ?: return
          val startMs = System.currentTimeMillis()
          val remote = connectUpstream(host, port)
          if (remote == null) {
            output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
            output.flush()
            recordProxyEvent(host, System.currentTimeMillis() - startMs, false, "خطای اتصال")
            return
          }
          remote.use { remoteSock ->
            TunnelRuntimeState.incrementProxyConnection()
            recordProxyEvent(
              host,
              System.currentTimeMillis() - startMs,
              true,
              if (settings.bypassMode == BypassMode.UPSTREAM_PROXY) "پروکسی" else "ضد فیلتر TLS",
            )
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            output.flush()
            relayBidirectional(clientSock, remoteSock, splitTlsFirstPacket = settings.bypassMode == BypassMode.DOH_PLUS_ANTI_DPI)
          }
        } else {
          val hostHeader = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()
          val (host, port) = parseHttpTargetHostPort(target, hostHeader) ?: return
          val startMs = System.currentTimeMillis()
          val remote = connectUpstream(host, port) ?: run {
            output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
            output.flush()
            return
          }
          remote.use { remoteSock ->
            TunnelRuntimeState.incrementProxyConnection()
            recordProxyEvent(host, System.currentTimeMillis() - startMs, true, "HTTP")
            val headerBytes = (headerText + "\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
            remoteSock.getOutputStream().write(headerBytes)
            remoteSock.getOutputStream().flush()
            TunnelRuntimeState.addTraffic(0L, headerBytes.size.toLong())
            relayBidirectional(clientSock, remoteSock, splitTlsFirstPacket = false)
          }
        }
      }
    }
  }

  private fun handleSocksClient(client: Socket) {
    client.use { clientSock ->
      runCatching {
        clientSock.tcpNoDelay = true
        clientSock.soTimeout = 30_000
        val input = BufferedInputStream(clientSock.getInputStream())
        val output = clientSock.getOutputStream()

        val ver = input.read()
        if (ver != 0x05) return
        val nMethods = input.read()
        if (nMethods <= 0) return
        val methods = ByteArray(nMethods)
        if (!readFully(input, methods)) return

        // Accept no-auth on local loopback
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        val reqHeader = ByteArray(4)
        if (!readFully(input, reqHeader)) return
        if (reqHeader[0].toInt() != 0x05 || reqHeader[1].toInt() != 0x01) {
          // Only CONNECT supported
          output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
          output.flush()
          return
        }

        val atyp = reqHeader[3].toInt() and 0xFF
        val host: String = when (atyp) {
          0x01 -> {
            val ip = ByteArray(4)
            if (!readFully(input, ip)) return
            InetAddress.getByAddress(ip).hostAddress ?: return
          }
          0x03 -> {
            val len = input.read()
            if (len <= 0) return
            val domain = ByteArray(len)
            if (!readFully(input, domain)) return
            String(domain, Charsets.UTF_8)
          }
          0x04 -> {
            val ip6 = ByteArray(16)
            if (!readFully(input, ip6)) return
            InetAddress.getByAddress(ip6).hostAddress ?: return
          }
          else -> return
        }

        val portBytes = ByteArray(2)
        if (!readFully(input, portBytes)) return
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

        val startMs = System.currentTimeMillis()
        val remote = connectUpstream(host, port)
        if (remote == null) {
          output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
          output.flush()
          recordProxyEvent(host, System.currentTimeMillis() - startMs, false, "SOCKS5 خطا")
          return
        }

        remote.use { remoteSock ->
          TunnelRuntimeState.incrementProxyConnection()
          recordProxyEvent(host, System.currentTimeMillis() - startMs, true, "SOCKS5")
          output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
          output.flush()
          relayBidirectional(clientSock, remoteSock, splitTlsFirstPacket = settings.bypassMode == BypassMode.DOH_PLUS_ANTI_DPI)
        }
      }
    }
  }

  private fun connectUpstream(targetHost: String, targetPort: Int): Socket? {
    if (settings.bypassMode == BypassMode.UPSTREAM_PROXY && settings.upstreamProxyHost.isNotBlank()) {
      return connectViaConfiguredUpstream(targetHost, targetPort)
    }

    val addresses = dohResolver.resolveHostAddresses(targetHost)
    if (addresses.isEmpty()) return null

    for (addr in addresses) {
      val sock = Socket()
      try {
        protectSocket(sock)
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(addr, targetPort), 7_000)
        return sock
      } catch (_: Exception) {
        runCatching { sock.close() }
      }
    }
    return null
  }

  private fun connectViaConfiguredUpstream(targetHost: String, targetPort: Int): Socket? {
    val proxyHost = settings.upstreamProxyHost.trim()
    val proxyPort = settings.upstreamProxyPort
    val proxyAddresses = dohResolver.resolveHostAddresses(proxyHost)
    if (proxyAddresses.isEmpty()) return null

    val sock = Socket()
    return try {
      protectSocket(sock)
      sock.tcpNoDelay = true
      sock.soTimeout = 15_000
      sock.connect(InetSocketAddress(proxyAddresses.first(), proxyPort), 8_000)

      val inStream = sock.getInputStream()
      val outStream = sock.getOutputStream()

      when (settings.upstreamProxyType) {
        UpstreamProxyType.SOCKS5 -> {
          val hasAuth = settings.upstreamProxyUser.isNotBlank()
          val greeting = if (hasAuth) byteArrayOf(0x05, 0x02, 0x00, 0x02) else byteArrayOf(0x05, 0x01, 0x00)
          outStream.write(greeting)
          outStream.flush()

          val methodChoice = ByteArray(2)
          if (!readFully(inStream, methodChoice) || methodChoice[0].toInt() != 0x05) {
            throw IOException("Invalid SOCKS5 greeting")
          }
          if (methodChoice[1].toInt() == 0x02) {
            val u = settings.upstreamProxyUser.toByteArray(Charsets.UTF_8)
            val p = settings.upstreamProxyPass.toByteArray(Charsets.UTF_8)
            val authReq = ByteArray(3 + u.size + p.size)
            authReq[0] = 0x01
            authReq[1] = u.size.toByte()
            System.arraycopy(u, 0, authReq, 2, u.size)
            authReq[2 + u.size] = p.size.toByte()
            System.arraycopy(p, 0, authReq, 3 + u.size, p.size)
            outStream.write(authReq)
            outStream.flush()
            val authResp = ByteArray(2)
            if (!readFully(inStream, authResp) || authResp[1].toInt() != 0x00) {
              throw IOException("SOCKS5 auth failed")
            }
          } else if (methodChoice[1].toInt() != 0x00) {
            throw IOException("SOCKS5 auth unsupported")
          }

          val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
          val connectReq = ByteArray(7 + hostBytes.size)
          connectReq[0] = 0x05
          connectReq[1] = 0x01
          connectReq[2] = 0x00
          connectReq[3] = 0x03
          connectReq[4] = hostBytes.size.toByte()
          System.arraycopy(hostBytes, 0, connectReq, 5, hostBytes.size)
          connectReq[5 + hostBytes.size] = ((targetPort ushr 8) and 0xFF).toByte()
          connectReq[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
          outStream.write(connectReq)
          outStream.flush()

          val respHead = ByteArray(4)
          if (!readFully(inStream, respHead) || respHead[1].toInt() != 0x00) {
            throw IOException("SOCKS5 connect rejected")
          }
          when (respHead[3].toInt() and 0xFF) {
            0x01 -> readFully(inStream, ByteArray(6))
            0x04 -> readFully(inStream, ByteArray(18))
            0x03 -> {
              val dLen = inStream.read()
              if (dLen > 0) readFully(inStream, ByteArray(dLen + 2))
            }
          }
        }

        UpstreamProxyType.HTTP -> {
          val sb = StringBuilder()
          sb.append("CONNECT ").append(targetHost).append(':').append(targetPort).append(" HTTP/1.1\r\n")
          sb.append("Host: ").append(targetHost).append(':').append(targetPort).append("\r\n")
          if (settings.upstreamProxyUser.isNotBlank()) {
            val token = Base64.encodeToString(
              "${settings.upstreamProxyUser}:${settings.upstreamProxyPass}".toByteArray(Charsets.UTF_8),
              Base64.NO_WRAP,
            )
            sb.append("Proxy-Authorization: Basic ").append(token).append("\r\n")
          }
          sb.append("\r\n")
          outStream.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
          outStream.flush()
          val respHeaders = readHttpHeaders(inStream) ?: throw IOException("No HTTP proxy response")
          val firstLine = respHeaders.substringBefore("\r\n")
          if (!firstLine.contains(" 200")) {
            throw IOException("HTTP proxy failed: $firstLine")
          }
        }
      }

      sock.soTimeout = 0
      sock
    } catch (_: Exception) {
      runCatching { sock.close() }
      null
    }
  }

  private fun relayBidirectional(
    clientSock: Socket,
    remoteSock: Socket,
    splitTlsFirstPacket: Boolean,
  ) {
    clientSock.soTimeout = 0
    remoteSock.soTimeout = 0

    val upstreamJob = scope.launch {
      copyStream(
        input = clientSock.getInputStream(),
        output = remoteSock.getOutputStream(),
        isOutbound = true,
        splitTlsFirstPacket = splitTlsFirstPacket,
      )
      runCatching { remoteSock.shutdownOutput() }
    }

    copyStream(
      input = remoteSock.getInputStream(),
      output = clientSock.getOutputStream(),
      isOutbound = false,
      splitTlsFirstPacket = false,
    )
    runCatching { clientSock.shutdownOutput() }
    runCatching { upstreamJob.cancel() }
  }

  private fun copyStream(
    input: InputStream,
    output: OutputStream,
    isOutbound: Boolean,
    splitTlsFirstPacket: Boolean,
  ) {
    val buffer = ByteArray(16 * 1024)
    var isFirstChunk = true
    try {
      while (running.get()) {
        val read = input.read(buffer)
        if (read <= 0) break

        if (isFirstChunk && splitTlsFirstPacket && isTlsClientHello(buffer, read)) {
          val chunkSize = settings.tlsChunkSize.coerceIn(1, 32)
          val firstPart = minOf(chunkSize, read)
          output.write(buffer, 0, firstPart)
          output.flush()
          if (read > firstPart) {
            Thread.sleep(2)
            val secondPart = minOf(chunkSize * 2, read - firstPart)
            output.write(buffer, firstPart, secondPart)
            output.flush()
            val remainingStart = firstPart + secondPart
            if (read > remainingStart) {
              output.write(buffer, remainingStart, read - remainingStart)
              output.flush()
            }
          }
        } else {
          output.write(buffer, 0, read)
          output.flush()
        }

        isFirstChunk = false
        if (isOutbound) {
          TunnelRuntimeState.addTraffic(bytesInDelta = 0L, bytesOutDelta = read.toLong())
        } else {
          TunnelRuntimeState.addTraffic(bytesInDelta = read.toLong(), bytesOutDelta = 0L)
        }
      }
    } catch (_: Exception) {
      // Stream closed normally
    }
  }

  private fun isTlsClientHello(buf: ByteArray, len: Int): Boolean {
    return len >= 6 && buf[0] == 0x16.toByte() && buf[1] == 0x03.toByte() && buf[5] == 0x01.toByte()
  }

  private fun readHttpHeaders(input: InputStream): String? {
    val sb = StringBuilder()
    var prev3 = 0
    var prev2 = 0
    var prev1 = 0
    var count = 0
    while (count < 16_384) {
      val b = input.read()
      if (b < 0) return null
      sb.append(b.toChar())
      count++
      if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
        return sb.toString().removeSuffix("\r\n\r\n")
      }
      prev3 = prev2
      prev2 = prev1
      prev1 = b
    }
    return null
  }

  private fun parseHostPort(authority: String, defaultPort: Int): Pair<String, Int>? {
    val clean = authority.trim()
    if (clean.isEmpty()) return null
    val colonIdx = clean.lastIndexOf(':')
    return if (colonIdx > 0 && !clean.endsWith("]")) {
      val host = clean.substring(0, colonIdx).removeSurrounding("[", "]")
      val port = clean.substring(colonIdx + 1).toIntOrNull() ?: defaultPort
      host to port
    } else {
      clean.removeSurrounding("[", "]") to defaultPort
    }
  }

  private fun parseHttpTargetHostPort(target: String, hostHeader: String?): Pair<String, Int>? {
    if (target.startsWith("http://", ignoreCase = true)) {
      val after = target.substringAfter("://").substringBefore("/")
      return parseHostPort(after, 80)
    }
    if (!hostHeader.isNullOrBlank()) {
      return parseHostPort(hostHeader, 80)
    }
    return null
  }

  private fun readFully(input: InputStream, dest: ByteArray): Boolean {
    var offset = 0
    while (offset < dest.size) {
      val r = input.read(dest, offset, dest.size - offset)
      if (r < 0) return false
      offset += r
    }
    return true
  }

  private fun recordProxyEvent(host: String, latencyMs: Long, success: Boolean, label: String) {
    val category = InstalledAppsInspector.classifyDomain(host)
    if (category != AppCategory.SYSTEM || !success) {
      TunnelRuntimeState.recordDnsQuery(
        DnsLogEntry(
          id = 0L,
          timestampMs = System.currentTimeMillis(),
          domain = host,
          queryType = label,
          latencyMs = latencyMs,
          resolvedSummary = if (success) "تونل برقرار شد" else "عدم دسترسی",
          isSuccess = success,
          category = category,
        )
      )
    }
  }
}
