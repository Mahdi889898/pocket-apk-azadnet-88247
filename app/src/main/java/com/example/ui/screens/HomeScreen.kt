package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BypassMode
import com.example.data.InstalledTargetApp
import com.example.data.RoutingScope
import com.example.data.VpnStatus
import com.example.ui.MainUiState
import com.example.ui.components.ShieldRadarControl
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.InstagramWarm
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseError
import com.example.ui.theme.TelegramAccent
import java.util.Locale

@Composable
fun HomeScreen(
  uiState: MainUiState,
  onToggleVpn: () -> Unit,
  onToggleTelegram: (Boolean) -> Unit,
  onToggleInstagram: (Boolean) -> Unit,
  onLaunchTelegram: () -> Unit,
  onLaunchInstagram: () -> Unit,
  onApplyLocalSocksTelegram: () -> Unit,
  onApplySavedProxyTelegram: () -> Unit,
  onOpenConfig: () -> Unit,
  onCycleTheme: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = uiState.stats.status
  val isConnected = status == VpnStatus.CONNECTED

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Top Brand Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
              )
            }
          }
          Column {
            Text(
              text = "آزادنت",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "گذر امن تلگرام و اینستاگرام",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Text(
              text = uiState.settings.themePreference.titleFa,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
          IconButton(
            onClick = onCycleTheme,
            modifier = Modifier.testTag("theme_cycle_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Brightness6,
              contentDescription = "تغییر پوسته رنگی",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Shield Hero Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
          width = 1.dp,
          color = if (isConnected) EmeraldPrimary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Status Badge Pill
          Surface(
            shape = RoundedCornerShape(50),
            color = when (status) {
              VpnStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
              VpnStatus.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
              VpnStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
              VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            },
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(
                    when (status) {
                      VpnStatus.CONNECTED -> EmeraldPrimary
                      VpnStatus.CONNECTING -> AmberWarning
                      VpnStatus.ERROR -> RoseError
                      VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                  )
              )
              Text(
                text = when (status) {
                  VpnStatus.CONNECTED -> "تونل آزادنت فعال است"
                  VpnStatus.CONNECTING -> "در حال راه‌اندازی تونل…"
                  VpnStatus.ERROR -> "خطا در برقراری اتصال"
                  VpnStatus.DISCONNECTED -> "آماده برای اتصال"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when (status) {
                  VpnStatus.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                  VpnStatus.CONNECTING -> MaterialTheme.colorScheme.onTertiaryContainer
                  VpnStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                  VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                },
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          ShieldRadarControl(
            status = status,
            onToggleClick = onToggleVpn,
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Session Timer & Active Mode
          Text(
            text = if (isConnected) formatDuration(uiState.sessionElapsedSeconds) else "00:00:00",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "${uiState.settings.bypassMode.titleFa} • ${uiState.settings.dohProvider.titleFa}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          AnimatedVisibility(visible = !uiState.stats.lastError.isNullOrBlank()) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            ) {
              Text(
                text = uiState.stats.lastError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Primary CTA Button
          Button(
            onClick = onToggleVpn,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isConnected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
              contentColor = if (isConnected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("vpn_primary_cta"),
          ) {
            Text(
              text = when (status) {
                VpnStatus.CONNECTED -> "قطع اتصال آزادنت"
                VpnStatus.CONNECTING -> "لغو اتصال"
                VpnStatus.DISCONNECTED, VpnStatus.ERROR -> "فعال‌سازی برای تلگرام و اینستاگرام"
              },
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }
      }
    }

    // Live Telemetry Strip
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TelemetryChip(
          icon = Icons.Rounded.ArrowDownward,
          label = "دریافت",
          value = formatBytes(uiState.stats.bytesIn),
          tint = EmeraldPrimary,
          modifier = Modifier.weight(1f),
        )
        TelemetryChip(
          icon = Icons.Rounded.ArrowUpward,
          label = "ارسال",
          value = formatBytes(uiState.stats.bytesOut),
          tint = CyanSecondary,
          modifier = Modifier.weight(1f),
        )
        TelemetryChip(
          icon = Icons.Rounded.Dns,
          label = "پرس‌وجو DNS",
          value = uiState.stats.dnsQueriesCount.toString(),
          tint = AmberWarning,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Split Tunnel Notice if fallback is active or neither app is installed
    if (uiState.settings.routingScope == RoutingScope.SPLIT_TELEGRAM_INSTAGRAM &&
      !uiState.telegramApp.isInstalled && !uiState.instagramApp.isInstalled
    ) {
      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.secondaryContainer,
          border = BorderStroke(1.dp, CyanSecondary.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "تلگرام یا اینستاگرام روی این دستگاه شناسایی نشد",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "تا زمان نصب این دو برنامه، آزادنت به‌صورت خودکار محافظت DoH و ضد فیلتر را برای کل دستگاه فعال می‌کند تا بتوانید در مرورگر و تستر شبکه عملکرد را بررسی کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
              )
            }
          }
        }
      }
    }

    // Section Title
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "برنامه‌های هدف (تونل هوشمند)",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        FilledTonalButton(
          onClick = onOpenConfig,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("تنظیمات عبور", style = MaterialTheme.typography.labelMedium)
        }
      }
    }

    // Telegram Target App Card
    item {
      TargetAppCard(
        app = uiState.telegramApp,
        subtitle = "پشتیبانی از تلگرام رسمی، Telegram X و Nekogram",
        accentColor = TelegramAccent,
        icon = Icons.AutoMirrored.Rounded.Send,
        enabledInTunnel = uiState.settings.includeTelegram,
        onToggleEnabled = onToggleTelegram,
        switchTestTag = "toggle_telegram",
        launchTestTag = "launch_telegram",
        onLaunchApp = onLaunchTelegram,
        extraAction = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isConnected && uiState.stats.localSocksProxyPort > 0 && uiState.settings.bypassMode != BypassMode.DOH_ONLY) {
              FilledTonalButton(
                onClick = onApplyLocalSocksTelegram,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("apply_local_socks_telegram"),
              ) {
                Text(
                  text = "اعمال پروکسی محلی ضد فیلتر در تلگرام (127.0.0.1:${uiState.stats.localSocksProxyPort})",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
            if (uiState.parsedTelegramProxy != null) {
              OutlinedButton(
                onClick = onApplySavedProxyTelegram,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("apply_saved_mtproto_telegram"),
              ) {
                Text(
                  text = "اعمال لینک MTProto ذخیره‌شده (${uiState.parsedTelegramProxy.server})",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
        },
      )
    }

    // Instagram Target App Card
    item {
      TargetAppCard(
        app = uiState.instagramApp,
        subtitle = "پشتیبانی از Instagram و Instagram Lite",
        accentColor = InstagramAccent,
        secondaryAccent = InstagramWarm,
        icon = Icons.Rounded.CameraAlt,
        enabledInTunnel = uiState.settings.includeInstagram,
        onToggleEnabled = onToggleInstagram,
        switchTestTag = "toggle_instagram",
        launchTestTag = "launch_instagram",
        onLaunchApp = onLaunchInstagram,
        extraAction = {
          if (isConnected && uiState.stats.localHttpProxyPort > 0) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "مسیر DoH + HTTP Bridge (پورت ${uiState.stats.localHttpProxyPort}) برای اینستاگرام آماده است",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
      )
    }
  }
}

@Composable
private fun TelemetryChip(
  icon: ImageVector,
  label: String,
  value: String,
  tint: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun TargetAppCard(
  app: InstalledTargetApp,
  subtitle: String,
  accentColor: Color,
  secondaryAccent: Color = accentColor,
  icon: ImageVector,
  enabledInTunnel: Boolean,
  onToggleEnabled: (Boolean) -> Unit,
  switchTestTag: String,
  launchTestTag: String,
  onLaunchApp: () -> Unit,
  extraAction: @Composable () -> Unit = {},
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(46.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Brush.linearGradient(listOf(accentColor, secondaryAccent))),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(24.dp),
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = app.nameFa,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Surface(
                shape = RoundedCornerShape(50),
                color = if (app.isInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Text(
                  text = if (app.isInstalled) "نصب‌شده" else "نصب‌نشده",
                  style = MaterialTheme.typography.labelMedium,
                  color = if (app.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Switch(
          checked = enabledInTunnel,
          onCheckedChange = onToggleEnabled,
          modifier = Modifier.testTag(switchTestTag),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onLaunchApp,
          enabled = app.isInstalled,
          modifier = Modifier
            .weight(1f)
            .testTag(launchTestTag),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (app.isInstalled) "باز کردن ${app.nameFa}" else "${app.nameFa} نصب نیست",
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }

      extraAction()
    }
  }
}

private fun formatDuration(totalSeconds: Long): String {
  val h = totalSeconds / 3600
  val m = (totalSeconds % 3600) / 60
  val s = totalSeconds % 60
  return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

private fun formatBytes(bytes: Long): String {
  return when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
  }
}
