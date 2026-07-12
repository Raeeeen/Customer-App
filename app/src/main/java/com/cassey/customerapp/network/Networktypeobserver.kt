package com.cassey.customerapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.cassey.customerapp.data.SupabaseClient
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NetworkTypeObserver() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var pendingLostJob: Job? = null
        var lastType = NetworkQuality.connectionType.value

        NetworkQuality.updateConnectionType(context)

        fun refreshAndMaybeReconnect() {
            NetworkQuality.updateConnectionType(context)
            val newType = NetworkQuality.connectionType.value
            if (newType != lastType && newType != ConnectionType.NONE) {
                scope.launch {
                    try {
                        SupabaseClient.client.realtime.disconnect()
                    } catch (e: Exception) {
                        // ignore
                    }
                    try {
                        SupabaseClient.client.realtime.connect()
                    } catch (e: Exception) {
                        // will retry
                    }
                }
            }
            lastType = newType
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                pendingLostJob?.cancel()
                refreshAndMaybeReconnect()
            }
            override fun onLost(network: Network) {
                pendingLostJob?.cancel()
                pendingLostJob = scope.launch {
                    delay(1200)
                    refreshAndMaybeReconnect()
                }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                pendingLostJob?.cancel()
                refreshAndMaybeReconnect()
            }
        }

        cm.registerNetworkCallback(request, callback)

        onDispose {
            pendingLostJob?.cancel()
            scope.cancel()
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Handle the exception
            }
        }
    }
}