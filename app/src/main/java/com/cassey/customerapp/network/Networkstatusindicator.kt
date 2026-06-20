package com.cassey.customerapp.network

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NetworkStatusIndicator(
    modifier: Modifier = Modifier,
    showTextWhenGood: Boolean = true
) {
    val connectionType by NetworkQuality.connectionType.collectAsState()
    val quality by NetworkQuality.quality.collectAsState()

    val (icon, iconColor) = when {
        connectionType == ConnectionType.NONE ->
            Icons.Default.CloudOff to Color(0xFFFB7185) // rose
        connectionType == ConnectionType.WIFI && quality == ConnectionQuality.VERY_SLOW ->
            Icons.Default.WifiOff to Color(0xFFFB7185)
        connectionType == ConnectionType.WIFI ->
            Icons.Default.Wifi to qualityColor(quality)
        else ->
            Icons.Default.SignalCellularAlt to qualityColor(quality)
    }

    val label = statusLabel(connectionType, quality)
    val shouldShowText = label != null && (showTextWhenGood || quality != ConnectionQuality.GOOD)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = label ?: "Connection status",
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        if (shouldShowText && label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = iconColor
            )
        }
    }
}

private fun qualityColor(quality: ConnectionQuality): Color = when (quality) {
    ConnectionQuality.GOOD -> Color(0xFF34D399)      // emerald
    ConnectionQuality.SLOW -> Color(0xFFFBBF24)      // amber
    ConnectionQuality.VERY_SLOW -> Color(0xFFFB7185) // rose
    ConnectionQuality.UNKNOWN -> Color(0xFF94A3B8)   // slate, neutral
}

private fun statusLabel(type: ConnectionType, quality: ConnectionQuality): String? {
    if (type == ConnectionType.NONE) return "No signal — check your connection"
    return when (quality) {
        ConnectionQuality.VERY_SLOW -> "Very slow connection — some actions may fail"
        ConnectionQuality.SLOW -> "Slow connection"
        ConnectionQuality.GOOD -> "Connected"
        ConnectionQuality.UNKNOWN -> null
    }
}