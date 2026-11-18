package com.example.data

import android.net.Uri

enum class BypassMode(
  val id: String,
  val titleFa: String,
  val subtitleFa: String,
) {
  DOH_PLUS_ANTI_DPI(
    id = "doh_anti_dpi",
    titleFa = "DNS رمزنگاری‌شده + ضد فیلتر TLS",
    subtitleFa = "تغییر DNS به DoH و تکه‌سازی بسته‌های TLS برای عبور از فیلتر SNI بدون نیاز به سرور خارجی",
  ),
  DOH_ONLY(
    id = "doh_only",
    titleFa = "فقط DNS رمزنگاری‌شده (DoH)",
    subtitleFa = "رفع مسدودیت DNS با حداکثر سرعت و کمترین مصرف باتری (مناسب شبکه‌هایی با فیلتر DNS)",
  ),
  UPSTREAM_PROXY(
    id = "upstream_proxy",
    titleFa = "اتصال از طریق پروکسی اختصاصی (SOCKS5 / HTTP)",
    subtitleFa = "هدایت ترافیک تلگرام و اینستاگرام از طریق سرور پروکسی شخصی شما همراه با DoH",
  );

  companion object {
    fun fromId(id: String?): BypassMode =
      entries.firstOrNull { it.id == id } ?: DOH_PLUS_ANTI_DPI
  }
}

enum class RoutingScope(
  val id: String,
  val titleFa: String,
  val subtitleFa: String,
) {
  SPLIT_TELEGRAM_INSTAGRAM(
    id = "split_tg_ig",
    titleFa = "فقط تلگرام و اینستاگرام",
    subtitleFa = "سایر برنامه‌ها و سایت‌های داخلی بدون تغییر و با سرعت مستقیم کار می‌کنند",
  ),
  FULL_DEVICE(
    id = "full_device",
    titleFa = "کل دستگاه (همه برنامه‌ها)",
    subtitleFa = "تمامی برنامه‌ها از تونل DNS و ضد فیلتر عبور می‌کنند",
  );

  companion object {
    fun fromId(id: String?): RoutingScope =
      entries.firstOrNull { it.id == id } ?: SPLIT_TELEGRAM_INSTAGRAM
  }
}

enum class DohProvider(
  val id: String,
  val titleFa: String,
  val url: String,
  val bootstrapIp: String,
) {
  CLOUDFLARE(
    id = "cloudflare",
    titleFa = "Cloudflare (1.1.1.1)",
    url = "https://cloudflare-dns.com/dns-query",
    bootstrapIp = "1.1.1.1",
  ),
  GOOGLE(
    id = "google",
    titleFa = "Google (8.8.8.8)",
    url = "https://dns.google/dns-query",
    bootstrapIp = "8.8.8.8",
  ),
  QUAD9(
    id = "quad9",
    titleFa = "Quad9 (9.9.9.9)",
    url = "https://dns.quad9.net/dns-query",
    bootstrapIp = "9.9.9.9",
  ),
  CUSTOM(
    id = "custom",
    titleFa = "آدرس دلخواه (Custom DoH)",
    url = "",
    bootstrapIp = "1.1.1.1",
  );

  companion object {
    fun fromId(id: String?): DohProvider =
      entries.firstOrNull { it.id == id } ?: CLOUDFLARE
  }
}

enum class UpstreamProxyType(val id: String, val label: String) {
  SOCKS5("socks5", "SOCKS5"),
  HTTP("http", "HTTP CONNECT");

  companion object {
    fun fromId(id: String?): UpstreamProxyType =
      entries.firstOrNull { it.id == id } ?: SOCKS5
  }
}

enum class ThemePreference(val id: String, val titleFa: String) {
  DARK("dark", "تیره"),
  LIGHT("light", "روشن"),
  SYSTEM("system", "سیستم");

  companion object {
    fun fromId(id: String?): ThemePreference =
      entries.firstOrNull { it.id == id } ?: DARK
  }
}

data class TelegramProxyConfig(
  val server: String,
  val port: Int,
  val secret: String = "",
  val username: String = "",
  val password: String = "",
  val isMtproto: Boolean = true,
  val rawLink: String = "",
) {
  fun toDeepLink(): String {
    return if (isMtproto) {
      Uri.Builder()
        .scheme("tg")
        .authority("proxy")
        .appendQueryParameter("server", server)
        .appendQueryParameter("port", port.toString())
        .appendQueryParameter("secret", secret)
        .build()
        .toString()
    } else {
      val builder = Uri.Builder()
        .scheme("tg")
        .authority("socks")
        .appendQueryParameter("server", server)
        .appendQueryParameter("port", port.toString())
      if (username.isNotBlank()) builder.appendQueryParameter("user", username)
      if (password.isNotBlank()) builder.appendQueryParameter("pass", password)
      builder.build().toString()
    }
  }
}

object TelegramProxyParser {
  fun parse(input: String): TelegramProxyConfig? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: ""
    val path = uri.path?.lowercase() ?: ""

    val isMtproto = when {
      scheme == "tg" && host == "proxy" -> true
      (scheme == "https" || scheme == "http") && (host == "t.me" || host == "telegram.me") && path.startsWith("/proxy") -> true
      scheme == "tg" && host == "socks" -> false
      (scheme == "https" || scheme == "http") && (host == "t.me" || host == "telegram.me") && path.startsWith("/socks") -> false
      else -> return null
    }

    val server = uri.getQueryParameter("server")?.trim().orEmpty()
    val port = uri.getQueryParameter("port")?.trim()?.toIntOrNull() ?: return null
    if (server.isEmpty() || port !in 1..65535) return null

    if (isMtproto) {
      val secret = uri.getQueryParameter("secret")?.trim().orEmpty()
      if (secret.isEmpty()) return null
      return TelegramProxyConfig(
        server = server,
        port = port,
        secret = secret,
        isMtproto = true,
        rawLink = trimmed,
      )
    } else {
      val user = uri.getQueryParameter("user")?.trim().orEmpty()
      val pass = uri.getQueryParameter("pass")?.trim().orEmpty()
      return TelegramProxyConfig(
        server = server,
        port = port,
        username = user,
        password = pass,
        isMtproto = false,
        rawLink = trimmed,
      )
    }
  }
}

data class AzadNetSettings(
  val bypassMode: BypassMode = BypassMode.DOH_PLUS_ANTI_DPI,
  val routingScope: RoutingScope = RoutingScope.SPLIT_TELEGRAM_INSTAGRAM,
  val includeTelegram: Boolean = true,
  val includeInstagram: Boolean = true,
  val dohProvider: DohProvider = DohProvider.CLOUDFLARE,
  val customDohUrl: String = "",
  val tlsChunkSize: Int = 3,
  val upstreamProxyType: UpstreamProxyType = UpstreamProxyType.SOCKS5,
  val upstreamProxyHost: String = "",
  val upstreamProxyPort: Int = 1080,
  val upstreamProxyUser: String = "",
  val upstreamProxyPass: String = "",
  val savedTelegramProxyLink: String = "",
  val themePreference: ThemePreference = ThemePreference.DARK,
) {
  fun effectiveDohUrl(): String {
    return if (dohProvider == DohProvider.CUSTOM && customDohUrl.isNotBlank()) {
      customDohUrl.trim()
    } else if (dohProvider == DohProvider.CUSTOM) {
      DohProvider.CLOUDFLARE.url
    } else {
      dohProvider.url
    }
  }
}

enum class VpnStatus {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  ERROR,
}

enum class AppCategory(val labelFa: String) {
  TELEGRAM("تلگرام"),
  INSTAGRAM("اینستاگرام"),
  SYSTEM("سایر"),
}

data class DnsLogEntry(
  val id: Long,
  val timestampMs: Long,
  val domain: String,
  val queryType: String,
  val latencyMs: Long,
  val resolvedSummary: String,
  val isSuccess: Boolean,
  val category: AppCategory,
)

data class TunnelStats(
  val status: VpnStatus = VpnStatus.DISCONNECTED,
  val connectedSinceMs: Long = 0L,
  val bytesIn: Long = 0L,
  val bytesOut: Long = 0L,
  val dnsQueriesCount: Long = 0L,
  val proxyConnectionsCount: Long = 0L,
  val localHttpProxyPort: Int = 0,
  val localSocksProxyPort: Int = 0,
  val routedPackages: List<String> = emptyList(),
  val usedFullDeviceFallback: Boolean = false,
  val lastError: String? = null,
)

data class InstalledTargetApp(
  val id: String,
  val nameFa: String,
  val installedPackages: List<String>,
  val primaryPackage: String?,
) {
  val isInstalled: Boolean get() = installedPackages.isNotEmpty()
}

data class ProbeTargetResult(
  val id: String,
  val titleFa: String,
  val endpoint: String,
  val isSuccess: Boolean,
  val latencyMs: Long,
  val detailFa: String,
)
