package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object InstalledAppsInspector {

  val TELEGRAM_PACKAGES = listOf(
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.thunderdog.challegram",
    "nekox.messenger",
    "org.telegram.plus",
  )

  val INSTAGRAM_PACKAGES = listOf(
    "com.instagram.android",
    "com.instagram.lite",
  )

  fun inspectTelegram(context: Context): InstalledTargetApp {
    val installed = findInstalled(context, TELEGRAM_PACKAGES)
    return InstalledTargetApp(
      id = "telegram",
      nameFa = "تلگرام",
      installedPackages = installed,
      primaryPackage = installed.firstOrNull(),
    )
  }

  fun inspectInstagram(context: Context): InstalledTargetApp {
    val installed = findInstalled(context, INSTAGRAM_PACKAGES)
    return InstalledTargetApp(
      id = "instagram",
      nameFa = "اینستاگرام",
      installedPackages = installed,
      primaryPackage = installed.firstOrNull(),
    )
  }

  fun findInstalled(context: Context, candidates: List<String>): List<String> {
    val pm = context.packageManager
    return candidates.filter { pkg ->
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(pkg, 0)
        }
        true
      } catch (_: PackageManager.NameNotFoundException) {
        false
      }
    }
  }

  fun launchApp(context: Context, packageName: String): Boolean {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(launchIntent)
      return true
    }
    return false
  }

  fun openUri(context: Context, uriString: String): Boolean {
    return try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    } catch (_: Exception) {
      false
    }
  }

  fun classifyDomain(domain: String): AppCategory {
    val d = domain.lowercase()
    return when {
      d.contains("telegram") || d.contains("t.me") || d.contains("tdesktop") || d.contains("telesco.pe") ->
        AppCategory.TELEGRAM
      d.contains("instagram") || d.contains("cdninstagram") || d.contains("ig.me") || d.contains("fbcdn") ->
        AppCategory.INSTAGRAM
      else -> AppCategory.SYSTEM
    }
  }
}
