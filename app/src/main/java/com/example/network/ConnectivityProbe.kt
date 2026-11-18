package com.example.network

import com.example.data.AzadNetSettings
import com.example.data.ProbeTargetResult
import com.example.vpn.DnsPacketCodec
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ConnectivityProbe {

  private val client = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(6, TimeUnit.SECONDS)
    .followRedirects(false)
    .build()

  suspend fun runFullProbe(settings: AzadNetSettings): List<ProbeTargetResult> = coroutineScope {
    val dohTarget = ProbeSpec(
      id = "doh",
      titleFa = "سرویس DNS رمزنگاری‌شده (${settings.dohProvider.titleFa})",
      endpoint = settings.effectiveDohUrl(),
      isDoh = true,
    )

    val targets = listOf(
      dohTarget,
      ProbeSpec("tg_api", "تلگرام API", "https://api.telegram.org", false),
      ProbeSpec("tg_web", "تلگرام وب", "https://web.telegram.org", false),
      ProbeSpec("ig_web", "اینستاگرام وب", "https://www.instagram.com", false),
      ProbeSpec("ig_api", "اینستاگرام موبایل API", "https://i.instagram.com", false),
    )

    targets.map { spec ->
      async(Dispatchers.IO) { probeSingle(spec) }
    }.awaitAll()
  }

  private suspend fun probeSingle(spec: ProbeSpec): ProbeTargetResult = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    try {
      val request = if (spec.isDoh) {
        val dnsQuery = DnsPacketCodec.buildDnsQuery("api.telegram.org", queryType = 1)
        Request.Builder()
          .url(spec.endpoint)
          .post(dnsQuery.toRequestBody("application/dns-message".toMediaType()))
          .header("Accept", "application/dns-message")
          .build()
      } else {
        Request.Builder()
          .url(spec.endpoint)
          .head()
          .header("User-Agent", "Mozilla/5.0 (Android)")
          .build()
      }

      client.newCall(request).execute().use { response ->
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val ok = response.code in 200..499
        val detail = if (spec.isDoh) {
          val bytes = response.body?.bytes()
          val ips = if (bytes != null) DnsPacketCodec.extractResolvedAddresses(bytes) else emptyList()
          if (ips.isNotEmpty()) "پاسخ DoH: ${ips.first()}" else "کد HTTP ${response.code}"
        } else {
          "پاسخ TLS/HTTP ${response.code}"
        }
        ProbeTargetResult(
          id = spec.id,
          titleFa = spec.titleFa,
          endpoint = spec.endpoint,
          isSuccess = ok,
          latencyMs = elapsed,
          detailFa = detail,
        )
      }
    } catch (e: Exception) {
      val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
      ProbeTargetResult(
        id = spec.id,
        titleFa = spec.titleFa,
        endpoint = spec.endpoint,
        isSuccess = false,
        latencyMs = elapsed,
        detailFa = e.javaClass.simpleName + ": " + (e.message?.take(60) ?: "خطای شبکه"),
      )
    }
  }

  private data class ProbeSpec(
    val id: String,
    val titleFa: String,
    val endpoint: String,
    val isDoh: Boolean,
  )
}
