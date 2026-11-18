package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.VpnStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseError

@Composable
fun ShieldRadarControl(
  status: VpnStatus,
  onToggleClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "shield_radar")

  val pulseProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "pulse",
  )

  val sweepAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "sweep",
  )

  val accentColor by animateColorAsState(
    targetValue = when (status) {
      VpnStatus.CONNECTED -> EmeraldPrimary
      VpnStatus.CONNECTING -> AmberWarning
      VpnStatus.ERROR -> RoseError
      VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
    },
    animationSpec = tween(300),
    label = "accent",
  )

  val secondaryGlow = when (status) {
    VpnStatus.CONNECTED -> CyanSecondary
    VpnStatus.CONNECTING -> AmberWarning
    VpnStatus.ERROR -> RoseError
    VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
  }

  val actionDescription = when (status) {
    VpnStatus.CONNECTED -> "قطع اتصال تونل آزادنت"
    VpnStatus.CONNECTING -> "در حال برقراری تونل"
    VpnStatus.DISCONNECTED, VpnStatus.ERROR -> "فعال‌سازی تونل آزادنت"
  }

  Box(
    modifier = modifier.size(216.dp),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = size.minDimension / 2f

      // Subtle radial glow
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            accentColor.copy(alpha = if (status == VpnStatus.CONNECTED) 0.22f else 0.10f),
            accentColor.copy(alpha = 0f),
          ),
          center = center,
          radius = maxRadius,
        ),
        radius = maxRadius,
        center = center,
      )

      // Expanding pulse ring when connected
      if (status == VpnStatus.CONNECTED) {
        val r = maxRadius * (0.64f + 0.34f * pulseProgress)
        drawCircle(
          color = EmeraldBright.copy(alpha = (1f - pulseProgress) * 0.45f),
          radius = r,
          center = center,
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      // Outer static orbital ring
      drawCircle(
        color = accentColor.copy(alpha = 0.28f),
        radius = maxRadius * 0.84f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
      )

      // Rotating arc during connecting or connected
      if (status == VpnStatus.CONNECTING || status == VpnStatus.CONNECTED) {
        drawArc(
          brush = Brush.sweepGradient(
            colors = listOf(accentColor.copy(alpha = 0f), accentColor, secondaryGlow),
            center = center,
          ),
          startAngle = sweepAngle,
          sweepAngle = if (status == VpnStatus.CONNECTING) 140f else 90f,
          useCenter = false,
          style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
      }
    }

    // Center interactive button (min 48dp easily satisfied at 142dp)
    Surface(
      shape = CircleShape,
      color = when (status) {
        VpnStatus.CONNECTED -> EmeraldPrimary.copy(alpha = 0.16f)
        VpnStatus.CONNECTING -> AmberWarning.copy(alpha = 0.16f)
        VpnStatus.ERROR -> RoseError.copy(alpha = 0.16f)
        VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
      },
      tonalElevation = 6.dp,
      modifier = Modifier
        .size(142.dp)
        .clip(CircleShape)
        .testTag("vpn_toggle_button")
        .semantics {
          contentDescription = actionDescription
        }
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = androidx.compose.material3.ripple(),
          role = Role.Button,
          onClick = onToggleClick,
        ),
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
      ) {
        Icon(
          imageVector = when (status) {
            VpnStatus.CONNECTED -> Icons.Rounded.GppGood
            VpnStatus.CONNECTING -> Icons.Rounded.Sync
            VpnStatus.ERROR -> Icons.Rounded.WarningAmber
            VpnStatus.DISCONNECTED -> Icons.Rounded.PowerSettingsNew
          },
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = when (status) {
            VpnStatus.CONNECTED -> "روشن"
            VpnStatus.CONNECTING -> "اتصال…"
            VpnStatus.ERROR -> "تلاش مجدد"
            VpnStatus.DISCONNECTED -> "فعال‌سازی"
          },
          style = MaterialTheme.typography.titleMedium,
          color = accentColor,
        )
      }
    }
  }
}
