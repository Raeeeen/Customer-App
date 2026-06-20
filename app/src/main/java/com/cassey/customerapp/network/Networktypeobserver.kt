package com.cassey.customerapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun NetworkTypeObserver() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        NetworkQuality.updateConnectionType(context)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                NetworkQuality.updateConnectionType(context)
            }
            override fun onLost(network: Network) {
                NetworkQuality.updateConnectionType(context)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                NetworkQuality.updateConnectionType(context)
            }
        }

        cm.registerNetworkCallback(request, callback)

        onDispose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Handle the exception
            }
        }
    }
}