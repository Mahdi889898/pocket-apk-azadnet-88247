package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppCategory
import com.example.data.AzadNetSettings
import com.example.data.BypassMode
import com.example.data.DnsLogEntry
import com.example.data.DohProvider
import com.example.data.InstalledAppsInspector
import com.example.data.InstalledTargetApp
import com.example.data.ProbeTargetResult
import com.example.data.RoutingScope
import com.example.data.SettingsRepository
import com.example.data.TelegramProxyConfig
import com.example.data.TelegramProxyParser
import com.example.data.ThemePreference
import com.example.data.TunnelStats
import com.example.data.UpstreamProxyType
import com.example.network.ConnectivityProbe
import com.example.vpn.AzadNetVpnService
import com.example.vpn.TunnelRuntimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
  val settings: AzadNetSettings = AzadNetSettings(),
  val stats: TunnelStats = TunnelStats(),
  val dnsLogs: List<DnsLogEntry> = emptyList(),
  val telegramApp: InstalledTargetApp = InstalledTargetApp("telegram", "تلگرام", emptyList(), null),
  val instagramApp: InstalledTargetApp = InstalledTargetApp("instagram", "اینستاگرام", emptyList(), null),
  val parsedTelegramProxy: TelegramProxyConfig? = null,
  val probeResults: List<ProbeTargetResult> = emptyList(),
  val isProbing: Boolean = false,
  val logFilter: AppCategory? = null,
  val sessionElapsedSeconds: Long = 0L,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val settingsRepository = SettingsRepository(application.applicationContext)
  private val connectivityProbe = ConnectivityProbe()

  private val _installedApps = MutableStateFlow(inspectApps(application.applicationContext))
  private val _probeResults = MutableStateFlow<List<ProbeTargetResult>>(emptyList())
  private val _isProbing = MutableStateFlow(false)
  private val _logFilter = MutableStateFlow<AppCategory?>(null)
  private val _elapsedSeconds = MutableStateFlow(0L)

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val uiState: StateFlow<MainUiState> = combine(
    settingsRepository.settingsFlow,
    TunnelRuntimeState.stats,
    TunnelRuntimeState.dnsLogs,
    _installedApps,
    combine(_probeResults, _isProbing, _logFilter, _elapsedSeconds) { p, probing, filter, elapsed ->
      Quadruple(p, probing, filter, elapsed)
    },
  ) { settings, stats, logs, apps, extra ->
    MainUiState(
      settings = settings,
      stats = stats,
      dnsLogs = logs,
      telegramApp = apps.first,
      instagramApp = apps.second,
      parsedTelegramProxy = TelegramProxyParser.parse(settings.savedTelegramProxyLink),
      probeResults = extra.first,
      isProbing = extra.second,
      logFilter = extra.third,
      sessionElapsedSeconds = extra.fourth,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = MainUiState(),
  )

  init {
    viewModelScope.launch {
      while (isActive) {
        val since = TunnelRuntimeState.stats.value.connectedSinceMs
        _elapsedSeconds.value = if (since > 0L) ((System.currentTimeMillis() - since) / 1000L).coerceAtLeast(0L) else 0L
        delay(1_000L)
      }
    }
  }

  fun refreshInstalledApps() {
    _installedApps.value = inspectApps(getApplication<Application>().applicationContext)
  }

  fun prepareVpnIntent(context: Context): Intent? = VpnService.prepare(context)

  fun startVpnService(context: Context) {
    val current = uiState.value.settings
    if (current.bypassMode == BypassMode.UPSTREAM_PROXY && current.upstreamProxyHost.isBlank()) {
      _messages.tryEmit("ابتدا آدرس سرور پروکسی را در بخش تنظیمات عبور وارد کنید")
      return
    }
    val intent = AzadNetVpnService.startIntent(context)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      context.startForegroundService(intent)
    } else {
      context.startService(intent)
    }
  }

  fun stopVpnService(context: Context) {
    context.startService(AzadNetVpnService.stopIntent(context))
  }

  fun setBypassMode(mode: BypassMode) {
    viewModelScope.launch { settingsRepository.updateBypassMode(mode) }
  }

  fun setRoutingScope(scope: RoutingScope) {
    viewModelScope.launch { settingsRepository.updateRoutingScope(scope) }
  }

  fun setIncludeTelegram(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.updateIncludeTelegram(enabled) }
  }

  fun setIncludeInstagram(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.updateIncludeInstagram(enabled) }
  }

  fun setDohProvider(provider: DohProvider) {
    viewModelScope.launch { settingsRepository.updateDohProvider(provider) }
  }

  fun saveCustomDohUrl(url: String) {
    val clean = url.trim()
    if (clean.isNotEmpty() && !clean.startsWith("https://")) {
      _messages.tryEmit("آدرس DoH باید با https:// شروع شود")
      return
    }
    viewModelScope.launch {
      settingsRepository.updateCustomDohUrl(clean)
      _messages.tryEmit("آدرس DoH ذخیره شد")
    }
  }

  fun setTlsChunkSize(size: Int) {
    viewModelScope.launch { settingsRepository.updateTlsChunkSize(size) }
  }

  fun saveUpstreamProxy(
    type: UpstreamProxyType,
    host: String,
    portText: String,
    user: String,
    pass: String,
  ) {
    val port = portText.trim().toIntOrNull()
    if (host.isBlank() || port == null || port !in 1..65535) {
      _messages.tryEmit("آدرس یا پورت پروکسی معتبر نیست")
      return
    }
    viewModelScope.launch {
      settingsRepository.updateUpstreamProxy(type, host, port, user, pass)
      _messages.tryEmit("تنظیمات پروکسی ذخیره شد")
    }
  }

  fun saveTelegramProxyLink(link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) {
      viewModelScope.launch {
        settingsRepository.updateSavedTelegramProxyLink("")
        _messages.tryEmit("لینک پروکسی تلگرام پاک شد")
      }
      return
    }
    val parsed = TelegramProxyParser.parse(trimmed)
    if (parsed == null) {
      _messages.tryEmit("فرمت لینک پروکسی تلگرام معتبر نیست (tg://proxy یا t.me/proxy)")
      return
    }
    viewModelScope.launch {
      settingsRepository.updateSavedTelegramProxyLink(trimmed)
      _messages.tryEmit("پروکسی تلگرام (${parsed.server}:${parsed.port}) ذخیره شد")
    }
  }

  fun setThemePreference(pref: ThemePreference) {
    viewModelScope.launch { settingsRepository.updateThemePreference(pref) }
  }

  fun cycleTheme() {
    val next = when (uiState.value.settings.themePreference) {
      ThemePreference.DARK -> ThemePreference.LIGHT
      ThemePreference.LIGHT -> ThemePreference.SYSTEM
      ThemePreference.SYSTEM -> ThemePreference.DARK
    }
    setThemePreference(next)
  }

  fun launchApp(context: Context, packageName: String?, fallbackName: String) {
    if (packageName == null) {
      _messages.tryEmit("برنامه $fallbackName روی این دستگاه نصب نیست")
      return
    }
    val launched = InstalledAppsInspector.launchApp(context, packageName)
    if (!launched) {
      _messages.tryEmit("امکان باز کردن $fallbackName وجود ندارد")
    }
  }

  fun applyLocalSocksToTelegram(context: Context) {
    val port = uiState.value.stats.localSocksProxyPort
    if (port <= 0) {
      _messages.tryEmit("ابتدا تونل آزادنت را فعال کنید")
      return
    }
    val uri = "tg://socks?server=127.0.0.1&port=$port"
    if (!InstalledAppsInspector.openUri(context, uri)) {
      _messages.tryEmit("اپلیکیشن تلگرام برای اعمال پروکسی یافت نشد")
    }
  }

  fun applySavedProxyToTelegram(context: Context) {
    val parsed = uiState.value.parsedTelegramProxy
    if (parsed == null) {
      _messages.tryEmit("ابتدا لینک پروکسی تلگرام را در تنظیمات عبور وارد کنید")
      return
    }
    if (!InstalledAppsInspector.openUri(context, parsed.toDeepLink())) {
      _messages.tryEmit("اپلیکیشن تلگرام روی دستگاه یافت نشد")
    }
  }

  fun runConnectivityProbe() {
    if (_isProbing.value) return
    viewModelScope.launch {
      _isProbing.value = true
      try {
        _probeResults.value = connectivityProbe.runFullProbe(uiState.value.settings)
      } finally {
        _isProbing.value = false
      }
    }
  }

  fun setLogFilter(category: AppCategory?) {
    _logFilter.value = category
  }

  fun clearDnsLogs() {
    TunnelRuntimeState.clearLogs()
  }

  private fun inspectApps(context: Context): Pair<InstalledTargetApp, InstalledTargetApp> =
    InstalledAppsInspector.inspectTelegram(context) to InstalledAppsInspector.inspectInstagram(context)

  private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
