package com.cassey.customerapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ConnectionType {
    NONE,
    WIFI,
    CELLULAR,
    OTHER
}

enum class ConnectionQuality {
    GOOD,
    SLOW,
    VERY_SLOW,
    UNKNOWN
}

private const val GOOD_THRESHOLD_MS = 1500L
private const val SLOW_THRESHOLD_MS = 4000L
private const val SAMPLE_WINDOW = 5

object NetworkQuality {

    private val recentLatenciesMs = ArrayDeque<Long>(SAMPLE_WINDOW)
    private var lastTimeoutAtMs: Long = 0L

    private val _quality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val quality = _quality.asStateFlow()

    private val _connectionType = MutableStateFlow(ConnectionType.NONE)
    val connectionType = _connectionType.asStateFlow()

    suspend fun <T> timed(block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        try {
            val result = block()
            recordCallTiming(System.currentTimeMillis() - start)
            return result
        } catch (e: TimeoutCancellationException) {
            recordTimeout()
            throw e
        }
    }

    fun recordCallTiming(durationMs: Long) {
        synchronized(recentLatenciesMs) {
            if (recentLatenciesMs.size >= SAMPLE_WINDOW) recentLatenciesMs.removeFirst()
            recentLatenciesMs.addLast(durationMs)
            recomputeQuality()
        }
    }

    fun recordTimeout() {
        synchronized(recentLatenciesMs) {
            lastTimeoutAtMs = System.currentTimeMillis()
            recomputeQuality()
        }
    }

    private fun recomputeQuality() {
        val timeoutRecently = (System.currentTimeMillis() - lastTimeoutAtMs) < 30_000L
        if (timeoutRecently) {
            _quality.value = ConnectionQuality.VERY_SLOW
            return
        }
        if (recentLatenciesMs.isEmpty()) {
            _quality.value = ConnectionQuality.UNKNOWN
            return
        }
        val avg = recentLatenciesMs.average()
        _quality.value = when {
            avg < GOOD_THRESHOLD_MS -> ConnectionQuality.GOOD
            avg < SLOW_THRESHOLD_MS -> ConnectionQuality.SLOW
            else -> ConnectionQuality.VERY_SLOW
        }
    }

    fun updateConnectionType(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }

        val newType = when {
            caps == null -> ConnectionType.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            else -> ConnectionType.OTHER
        }

        if (newType != _connectionType.value) {
            synchronized(recentLatenciesMs) {
                recentLatenciesMs.clear()
                lastTimeoutAtMs = 0L
                _quality.value = ConnectionQuality.UNKNOWN
            }
        }

        _connectionType.value = newType
    }
}