package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppCategory
import com.example.data.DnsLogEntry
import com.example.data.ProbeTargetResult
import com.example.ui.MainUiState
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseError
import com.example.ui.theme.TelegramAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
  uiState: MainUiState,
  onRunProbe: () -> Unit,
  onSelectLogFilter: (AppCategory?) -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val filteredLogs = remember(uiState.dnsLogs, uiState.logFilter) {
    val f = uiState.logFilter
    if (f == null) uiState.dnsLogs else uiState.dnsLogs.filter { it.category == f }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column {
        Text(
          text = "پایش شبکه و گزارش زنده",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "تست واقعی دسترسی به سرورهای تلگرام، اینستاگرام و DoH به همراه گزارش لحظه‌ای درخواست‌های DNS.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Real Connectivity Probe Card
    item {
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.NetworkCheck,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "تست زنده دسترسی و تاخیر",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          Button(
            onClick = onRunProbe,
            enabled = !uiState.isProbing,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("run_probe_button"),
          ) {
            if (uiState.isProbing) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("در حال بررسی سرورها…")
            } else {
              Icon(
                imageVector = Icons.Rounded.Radar,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("اجرای تست واقعی اتصال")
            }
          }

          if (uiState.probeResults.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              uiState.probeResults.forEach { result ->
                ProbeResultRow(result)
              }
            }
          }
        }
      }
    }

    // DNS Log Header & Filters
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "گزارش زنده DNS و پروکسی (${filteredLogs.size})",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(
          onClick = onClearLogs,
          modifier = Modifier.testTag("clear_logs_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteSweep,
            contentDescription = "پاک کردن گزارش",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = uiState.logFilter == null,
          onClick = { onSelectLogFilter(null) },
          label = { Text("همه") },
        )
        FilterChip(
          selected = uiState.logFilter == AppCategory.TELEGRAM,
          onClick = { onSelectLogFilter(AppCategory.TELEGRAM) },
          label = { Text("تلگرام") },
        )
        FilterChip(
          selected = uiState.logFilter == AppCategory.INSTAGRAM,
          onClick = { onSelectLogFilter(AppCategory.INSTAGRAM) },
          label = { Text("اینستاگرام") },
        )
      }
    }

    if (filteredLogs.isEmpty()) {
      item {
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Radar,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(36.dp),
            )
            Text(
              text = "هنوز درخواستی ثبت نشده است",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "پس از فعال‌سازی آزادنت و باز کردن تلگرام یا اینستاگرام، درخواست‌های DNS رمزنگاری‌شده در اینجا نمایش داده می‌شوند.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredLogs, key = { it.id }) { entry ->
        DnsLogCard(entry)
      }
    }
  }
}

@Composable
private fun ProbeResultRow(result: ProbeTargetResult) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = if (result.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
          contentDescription = null,
          tint = if (result.isSuccess) EmeraldPrimary else RoseError,
          modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = result.titleFa,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = result.detailFa,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(50),
        color = if (result.isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
      ) {
        Text(
          text = "${result.latencyMs} ms",
          style = MaterialTheme.typography.labelSmall,
          color = if (result.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun DnsLogCard(entry: DnsLogEntry) {
  val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
  val badgeColor = when (entry.category) {
    AppCategory.TELEGRAM -> TelegramAccent
    AppCategory.INSTAGRAM -> InstagramAccent
    AppCategory.SYSTEM -> CyanSecondary
  }

  Card(
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.16f),
          ) {
            Text(
              text = entry.category.labelFa,
              style = MaterialTheme.typography.labelMedium,
              color = badgeColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          Text(
            text = entry.domain,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "${entry.queryType} • ${entry.resolvedSummary}",
          style = MaterialTheme.typography.bodySmall,
          color = if (entry.isSuccess) MaterialTheme.colorScheme.onSurfaceVariant else RoseError,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "${entry.latencyMs}ms",
          style = MaterialTheme.typography.labelSmall,
          color = if (entry.isSuccess) EmeraldPrimary else RoseError,
        )
        Text(
          text = timeFormat.format(Date(entry.timestampMs)),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
