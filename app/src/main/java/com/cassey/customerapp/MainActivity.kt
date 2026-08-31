package com.cassey.customerapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.cassey.customerapp.ui.navigation.NavGraph
import com.cassey.customerapp.ui.theme.CustomerAppTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CustomerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClampedFontScale {
                        NavGraph()
                    }
                }
            }
        }
    }
}

@Composable
private fun ClampedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val clampedFontScale = density.fontScale.coerceIn(0.85f, 1.15f)
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, clampedFontScale)
    ) {
        content()
    }
}