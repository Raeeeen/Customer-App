package com.cassey.customerapp.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.Job

class HomeState(sharedPrefs: android.content.SharedPreferences) {
    var loading by mutableStateOf(false)
    var statusMessage by mutableStateOf("Locating user session & locking GPS...")
    var success by mutableStateOf<Boolean?>(null)
    var isDashboardVisible by mutableStateOf(true)
    var driverName by mutableStateOf("")
    var driverPhone by mutableStateOf("")

    var activeBookingId by mutableStateOf(sharedPrefs.getString("active_booking_id", null))
    var bookingStatus by mutableStateOf(
        if (sharedPrefs.getString("active_booking_id", null) != null) "pending" else null
    )
    var currentRiderId by mutableStateOf<String?>(null)
    var riderLatLng by mutableStateOf<LatLng?>(null)

    var dropoffLat by mutableStateOf(sharedPrefs.getFloat("saved_dropoff_lat", 0.0f).toDouble())
    var dropoffLng by mutableStateOf(sharedPrefs.getFloat("saved_dropoff_lng", 0.0f).toDouble())
    var selectedDropoffAddress by mutableStateOf(sharedPrefs.getString("saved_dropoff_address", "") ?: "")
    var dropoffInput by mutableStateOf(sharedPrefs.getString("saved_dropoff_address", "") ?: "")

    var tripDistance by mutableStateOf(sharedPrefs.getFloat("saved_trip_distance", 0.0f).toDouble())
    var estimatedFare by mutableStateOf(sharedPrefs.getFloat("saved_estimated_fare", 0.0f).toDouble())

    var pickupAddress by mutableStateOf(
        sharedPrefs.getString("saved_pickup_address", "Locating your position...") ?: "Locating your position..."
    )
    var pickupLat by mutableStateOf(sharedPrefs.getFloat("saved_pickup_lat", 7.8285f).toDouble())
    var pickupLng by mutableStateOf(sharedPrefs.getFloat("saved_pickup_lng", 123.4332f).toDouble())

    var customerRealtimeChannel by mutableStateOf<RealtimeChannel?>(null)
    var riderBroadcastChannel by mutableStateOf<RealtimeChannel?>(null)
    var nearbyRiders by mutableStateOf<List<NearbyRider>>(emptyList())
    var nearbyRidersJob by mutableStateOf<Job?>(null)
    var suggestions by mutableStateOf<List<PlaceSuggestion>>(emptyList())
    var searchJob by mutableStateOf<Job?>(null)

    var termsRecord by mutableStateOf<TermsOfServiceRecord?>(null)
    var showTermsDialog by mutableStateOf(false)
    var isTermsReviewMode by mutableStateOf(false)
    var isCustomerRealtimeConnected by mutableStateOf(true)

    fun clear(sharedPrefs: android.content.SharedPreferences) {
        sharedPrefs.edit()
            .remove("active_booking_id")
            .remove("saved_dropoff_lat")
            .remove("saved_dropoff_lng")
            .remove("saved_dropoff_address")
            .remove("saved_pickup_address")
            .remove("saved_pickup_lat")
            .remove("saved_pickup_lng")
            .remove("saved_trip_distance")
            .remove("saved_estimated_fare")
            .apply()
        activeBookingId = null
        bookingStatus = null
        currentRiderId = null
        riderLatLng = null
        estimatedFare = 0.0
        tripDistance = 0.0
        dropoffLat = 0.0
        dropoffLng = 0.0
        pickupLat = 7.8285
        pickupLng = 123.4332
        selectedDropoffAddress = ""
        dropoffInput = ""
        suggestions = emptyList()
        driverName = ""
        driverPhone = ""
        success = null
        statusMessage = "Long-press on the map to pin your destination!"
    }
}