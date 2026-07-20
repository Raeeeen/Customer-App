package com.cassey.customerapp.ui.home

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.cassey.customerapp.data.SupabaseClient
import com.cassey.customerapp.network.NetworkQuality
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.maps.android.compose.CameraPositionState
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeEffects(
    state: HomeState,
    sharedPrefs: android.content.SharedPreferences,
    context: Context,
    scope: CoroutineScope,
    fusedLocationClient: FusedLocationProviderClient,
    cameraPositionState: CameraPositionState,
    onRequestLocationPermission: () -> Unit,
    onFetchGPS: () -> Unit,
    onReverseGeocode: (Double, Double) -> Unit,
    onTripUpdate: (distance: Double, fare: Double) -> Unit,
    onClearBooking: () -> Unit,
) {

    var showCompletedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showCompletedDialog) {
        if (showCompletedDialog) {
            delay(5000)
            showCompletedDialog = false
            onClearBooking()
            onFetchGPS()
        }
    }

    // Request location permission on first launch
    LaunchedEffect(Unit) {
        if (state.activeBookingId != null) {
            val cachedAddress = sharedPrefs.getString("saved_pickup_address", "")
            if (!cachedAddress.isNullOrEmpty()) state.pickupAddress = cachedAddress
            else onReverseGeocode(state.pickupLat, state.pickupLng)
            state.pickupLat = sharedPrefs.getFloat("saved_pickup_lat", 0.0f).toDouble()
            state.pickupLng = sharedPrefs.getFloat("saved_pickup_lng", 0.0f).toDouble()
            state.dropoffLat = sharedPrefs.getFloat("saved_dropoff_lat", 0.0f).toDouble()
            state.dropoffLng = sharedPrefs.getFloat("saved_dropoff_lng", 0.0f).toDouble()
        } else {
            onRequestLocationPermission()
        }
    }

    // Booking realtime listener
    LaunchedEffect(state.activeBookingId) {
        state.customerRealtimeChannel?.let { stale ->
            withContext(NonCancellable) {
                try { stale.unsubscribe(); SupabaseClient.client.realtime.removeChannel(stale) }
                catch (e: Exception) { Log.e("Realtime", "Stale channel cleanup failed", e) }
            }
            state.customerRealtimeChannel = null
        }

        val currentBookingId = state.activeBookingId ?: return@LaunchedEffect

        try {
            val snapshot = withContext(Dispatchers.IO) {
                NetworkQuality.timed {
                    withTimeout(8000) {
                        SupabaseClient.client.from("bookings")
                            .select { filter { eq("id", currentBookingId) } }
                            .decodeSingleOrNull<BookingStatusUpdate>()
                    }
                }
            }
            if (snapshot != null) {
                state.bookingStatus = snapshot.status
                if (snapshot.status == "accepted" && snapshot.rider_id != null) {
                    Log.d("Vibration", "Status changed to accepted, hasVibrated=${state.hasVibratedForAcceptance}")
                    state.currentRiderId = snapshot.rider_id
                    if (!state.hasVibratedForAcceptance) {
                        vibrateDevice(context)
                        state.hasVibratedForAcceptance = true
                    }
                    fetchDriverDetails(snapshot.rider_id) { name, phone ->
                        state.driverName = name
                        state.driverPhone = phone
                    }
                }
                if (snapshot.status in listOf("completed", "cancelled")) {
                    onClearBooking(); onFetchGPS(); return@LaunchedEffect
                }
            }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Failed to seed booking state", e)
        }

        val channel = SupabaseClient.client.channel("customer-booking-$currentBookingId")
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "bookings"
            filter("id", FilterOperator.EQ, currentBookingId)
        }.onEach { change ->
            val updated = change.decodeRecord<BookingStatusUpdate>()
            state.bookingStatus = updated.status
            if (updated.status == "accepted") {
                state.currentRiderId = updated.rider_id
                if (!state.hasVibratedForAcceptance) {
                    vibrateDevice(context)
                    state.hasVibratedForAcceptance = true
                }
                scope.launch {
                    fetchDriverDetails(updated.rider_id) { name, phone ->
                        state.driverName = name
                        state.driverPhone = phone
                    }
                }
            }
            if (updated.status in listOf("completed", "cancelled")) {
                showCompletedDialog = true
                withContext(NonCancellable) {
                    try { channel.unsubscribe(); SupabaseClient.client.realtime.removeChannel(channel) }
                    catch (e: Exception) { Log.e("Realtime", "Channel cleanup failed", e) }
                }
                state.customerRealtimeChannel = null
                onClearBooking()
                onFetchGPS()
            }
        }.launchIn(this)

        channel.subscribe()
        state.customerRealtimeChannel = channel
    }

    // Rider live location broadcast
    LaunchedEffect(state.currentRiderId) {
        state.riderBroadcastChannel?.let { stale ->
            withContext(NonCancellable) {
                try { stale.unsubscribe(); SupabaseClient.client.realtime.removeChannel(stale) }
                catch (e: Exception) { Log.e("Realtime", "Stale broadcast cleanup failed", e) }
            }
            state.riderBroadcastChannel = null
        }

        val targetRiderId = state.currentRiderId ?: return@LaunchedEffect

        try {
            val locationData = withContext(Dispatchers.IO) {
                NetworkQuality.timed {
                    withTimeout(8000) {
                        SupabaseClient.client.from("rider_locations")
                            .select { filter { eq("rider_id", targetRiderId) } }
                            .decodeSingleOrNull<RiderLocationUpdate>()
                    }
                }
            }
            if (locationData != null) {
                state.riderLatLng = LatLng(locationData.lat, locationData.lng)
            }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Initial rider location fetch failed", e)
        }

        val broadcastChannel = SupabaseClient.client.channel("booking-updates")
        broadcastChannel.broadcastFlow<JsonObject>(event = "rider-location")
            .onEach { payload ->
                val riderId = payload["riderId"]?.jsonPrimitive?.content
                if (riderId != targetRiderId) return@onEach
                val lat = payload["lat"]?.jsonPrimitive?.double ?: return@onEach
                val lng = payload["lng"]?.jsonPrimitive?.double ?: return@onEach
                withContext(Dispatchers.Main) { state.riderLatLng = LatLng(lat, lng) }
            }.launchIn(this)

        broadcastChannel.subscribe()
        state.riderBroadcastChannel = broadcastChannel
    }

    // Nearby riders polling (10 s interval, paused during active booking)
    LaunchedEffect(state.pickupLat, state.pickupLng, state.activeBookingId) {
        if (state.activeBookingId != null) {
            state.nearbyRiders = emptyList()
            state.nearbyRidersJob?.cancel()
            return@LaunchedEffect
        }
        state.nearbyRidersJob?.cancel()
        state.nearbyRidersJob = scope.launch {
            while (isActive) {
                try {
                    val all = withContext(Dispatchers.IO) {
                        NetworkQuality.timed {
                            withTimeout(8000) {
                                SupabaseClient.client.from("rider_locations")
                                    .select { filter { eq("is_active", true) } }
                                    .decodeList<NearbyRider>()
                            }
                        }
                    }
                    val metersPerDeg = 111000.0
                    state.nearbyRiders = all.filter { rider ->
                        val latDelta = abs(rider.lat - state.pickupLat) * metersPerDeg
                        val lngDelta = abs(rider.lng - state.pickupLng) *
                                (metersPerDeg * cos(Math.toRadians(state.pickupLat)))
                        sqrt(latDelta.pow(2) + lngDelta.pow(2)) <= 200.0
                    }
                } catch (e: Exception) {
                    Log.e("CustomerApp", "Nearby riders fetch failed", e)
                }
                delay(10000L)
            }
        }
    }

    // ToS check on launch
    LaunchedEffect(Unit) {
        val fetched = fetchCustomerTermsOfService()
        if (fetched != null) {
            state.termsRecord = fetched
            val alreadyAccepted = hasAcceptedCurrentTerms(context, fetched.version)
            if (!alreadyAccepted) {
                state.isTermsReviewMode = false
                state.showTermsDialog = true
            }
        }
    }

    // Realtime connection status observer
    LaunchedEffect(Unit) {
        var debounceJob: Job? = null
        SupabaseClient.client.realtime.status.collect { status ->
            debounceJob?.cancel()
            if (status == Realtime.Status.CONNECTED) {
                state.isCustomerRealtimeConnected = true
            } else {
                debounceJob = launch {
                    delay(1500)
                    state.isCustomerRealtimeConnected = false
                }
            }
        }
    }

    // Auto-clear a stale error banner once connectivity returns
    LaunchedEffect(state.isCustomerRealtimeConnected) {
        if (state.isCustomerRealtimeConnected && state.success == false && state.activeBookingId == null) {
            state.success = null
            state.statusMessage = "Connection restored. Ready to book!"
        }
    }

    // Check for Google Play Service
    LaunchedEffect(Unit) {
        if (!isGooglePlayServicesAvailable(context)) {
            state.showGmsMissingDialog = true
        }
    }

    // Cleanup channels on dispose
    DisposableEffect(Unit) {
        onDispose {
            listOf(state.customerRealtimeChannel, state.riderBroadcastChannel).forEach { ch ->
                ch?.let {
                    GlobalScope.launch(Dispatchers.IO + NonCancellable) {
                        try { it.unsubscribe(); SupabaseClient.client.realtime.removeChannel(it) }
                        catch (e: Exception) { Log.e("Realtime", "Dispose cleanup failed", e) }
                    }
                }
            }
        }
    }

    if (showCompletedDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Ride Completed!") },
            text = {
                Text("Your Ride is Completed by the Rider.\nThanks for riding with us!")
            },
            confirmButton = {},
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF94A3B8)
        )
    }
}
