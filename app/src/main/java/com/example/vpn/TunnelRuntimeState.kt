package com.example.vpn

import com.example.data.DnsLogEntry
import com.example.data.TunnelStats
import com.example.data.VpnStatus
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TunnelRuntimeState {
  private const val MAX_LOG_ENTRIES = 150
  private val nextLogId = AtomicLong(1L)

  private val _stats = MutableStateFlow(TunnelStats())
  val stats: StateFlow<TunnelStats> = _stats.asStateFlow()

  private val _dnsLogs = MutableStateFlow<List<DnsLogEntry>>(emptyList())
  val dnsLogs: StateFlow<List<DnsLogEntry>> = _dnsLogs.asStateFlow()

  fun setStatus(
    status: VpnStatus,
    localHttpPort: Int = _stats.value.localHttpProxyPort,
    localSocksPort: Int = _stats.value.localSocksProxyPort,
    routedPackages: List<String> = _stats.value.routedPackages,
    usedFullDeviceFallback: Boolean = _stats.value.usedFullDeviceFallback,
    lastError: String? = null,
  ) {
    _stats.update { current ->
      val since = when (status) {
        VpnStatus.CONNECTED -> if (current.connectedSinceMs > 0L) current.connectedSinceMs else System.currentTimeMillis()
        VpnStatus.DISCONNECTED, VpnStatus.ERROR -> 0L
        VpnStatus.CONNECTING -> current.connectedSinceMs
      }
      current.copy(
        status = status,
        connectedSinceMs = since,
        localHttpProxyPort = localHttpPort,
        localSocksProxyPort = localSocksPort,
        routedPackages = routedPackages,
        usedFullDeviceFallback = usedFullDeviceFallback,
        lastError = lastError,
      )
    }
  }

  fun addTraffic(bytesInDelta: Long, bytesOutDelta: Long) {
    if (bytesInDelta <= 0L && bytesOutDelta <= 0L) return
    _stats.update {
      it.copy(
        bytesIn = it.bytesIn + bytesInDelta,
        bytesOut = it.bytesOut + bytesOutDelta,
      )
    }
  }

  fun incrementProxyConnection() {
    _stats.update { it.copy(proxyConnectionsCount = it.proxyConnectionsCount + 1) }
  }

  fun recordDnsQuery(entry: DnsLogEntry) {
    _stats.update { it.copy(dnsQueriesCount = it.dnsQueriesCount + 1) }
    val numbered = entry.copy(id = nextLogId.getAndIncrement())
    _dnsLogs.update { list ->
      (listOf(numbered) + list).take(MAX_LOG_ENTRIES)
    }
  }

  fun resetCountersOnStop() {
    _stats.value = TunnelStats(status = VpnStatus.DISCONNECTED)
  }

  fun clearLogs() {
    _dnsLogs.value = emptyList()
  }
}
