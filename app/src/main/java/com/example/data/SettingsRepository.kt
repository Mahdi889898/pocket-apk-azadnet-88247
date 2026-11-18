package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.azadNetDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "azadnet_preferences"
)

class SettingsRepository(private val context: Context) {

  private object Keys {
    val BYPASS_MODE = stringPreferencesKey("bypass_mode")
    val ROUTING_SCOPE = stringPreferencesKey("routing_scope")
    val INCLUDE_TELEGRAM = booleanPreferencesKey("include_telegram")
    val INCLUDE_INSTAGRAM = booleanPreferencesKey("include_instagram")
    val DOH_PROVIDER = stringPreferencesKey("doh_provider")
    val CUSTOM_DOH_URL = stringPreferencesKey("custom_doh_url")
    val TLS_CHUNK_SIZE = intPreferencesKey("tls_chunk_size")
    val UPSTREAM_PROXY_TYPE = stringPreferencesKey("upstream_proxy_type")
    val UPSTREAM_PROXY_HOST = stringPreferencesKey("upstream_proxy_host")
    val UPSTREAM_PROXY_PORT = intPreferencesKey("upstream_proxy_port")
    val UPSTREAM_PROXY_USER = stringPreferencesKey("upstream_proxy_user")
    val UPSTREAM_PROXY_PASS = stringPreferencesKey("upstream_proxy_pass")
    val SAVED_TG_PROXY_LINK = stringPreferencesKey("saved_tg_proxy_link")
    val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
  }

  val settingsFlow: Flow<AzadNetSettings> = context.azadNetDataStore.data.map { prefs ->
    AzadNetSettings(
      bypassMode = BypassMode.fromId(prefs[Keys.BYPASS_MODE]),
      routingScope = RoutingScope.fromId(prefs[Keys.ROUTING_SCOPE]),
      includeTelegram = prefs[Keys.INCLUDE_TELEGRAM] ?: true,
      includeInstagram = prefs[Keys.INCLUDE_INSTAGRAM] ?: true,
      dohProvider = DohProvider.fromId(prefs[Keys.DOH_PROVIDER]),
      customDohUrl = prefs[Keys.CUSTOM_DOH_URL].orEmpty(),
      tlsChunkSize = (prefs[Keys.TLS_CHUNK_SIZE] ?: 3).coerceIn(1, 32),
      upstreamProxyType = UpstreamProxyType.fromId(prefs[Keys.UPSTREAM_PROXY_TYPE]),
      upstreamProxyHost = prefs[Keys.UPSTREAM_PROXY_HOST].orEmpty(),
      upstreamProxyPort = (prefs[Keys.UPSTREAM_PROXY_PORT] ?: 1080).coerceIn(1, 65535),
      upstreamProxyUser = prefs[Keys.UPSTREAM_PROXY_USER].orEmpty(),
      upstreamProxyPass = prefs[Keys.UPSTREAM_PROXY_PASS].orEmpty(),
      savedTelegramProxyLink = prefs[Keys.SAVED_TG_PROXY_LINK].orEmpty(),
      themePreference = ThemePreference.fromId(prefs[Keys.THEME_PREFERENCE]),
    )
  }

  suspend fun getCurrentSettings(): AzadNetSettings = settingsFlow.first()

  suspend fun updateBypassMode(mode: BypassMode) {
    context.azadNetDataStore.edit { it[Keys.BYPASS_MODE] = mode.id }
  }

  suspend fun updateRoutingScope(scope: RoutingScope) {
    context.azadNetDataStore.edit { it[Keys.ROUTING_SCOPE] = scope.id }
  }

  suspend fun updateIncludeTelegram(enabled: Boolean) {
    context.azadNetDataStore.edit { it[Keys.INCLUDE_TELEGRAM] = enabled }
  }

  suspend fun updateIncludeInstagram(enabled: Boolean) {
    context.azadNetDataStore.edit { it[Keys.INCLUDE_INSTAGRAM] = enabled }
  }

  suspend fun updateDohProvider(provider: DohProvider) {
    context.azadNetDataStore.edit { it[Keys.DOH_PROVIDER] = provider.id }
  }

  suspend fun updateCustomDohUrl(url: String) {
    context.azadNetDataStore.edit { it[Keys.CUSTOM_DOH_URL] = url.trim() }
  }

  suspend fun updateTlsChunkSize(size: Int) {
    context.azadNetDataStore.edit { it[Keys.TLS_CHUNK_SIZE] = size.coerceIn(1, 32) }
  }

  suspend fun updateUpstreamProxy(
    type: UpstreamProxyType,
    host: String,
    port: Int,
    user: String,
    pass: String,
  ) {
    context.azadNetDataStore.edit { prefs ->
      prefs[Keys.UPSTREAM_PROXY_TYPE] = type.id
      prefs[Keys.UPSTREAM_PROXY_HOST] = host.trim()
      prefs[Keys.UPSTREAM_PROXY_PORT] = port.coerceIn(1, 65535)
      prefs[Keys.UPSTREAM_PROXY_USER] = user.trim()
      prefs[Keys.UPSTREAM_PROXY_PASS] = pass
    }
  }

  suspend fun updateSavedTelegramProxyLink(link: String) {
    context.azadNetDataStore.edit { it[Keys.SAVED_TG_PROXY_LINK] = link.trim() }
  }

  suspend fun updateThemePreference(pref: ThemePreference) {
    context.azadNetDataStore.edit { it[Keys.THEME_PREFERENCE] = pref.id }
  }
}
