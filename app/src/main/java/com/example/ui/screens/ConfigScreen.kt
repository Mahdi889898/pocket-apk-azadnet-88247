package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.BypassMode
import com.example.data.DohProvider
import com.example.data.RoutingScope
import com.example.data.UpstreamProxyType
import com.example.data.VpnStatus
import com.example.ui.MainUiState

@Composable
fun ConfigScreen(
  uiState: MainUiState,
  onSelectBypassMode: (BypassMode) -> Unit,
  onSelectRoutingScope: (RoutingScope) -> Unit,
  onSelectDohProvider: (DohProvider) -> Unit,
  onSaveCustomDohUrl: (String) -> Unit,
  onSelectTlsChunkSize: (Int) -> Unit,
  onSaveUpstreamProxy: (UpstreamProxyType, String, String, String, String) -> Unit,
  onSaveTelegramProxyLink: (String) -> Unit,
  onApplySavedTelegramProxy: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val settings = uiState.settings

  var customDohInput by remember(settings.customDohUrl) { mutableStateOf(settings.customDohUrl) }
  var proxyType by remember(settings.upstreamProxyType) { mutableStateOf(settings.upstreamProxyType) }
  var proxyHost by remember(settings.upstreamProxyHost) { mutableStateOf(settings.upstreamProxyHost) }
  var proxyPort by remember(settings.upstreamProxyPort) { mutableStateOf(settings.upstreamProxyPort.toString()) }
  var proxyUser by remember(settings.upstreamProxyUser) { mutableStateOf(settings.upstreamProxyUser) }
  var proxyPass by remember(settings.upstreamProxyPass) { mutableStateOf(settings.upstreamProxyPass) }
  var tgProxyLinkInput by remember(settings.savedTelegramProxyLink) { mutableStateOf(settings.savedTelegramProxyLink) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column {
        Text(
          text = "تنظیمات عبور و DNS",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "روش عبور از فیلتر برای تلگرام و اینستاگرام را متناسب با شبکه خود انتخاب کنید.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.stats.status == VpnStatus.CONNECTED) {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer,
          ) {
            Text(
              text = "برای اعمال تغییرات جدید در تونل فعال، پس از ویرایش یک بار اتصال را قطع و مجدداً وصل کنید.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }
    }

    // 1. Bypass Mode
    item {
      SectionCard(
        title = "موتور ضد فیلتر",
        icon = Icons.Rounded.Shield,
      ) {
        BypassMode.entries.forEach { mode ->
          SelectableOptionRow(
            selected = settings.bypassMode == mode,
            title = mode.titleFa,
            subtitle = mode.subtitleFa,
            onClick = { onSelectBypassMode(mode) },
            testTag = "bypass_mode_${mode.id}",
          )
        }

        AnimatedVisibility(visible = settings.bypassMode == BypassMode.DOH_PLUS_ANTI_DPI) {
          Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
              text = "اندازه تکه‌سازی بسته TLS ClientHello (بایت):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(2, 3, 5, 8).forEach { chunk ->
                FilterChip(
                  selected = settings.tlsChunkSize == chunk,
                  onClick = { onSelectTlsChunkSize(chunk) },
                  label = { Text("$chunk بایت") },
                )
              }
            }
          }
        }
      }
    }

    // 2. Routing Scope
    item {
      SectionCard(
        title = "محدوده مسیریابی برنامه‌ها",
        icon = Icons.Rounded.Route,
      ) {
        RoutingScope.entries.forEach { scope ->
          SelectableOptionRow(
            selected = settings.routingScope == scope,
            title = scope.titleFa,
            subtitle = scope.subtitleFa,
            onClick = { onSelectRoutingScope(scope) },
            testTag = "routing_scope_${scope.id}",
          )
        }
      }
    }

    // 3. DoH Provider
    item {
      SectionCard(
        title = "ارائه‌دهنده DNS رمزنگاری‌شده (DoH)",
        icon = Icons.Rounded.Dns,
      ) {
        DohProvider.entries.forEach { provider ->
          SelectableOptionRow(
            selected = settings.dohProvider == provider,
            title = provider.titleFa,
            subtitle = if (provider == DohProvider.CUSTOM) "آدرس DoH اختصاصی با HTTPS" else provider.url,
            onClick = { onSelectDohProvider(provider) },
            testTag = "doh_${provider.id}",
          )
        }

        AnimatedVisibility(visible = settings.dohProvider == DohProvider.CUSTOM) {
          Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = customDohInput,
              onValueChange = { customDohInput = it },
              label = { Text("آدرس سرور DoH (https://...)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              onClick = { onSaveCustomDohUrl(customDohInput) },
              modifier = Modifier.align(Alignment.End),
            ) {
              Text("ذخیره DoH")
            }
          }
        }
      }
    }

    // 4. Upstream SOCKS5/HTTP Proxy Settings
    item {
      SectionCard(
        title = "پروکسی بالادست (SOCKS5 / HTTP)",
        icon = Icons.Rounded.VpnKey,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          UpstreamProxyType.entries.forEach { type ->
            FilterChip(
              selected = proxyType == type,
              onClick = { proxyType = type },
              label = { Text(type.label) },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = proxyHost,
            onValueChange = { proxyHost = it },
            label = { Text("آدرس سرور (IP یا دامنه)") },
            singleLine = true,
            modifier = Modifier
              .weight(2f)
              .testTag("upstream_host_input"),
          )
          OutlinedTextField(
            value = proxyPort,
            onValueChange = { proxyPort = it.filter { ch -> ch.isDigit() } },
            label = { Text("پورت") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .weight(1f)
              .testTag("upstream_port_input"),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = proxyUser,
            onValueChange = { proxyUser = it },
            label = { Text("نام کاربری (اختیاری)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = proxyPass,
            onValueChange = { proxyPass = it },
            label = { Text("رمز عبور (اختیاری)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.weight(1f),
          )
        }
        Button(
          onClick = { onSaveUpstreamProxy(proxyType, proxyHost, proxyPort, proxyUser, proxyPass) },
          modifier = Modifier
            .align(Alignment.End)
            .testTag("save_upstream_proxy_button"),
        ) {
          Text("ذخیره تنظیمات پروکسی")
        }
      }
    }

    // 5. Direct Telegram MTProto / SOCKS Link
    item {
      SectionCard(
        title = "پروکسی مستقیم تلگرام (MTProto / SOCKS)",
        icon = Icons.Rounded.Link,
      ) {
        Text(
          text = "اگر لینک پروکسی تلگرام (tg://proxy یا t.me/proxy) دارید، اینجا وارد کنید تا با یک لمس در تلگرام اعمال شود.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = tgProxyLinkInput,
          onValueChange = { tgProxyLinkInput = it },
          label = { Text("tg://proxy?server=...&port=...&secret=...") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("telegram_proxy_link_input"),
        )
        if (uiState.parsedTelegramProxy != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "سرور شناسایی‌شده: ${uiState.parsedTelegramProxy.server}:${uiState.parsedTelegramProxy.port}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { onSaveTelegramProxyLink(tgProxyLinkInput) },
            modifier = Modifier
              .weight(1f)
              .testTag("save_tg_proxy_button"),
          ) {
            Text("بررسی و ذخیره")
          }
          if (uiState.parsedTelegramProxy != null) {
            Button(
              onClick = onApplySavedTelegramProxy,
              modifier = Modifier.weight(1f),
            ) {
              Text("اعمال در تلگرام")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  icon: ImageVector,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      content()
    }
  }
}

@Composable
private fun SelectableOptionRow(
  selected: Boolean,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    border = BorderStroke(
      width = 1.dp,
      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(selected = selected, onClick = onClick)
      Spacer(modifier = Modifier.width(6.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
