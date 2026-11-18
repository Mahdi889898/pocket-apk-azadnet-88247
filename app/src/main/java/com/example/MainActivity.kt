package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ThemePreference
import com.example.data.VpnStatus
import com.example.ui.MainViewModel
import com.example.ui.screens.ConfigScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AzadNetTheme

object AzadRoutes {
  const val HOME = "home"
  const val CONFIG = "config"
  const val DIAGNOSTICS = "diagnostics"
}

private data class NavTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val navTabs = listOf(
  NavTab(
    route = AzadRoutes.HOME,
    label = "اتصال",
    selectedIcon = Icons.Rounded.Shield,
    unselectedIcon = Icons.Outlined.Shield,
    testTag = "nav_home",
  ),
  NavTab(
    route = AzadRoutes.CONFIG,
    label = "تنظیمات عبور",
    selectedIcon = Icons.Rounded.Tune,
    unselectedIcon = Icons.Outlined.Tune,
    testTag = "nav_config",
  ),
  NavTab(
    route = AzadRoutes.DIAGNOSTICS,
    label = "پایش شبکه",
    selectedIcon = Icons.Rounded.NetworkCheck,
    unselectedIcon = Icons.Outlined.NetworkCheck,
    testTag = "nav_diagnostics",
  ),
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      val darkTheme = when (uiState.settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
      }

      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AzadNetTheme(darkTheme = darkTheme) {
          AzadNetAppRoot(viewModel = viewModel)
        }
      }
    }
  }
}

@Composable
fun AzadNetAppRoot(viewModel: MainViewModel) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }

  val vpnPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      viewModel.startVpnService(context)
    } else {
      viewModel.stopVpnService(context)
    }
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { _ ->
    val prepareIntent = viewModel.prepareVpnIntent(context)
    if (prepareIntent != null) {
      vpnPermissionLauncher.launch(prepareIntent)
    } else {
      viewModel.startVpnService(context)
    }
  }

  val onToggleVpn: () -> Unit = {
    val currentStatus = uiState.stats.status
    if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING) {
      viewModel.stopVpnService(context)
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      } else {
        val prepareIntent = viewModel.prepareVpnIntent(context)
        if (prepareIntent != null) {
          vpnPermissionLauncher.launch(prepareIntent)
        } else {
          viewModel.startVpnService(context)
        }
      }
    }
  }

  LaunchedEffect(Unit) {
    viewModel.refreshInstalledApps()
    viewModel.messages.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: AzadRoutes.HOME

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(
            windowInsets = WindowInsets.navigationBars,
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            navTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            navTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          NavHost(
            navController = navController,
            startDestination = AzadRoutes.HOME,
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 680.dp),
          ) {
            composable(AzadRoutes.HOME) {
              HomeScreen(
                uiState = uiState,
                onToggleVpn = onToggleVpn,
                onToggleTelegram = viewModel::setIncludeTelegram,
                onToggleInstagram = viewModel::setIncludeInstagram,
                onLaunchTelegram = {
                  viewModel.launchApp(context, uiState.telegramApp.primaryPackage, "تلگرام")
                },
                onLaunchInstagram = {
                  viewModel.launchApp(context, uiState.instagramApp.primaryPackage, "اینستاگرام")
                },
                onApplyLocalSocksTelegram = { viewModel.applyLocalSocksToTelegram(context) },
                onApplySavedProxyTelegram = { viewModel.applySavedProxyToTelegram(context) },
                onOpenConfig = { navigateToTab(AzadRoutes.CONFIG) },
                onCycleTheme = viewModel::cycleTheme,
              )
            }

            composable(AzadRoutes.CONFIG) {
              ConfigScreen(
                uiState = uiState,
                onSelectBypassMode = viewModel::setBypassMode,
                onSelectRoutingScope = viewModel::setRoutingScope,
                onSelectDohProvider = viewModel::setDohProvider,
                onSaveCustomDohUrl = viewModel::saveCustomDohUrl,
                onSelectTlsChunkSize = viewModel::setTlsChunkSize,
                onSaveUpstreamProxy = viewModel::saveUpstreamProxy,
                onSaveTelegramProxyLink = viewModel::saveTelegramProxyLink,
                onApplySavedTelegramProxy = { viewModel.applySavedProxyToTelegram(context) },
              )
            }

            composable(AzadRoutes.DIAGNOSTICS) {
              DiagnosticsScreen(
                uiState = uiState,
                onRunProbe = viewModel::runConnectivityProbe,
                onSelectLogFilter = viewModel::setLogFilter,
                onClearLogs = viewModel::clearDnsLogs,
              )
            }
          }
        }
      }
    }
  }
}
