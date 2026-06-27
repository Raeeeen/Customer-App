package com.cassey.customerapp.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cassey.customerapp.data.SupabaseClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.*
import com.cassey.customerapp.network.NetworkQuality
import com.cassey.customerapp.network.NetworkStatusIndicator
import com.cassey.customerapp.network.NetworkTypeObserver
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

@Serializable
data class BookingRequest(
    @SerialName("customer_id") val customerId: String,
    @SerialName("pickup_address") val pickupAddress: String,
    @SerialName("dropoff_address") val dropoffAddress: String,
    @SerialName("pickup_latitude") val pickupLatitude: Double,
    @SerialName("pickup_longitude") val pickupLongitude: Double,
    @SerialName("dropoff_latitude") val dropoffLatitude: Double,
    @SerialName("dropoff_longitude") val dropoffLongitude: Double,
    @SerialName("fare") val fare: String,
    val status: String
)

data class PlaceSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class BookingStatusUpdate(
    val id: String,
    @SerialName("customer_id") val customer_id: String,
    @SerialName("status") val status: String,
    @SerialName("rider_id") val rider_id: String? = null
)

@Serializable
data class RiderProfile(
    val id: String,
    val name: String,
    @SerialName("phone_number") val phoneNumber: String,
    val role: String
)

@Serializable
data class FareSettings(
    val id: Long,
    val base_fare: Double,
    val succeeding_km_rate: Double
)

@Serializable
data class RiderLocationUpdate(
    @SerialName("rider_id") val riderId: String,
    @SerialName("current_latitude") val lat: Double,
    @SerialName("current_longitude") val lng: Double,
    val heading: Float,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class NearbyRider(
    @SerialName("rider_id") val riderId: String,
    @SerialName("current_latitude") val lat: Double,
    @SerialName("current_longitude") val lng: Double,
    val heading: Float,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_active") val isActive: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val isoTimestamp = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
    }

    NetworkTypeObserver()

    val sharedPrefs = remember { context.getSharedPreferences("RideGoPrefs", android.content.Context.MODE_PRIVATE) }

    // State
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Locating user session & locking GPS...") }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var isDashboardVisible by remember { mutableStateOf(true) }
    var driverName by remember { mutableStateOf("") }
    var driverPhone by remember { mutableStateOf("") }
    var activeBookingId by remember { mutableStateOf(sharedPrefs.getString("active_booking_id", null)) }
    var bookingStatus by remember { mutableStateOf(if (sharedPrefs.getString("active_booking_id", null) != null) "pending" else null) }
    var currentRiderId by remember { mutableStateOf<String?>(null) }
    var riderLatLng by remember { mutableStateOf<LatLng?>(null) }
    var dropoffLat by remember { mutableStateOf(sharedPrefs.getFloat("saved_dropoff_lat", 0.0f).toDouble()) }
    var dropoffLng by remember { mutableStateOf(sharedPrefs.getFloat("saved_dropoff_lng", 0.0f).toDouble()) }
    var selectedDropoffAddress by remember { mutableStateOf(sharedPrefs.getString("saved_dropoff_address", "") ?: "") }
    var dropoffInput by remember { mutableStateOf(sharedPrefs.getString("saved_dropoff_address", "") ?: "") }
    var tripDistance by remember { mutableStateOf(sharedPrefs.getFloat("saved_trip_distance", 0.0f).toDouble()) }
    var estimatedFare by remember { mutableStateOf(sharedPrefs.getFloat("saved_estimated_fare", 0.0f).toDouble()) }
    var pickupAddress by remember { mutableStateOf(sharedPrefs.getString("saved_pickup_address", "Locating your position...") ?: "Locating your position...") }
    var pickupLat by remember { mutableStateOf(sharedPrefs.getFloat("saved_pickup_lat", 7.8285f).toDouble()) }
    var pickupLng by remember { mutableStateOf(sharedPrefs.getFloat("saved_pickup_lng", 123.4332f).toDouble()) }
    var customerRealtimeChannel by remember { mutableStateOf<RealtimeChannel?>(null) }
    var riderBroadcastChannel by remember { mutableStateOf<RealtimeChannel?>(null) }
    var nearbyRiders by remember { mutableStateOf<List<NearbyRider>>(emptyList()) }
    var nearbyRidersJob by remember { mutableStateOf<Job?>(null) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var termsRecord by remember { mutableStateOf<TermsOfServiceRecord?>(null) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var isTermsReviewMode by remember { mutableStateOf(false) }
    var isCustomerRealtimeConnected by remember { mutableStateOf(true) }

    // Google Maps state
    val pagadianBounds = remember { LatLngBounds(LatLng(7.800, 123.360), LatLng(7.860, 123.480)) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(7.8275, 123.420), 13f)  // center of new bounds
    }

    // Tailwind Palette Configurations
    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate400 = Color(0xFF94A3B8)
    val slate300 = Color(0xFFCBD5E1)
    val emerald400 = Color(0xFF34D399)
    val rose400 = Color(0xFFFB7185)
    val indigo600 = Color(0xFF4F46E5)
    val emerald600 = Color(0xFF229954)
    val slate600 = Color(0xFF718096)

    // Reverse Geocode GPS Points for Pickup Spot
    fun reverseGeocodePickup(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val name = addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: "Current Location"
                        pickupAddress = "$name, ${addr.locality ?: ""}".trimEnd(',', ' ')
                    } else {
                        pickupAddress = "Lat: %.4f, Lng: %.4f".format(lat, lng)
                    }
                    sharedPrefs.edit().putString("saved_pickup_address", pickupAddress).apply()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pickupAddress = "Lat: %.4f, Lng: %.4f".format(lat, lng)
                }
            }
        }
    }

    // Fetch GPS Coordinates
    @SuppressLint("MissingPermission")
    fun fetchCurrentGPSLocation() {
        if (activeBookingId != null) return
        statusMessage = "Fetching precise GPS coordinates..."
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    pickupLat = location.latitude
                    pickupLng = location.longitude
                    sharedPrefs.edit()
                        .putFloat("saved_pickup_lat", location.latitude.toFloat())
                        .putFloat("saved_pickup_lng", location.longitude.toFloat())
                        .apply()
                    reverseGeocodePickup(location.latitude, location.longitude)
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f)
                        )
                    }
                    statusMessage = "GPS lock established. Long-press map to pin destination!"
                } else {
                    statusMessage = "Failed to lock GPS. Using Pagadian City center fallback."
                }
            }
            .addOnFailureListener {
                statusMessage = "GPS error: ${it.localizedMessage}"
            }
    }

    // Permission Request Launcher Engine
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentGPSLocation()
        } else {
            statusMessage = "Location access denied. Using center city defaults."
            pickupAddress = "Pagadian City Center (Manual Access)"
        }
    }

    //  Rider Details when accepted
    suspend fun fetchDriverDetails(riderId: String?) {
        if (riderId.isNullOrEmpty()) { driverName = ""; driverPhone = ""; return }
        try {
            val profile = NetworkQuality.timed {
                withTimeout(6000) {
                    SupabaseClient.client.from("profiles")
                        .select { filter { eq("id", riderId) } }
                        .decodeSingleOrNull<RiderProfile>()
                }
            }
            if (profile != null) { driverName = profile.name; driverPhone = profile.phoneNumber }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Failed to fetch rider profile", e)
        }
    }

    // Get fare from the fare table in database
    suspend fun fetchLiveFareSettings(): FareSettings? {
        return withContext(Dispatchers.IO) {
            try {
                NetworkQuality.timed {
                    withTimeout(6000) {
                        SupabaseClient.client.postgrest.from("fare")
                            .select { filter { eq("id", 1) } }
                            .decodeSingle<FareSettings>()
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerApp", "Failed to fetch fare settings", e)
                null
            }
        }
    }

    // Calculate distance between two coordinates
    fun calculateDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadiusKm * 2 * asin(sqrt(a))
    }

    // Get the coordinates of the pickup and dropoff points
    fun updateTripDetails(pLat: Double, pLng: Double, dLat: Double, dLng: Double) {
        if (bookingStatus != null || activeBookingId != null) return
        if (pLat != 0.0 && pLng != 0.0 && dLat != 0.0 && dLng != 0.0) {
            val distance = calculateDistanceInKm(pLat, pLng, dLat, dLng)
            tripDistance = distance
            sharedPrefs.edit().putFloat("saved_trip_distance", distance.toFloat()).apply()
            scope.launch {
                val settings = fetchLiveFareSettings()
                val base = settings?.base_fare ?: 15.0
                val succeeding = settings?.succeeding_km_rate ?: 2.0
                val fare = if (distance <= 1.0) base else {
                    base + (ceil(distance - 1.0) * succeeding)
                }
                withContext(Dispatchers.Main) {
                    estimatedFare = fare
                    sharedPrefs.edit().putFloat("saved_estimated_fare", fare.toFloat()).apply()
                }
            }
        }
    }

    // Smart snap: find nearest recognized place, fall back to geocode
    fun smartSnapDropoff(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            try {
                val mapsApiKey = com.cassey.customerapp.BuildConfig.MAPS_API_KEY

                // 1. Try Places Nearby Search first — prefers named places over streets
                val nearbyUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                        "?location=$lat,$lng" +
                        "&rankby=distance" +
                        "&type=establishment" +
                        "&key=$mapsApiKey"

                val nearbyConn = URL(nearbyUrl).openConnection() as HttpURLConnection
                nearbyConn.connectTimeout = 6000
                nearbyConn.readTimeout = 6000

                if (nearbyConn.responseCode == 200) {
                    val response = nearbyConn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val results = json.optJSONArray("results")

                    if (results != null && results.length() > 0) {
                        val top = results.getJSONObject(0)
                        val name = top.optString("name", "")
                        val vicinity = top.optString("vicinity", "")
                        val location = top.optJSONObject("geometry")?.optJSONObject("location")
                        val placeLat = location?.getDouble("lat") ?: lat
                        val placeLng = location?.getDouble("lng") ?: lng

                        // Only snap if within 80m — otherwise it's too far from actual pin
                        val distToPlace = calculateDistanceInKm(lat, lng, placeLat, placeLng) * 1000
                        val snappedLat = if (distToPlace <= 250) placeLat else lat
                        val snappedLng = if (distToPlace <= 250) placeLng else lng
                        val displayName = buildString {
                            if (name.isNotEmpty()) append(name)
                            if (vicinity.isNotEmpty()) {
                                if (name.isNotEmpty()) append(", ")
                                append(vicinity)
                            }
                            if (isEmpty()) append("Pinned: (%.4f, %.4f)".format(lat, lng))
                        }

                        withContext(Dispatchers.Main) {
                            dropoffLat = snappedLat
                            dropoffLng = snappedLng
                            selectedDropoffAddress = displayName
                            dropoffInput = displayName
                            sharedPrefs.edit()
                                .putFloat("saved_dropoff_lat", snappedLat.toFloat())
                                .putFloat("saved_dropoff_lng", snappedLng.toFloat())
                                .putString("saved_dropoff_address", displayName)
                                .apply()
                            updateTripDetails(pickupLat, pickupLng, snappedLat, snappedLng)
                            statusMessage = "📍 Snapped to: $displayName"
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerApp", "Nearby search failed, falling back to geocoder", e)
            }

            // 2. Fallback — reverse geocode if Places API fails
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val feature = addr.featureName?.takeIf { it.first().isLetter() }
                            ?: addr.thoroughfare
                            ?: addr.subLocality
                            ?: "Pinned Location"
                        val locality = addr.locality ?: "Pagadian City"
                        val displayName = "$feature, $locality"
                        selectedDropoffAddress = displayName
                        dropoffInput = displayName
                        sharedPrefs.edit().putString("saved_dropoff_address", displayName).apply()
                        updateTripDetails(pickupLat, pickupLng, lat, lng)
                        statusMessage = "📍 Pinned to: $displayName"
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f)
                            )
                        }
                    } else {
                        val coordString = "Pinned: (%.4f, %.4f)".format(lat, lng)
                        selectedDropoffAddress = coordString
                        dropoffInput = coordString
                        statusMessage = "📍 Pinned to custom coordinates"
                    }
                    sharedPrefs.edit().putString("saved_dropoff_address", selectedDropoffAddress).apply()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val coordString = "Pinned: (%.4f, %.4f)".format(lat, lng)
                    selectedDropoffAddress = coordString
                    dropoffInput = coordString
                }
            }
        }
    }

    // Suggestions then auto mark in the map
    fun searchDestination(query: String) {
        searchJob?.cancel()
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) { suggestions = emptyList(); return }
        searchJob = scope.launch(Dispatchers.IO) {
            delay(200)
            try {
                val mapsApiKey = com.cassey.customerapp.BuildConfig.MAPS_API_KEY
                val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")

                val urlString = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                        "?input=$encodedQuery" +
                        "&location=7.8275,123.420" +
                        "&radius=9000" +
                        "&strictbounds=true" +
                        "&key=$mapsApiKey"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.connectTimeout = 6000
                connection.readTimeout = 6000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val predictions = json.optJSONArray("predictions") ?: JSONArray()
                    val loaded = mutableListOf<PlaceSuggestion>()

                    for (i in 0 until predictions.length()) {
                        val prediction = predictions.getJSONObject(i)
                        val placeId = prediction.optString("place_id", "")
                        val description = prediction.optString("description", "").trim()
                        if (placeId.isEmpty() || description.isEmpty()) continue

                        try {
                            val detailsUrl = "https://maps.googleapis.com/maps/api/place/details/json" +
                                    "?place_id=$placeId" +
                                    "&fields=geometry" +
                                    "&key=$mapsApiKey"

                            val detailsConnection = URL(detailsUrl).openConnection() as HttpURLConnection
                            detailsConnection.connectTimeout = 5000
                            detailsConnection.readTimeout = 5000

                            if (detailsConnection.responseCode == 200) {
                                val detailsResponse = detailsConnection.inputStream.bufferedReader().use { it.readText() }
                                val detailsJson = JSONObject(detailsResponse)
                                val location = detailsJson
                                    .optJSONObject("result")
                                    ?.optJSONObject("geometry")
                                    ?.optJSONObject("location")

                                if (location != null) {
                                    val lat = location.getDouble("lat")
                                    val lng = location.getDouble("lng")

                                    // Hard filter — drop anything outside the actual map bounds
                                    if (lat in 7.800..7.860 && lng in 123.360..123.480) {
                                        loaded.add(PlaceSuggestion(description, lat, lng))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CustomerApp", "Place details fetch failed for $placeId", e)
                            continue
                        }
                    }

                    withContext(Dispatchers.Main) { suggestions = loaded.take(5) }
                }
            } catch (e: Exception) {
                Log.e("CustomerApp", "Search failed", e)
            }
        }
    }

    fun clearBookingState() {
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
        pickupLat = 0.0
        pickupLng = 0.0
        selectedDropoffAddress = ""
        dropoffInput = ""
        suggestions = emptyList()
        driverName = ""
        driverPhone = ""
        success = null
        statusMessage = "Long-press on the map to pin your destination!"
    }

    // Permission request on launch
    LaunchedEffect(Unit) {
        if (activeBookingId != null) {
            val cachedAddress = sharedPrefs.getString("saved_pickup_address", "")
            if (!cachedAddress.isNullOrEmpty()) pickupAddress = cachedAddress
            else reverseGeocodePickup(pickupLat, pickupLng)
            pickupLat = sharedPrefs.getFloat("saved_pickup_lat", 0.0f).toDouble()
            pickupLng = sharedPrefs.getFloat("saved_pickup_lng", 0.0f).toDouble()
            dropoffLat = sharedPrefs.getFloat("saved_dropoff_lat", 0.0f).toDouble()
            dropoffLng = sharedPrefs.getFloat("saved_dropoff_lng", 0.0f).toDouble()
        } else {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Booking realtime listener
    LaunchedEffect(activeBookingId) {
        customerRealtimeChannel?.let { stale ->
            withContext(NonCancellable) {
                try { stale.unsubscribe(); SupabaseClient.client.realtime.removeChannel(stale) }
                catch (e: Exception) { Log.e("Realtime", "Stale channel cleanup failed", e) }
            }
            customerRealtimeChannel = null
        }

        val currentBookingId = activeBookingId ?: return@LaunchedEffect

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
                bookingStatus = snapshot.status
                if (snapshot.status == "accepted" && snapshot.rider_id != null) {
                    currentRiderId = snapshot.rider_id
                    fetchDriverDetails(snapshot.rider_id)
                }
                if (snapshot.status in listOf("completed", "cancelled")) {
                    clearBookingState()
                    fetchCurrentGPSLocation()
                    return@LaunchedEffect
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
            bookingStatus = updated.status
            if (updated.status == "accepted") {
                currentRiderId = updated.rider_id
                scope.launch { fetchDriverDetails(updated.rider_id) }
            }
            if (updated.status in listOf("completed", "cancelled")) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Your ride is complete!", Toast.LENGTH_LONG).show()
                }
                withContext(NonCancellable) {
                    try { channel.unsubscribe(); SupabaseClient.client.realtime.removeChannel(channel) }
                    catch (e: Exception) { Log.e("Realtime", "Channel cleanup failed", e) }
                }
                customerRealtimeChannel = null
                clearBookingState()
                fetchCurrentGPSLocation()
            }
        }.launchIn(this)

        channel.subscribe()
        customerRealtimeChannel = channel
    }

    // Rider location broadcast listener
    LaunchedEffect(currentRiderId) {
        riderBroadcastChannel?.let { stale ->
            withContext(NonCancellable) {
                try { stale.unsubscribe(); SupabaseClient.client.realtime.removeChannel(stale) }
                catch (e: Exception) { Log.e("Realtime", "Stale broadcast cleanup failed", e) }
            }
            riderBroadcastChannel = null
        }

        val targetRiderId = currentRiderId ?: return@LaunchedEffect

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
                riderLatLng = LatLng(locationData.lat, locationData.lng)
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
                withContext(Dispatchers.Main) {
                    riderLatLng = LatLng(lat, lng)
                }
            }.launchIn(this)

        broadcastChannel.subscribe()
        riderBroadcastChannel = broadcastChannel
    }

    // Nearby riders polling
    LaunchedEffect(pickupLat, pickupLng, activeBookingId) {
        if (activeBookingId != null) {
            nearbyRiders = emptyList()
            nearbyRidersJob?.cancel()
            return@LaunchedEffect
        }
        nearbyRidersJob?.cancel()
        nearbyRidersJob = scope.launch {
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
                    nearbyRiders = all.filter { rider ->
                        val latDelta = abs(rider.lat - pickupLat) * metersPerDeg
                        val lngDelta = abs(rider.lng - pickupLng) * (metersPerDeg * cos(Math.toRadians(pickupLat)))
                        sqrt(latDelta.pow(2) + lngDelta.pow(2)) <= 200.0
                    }
                } catch (e: Exception) {
                    Log.e("CustomerApp", "Nearby riders fetch failed", e)
                }
                delay(10000L)
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            listOf(customerRealtimeChannel, riderBroadcastChannel).forEach { ch ->
                ch?.let {
                    GlobalScope.launch(Dispatchers.IO + NonCancellable) {
                        try { it.unsubscribe(); SupabaseClient.client.realtime.removeChannel(it) }
                        catch (e: Exception) { Log.e("Realtime", "Dispose cleanup failed", e) }
                    }
                }
            }
        }
    }

    // ToS
    LaunchedEffect(Unit) {
        val fetched = fetchCustomerTermsOfService()
        if (fetched != null) {
            termsRecord = fetched
            val alreadyAccepted = hasAcceptedCurrentTerms(context, fetched.version)
            if (!alreadyAccepted) {
                isTermsReviewMode = false
                showTermsDialog = true
            }
        }
    }

    // Realtime status
    LaunchedEffect(Unit) {
        SupabaseClient.client.realtime.status.collect { status ->
            isCustomerRealtimeConnected = status == Realtime.Status.CONNECTED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Ride&Go", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(
                        onClick = {
                            if (termsRecord != null) {
                                isTermsReviewMode = true
                                showTermsDialog = true
                            } else {
                                Toast.makeText(context, "Terms of Service unavailable right now.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View Terms of Service",
                            tint = Color.White
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    SupabaseClient.client.auth.signOut()
                                } catch (e: Exception) {
                                    Log.e("RideGoAuth", "Logout failed", e)
                                }
                            }
                        }
                    ) {
                        Text(text = "Log Out", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00B14F))
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = if (isDashboardVisible) Alignment.BottomCenter else Alignment.BottomCenter
        ) {

            // Google Map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    latLngBoundsForCameraTarget = pagadianBounds,
                    minZoomPreference = 13f,
                    maxZoomPreference = 25f
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                ),
                onMapLongClick = { point ->
                    if (pagadianBounds.contains(point)) {
                        if (activeBookingId != null) return@GoogleMap
                        dropoffLat = point.latitude
                        dropoffLng = point.longitude
                        statusMessage = "🔍 Finding nearest place..."
                        smartSnapDropoff(point.latitude, point.longitude)
                    } else {
                        statusMessage = "⚠️ Outside operational zone. Pin inside Pagadian City."
                    }
                }
            ) {
                // Pickup marker
                if (pickupLat != 0.0 && pickupLng != 0.0) {
                    val pickupMarkerState = rememberMarkerState(
                        position = LatLng(pickupLat, pickupLng)
                    )
                    LaunchedEffect(pickupLat, pickupLng) {
                        pickupMarkerState.position = LatLng(pickupLat, pickupLng)
                    }
                    Marker(
                        state = pickupMarkerState,
                        title = "Your Pickup Location",
                        snippet = pickupAddress
                    )
                }

                // Dropoff marker
                if (dropoffLat != 0.0 && dropoffLng != 0.0) {
                    val dropoffMarkerState = rememberMarkerState(
                        position = LatLng(dropoffLat, dropoffLng)
                    )
                    LaunchedEffect(dropoffLat, dropoffLng) {
                        dropoffMarkerState.position = LatLng(dropoffLat, dropoffLng)
                    }
                    Marker(
                        state = dropoffMarkerState,
                        title = "Dropoff Destination",
                        snippet = selectedDropoffAddress
                    )
                }

                // Rider marker
                riderLatLng?.let { rLatLng ->
                    val riderMarkerState = rememberMarkerState(position = rLatLng)
                    LaunchedEffect(rLatLng) {
                        riderMarkerState.position = rLatLng
                    }
                    Marker(
                        state = riderMarkerState,
                        title = if (driverName.isNotEmpty()) "$driverName is on the way" else "Rider is on the way"
                    )
                }

                // Nearby rider markers (when no active booking)
                if (activeBookingId == null) {
                    nearbyRiders.forEach { rider ->
                        val nearbyMarkerState = rememberMarkerState(
                            position = LatLng(rider.lat, rider.lng)
                        )
                        Marker(
                            state = nearbyMarkerState,
                            title = "Nearby Rider"
                        )
                    }

                    if (pickupLat != 0.0 && pickupLng != 0.0) {
                        Circle(
                            center = LatLng(pickupLat, pickupLng),
                            radius = 400.0,
                            strokeColor = Color(0xFF0A84FF),
                            strokeWidth = 2f,
                            fillColor = Color(0x220A84FF)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                NetworkStatusIndicator()
            }

            // Dashboard panel
            if (isDashboardVisible) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = slate800.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, slate700)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Booking Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            IconButton(onClick = { isDashboardVisible = false }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Hide Dashboard",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = slate900),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📍 PICKUP LOCATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = emerald400
                                )
                                Text(text = pickupAddress, style = MaterialTheme.typography.bodyMedium, color = Color.White)

                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = "ESTIMATED FARE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = slate400
                                    )
                                    Text(
                                        text = "₱${String.format("%.2f", estimatedFare)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = emerald400
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = slate800)

                                Text(
                                    text = "🏁 DROPOFF TARGET DESTINATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = rose400
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = dropoffInput,
                                    onValueChange = { newValue ->
                                        dropoffInput = newValue
                                        if (newValue.trim().isEmpty()) {
                                            dropoffLat = 0.0
                                            dropoffLng = 0.0
                                            selectedDropoffAddress = ""
                                            suggestions = emptyList()
                                            statusMessage = "Long-press on the map to pin your destination!"
                                        } else {
                                            searchDestination(newValue)
                                        }
                                    },
                                    enabled = activeBookingId == null,
                                    placeholder = {
                                        Text(
                                            text = if (activeBookingId != null) "Booking in progress..." else "Type destination or long-press map...",
                                            color = slate400
                                        )
                                    },
                                    trailingIcon = {
                                        if (dropoffInput.isNotEmpty() && activeBookingId == null) {
                                            IconButton(onClick = {
                                                dropoffInput = ""
                                                dropoffLat = 0.0
                                                dropoffLng = 0.0
                                                selectedDropoffAddress = ""
                                                suggestions = emptyList()
                                                statusMessage = "Long-press on the map to pin your destination!"
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = slate400
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        disabledBorderColor = slate700.copy(alpha = 0.5f),
                                        focusedBorderColor = indigo600,
                                        unfocusedBorderColor = slate700
                                    )
                                )


                                // Live suggestion list — renders inline below the field
                                AnimatedVisibility(
                                    visible = suggestions.isNotEmpty() && activeBookingId == null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                                            .background(slate800)
                                            .border(
                                                width = 1.dp,
                                                color = slate700,
                                                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                            )
                                    ) {
                                        suggestions.forEachIndexed { index, suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        dropoffLat = suggestion.latitude
                                                        dropoffLng = suggestion.longitude
                                                        selectedDropoffAddress = suggestion.displayName
                                                        dropoffInput = suggestion.displayName
                                                        suggestions = emptyList()
                                                        sharedPrefs.edit()
                                                            .putFloat("saved_dropoff_lat", suggestion.latitude.toFloat())
                                                            .putFloat("saved_dropoff_lng", suggestion.longitude.toFloat())
                                                            .putString("saved_dropoff_address", suggestion.displayName)
                                                            .apply()
                                                        updateTripDetails(pickupLat, pickupLng, suggestion.latitude, suggestion.longitude)
                                                        scope.launch {
                                                            cameraPositionState.animate(
                                                                CameraUpdateFactory.newLatLngZoom(
                                                                    LatLng(suggestion.latitude, suggestion.longitude), 22f
                                                                )
                                                            )
                                                        }
                                                        statusMessage = "Destination set!"
                                                    }
                                                    .background(
                                                        if (index % 2 == 0) slate800 else slate700.copy(alpha = 0.2f)
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = emerald400,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = suggestion.displayName,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (index < suggestions.size - 1) {
                                                HorizontalDivider(color = slate700.copy(alpha = 0.5f), thickness = 0.5.dp)
                                            }
                                        }
                                    }
                                }

                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (activeBookingId != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = slate900),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, indigo600.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val context = LocalContext.current
                                    when (bookingStatus) {
                                        "pending" -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(32.dp),
                                                color = emerald400,
                                                strokeWidth = 3.dp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Waiting for Confirmation...",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = "Waiting for a nearby rider to accept your request.",
                                                color = slate400,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )

                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            val currentBookingId = activeBookingId

                                                            if (!currentBookingId.isNullOrEmpty()) {
                                                                SupabaseClient.client.from("bookings")
                                                                    .delete {
                                                                        filter {
                                                                            eq(
                                                                                "id",
                                                                                currentBookingId
                                                                            )
                                                                            eq("status", "pending")
                                                                        }
                                                                    }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e(
                                                                "RideGoCancel",
                                                                "Failed to delete booking row",
                                                                e
                                                            )
                                                        } finally {
                                                            clearBookingState()
                                                            Toast.makeText(
                                                                context,
                                                                "Booking cancelled.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            fetchCurrentGPSLocation()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = "Cancel Booking Request",
                                                    color = rose400,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }

                                        "accepted" -> {
                                            Text(
                                                text = "🚖 Rider Dispatched!",
                                                fontWeight = FontWeight.Bold,
                                                color = emerald400,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = slate800)
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Rider Assigned:",
                                                    color = slate400,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = driverName.ifEmpty { "Rider Found" },
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Contact Number:",
                                                    color = slate400,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = driverPhone.ifEmpty { "Updating..." },
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = indigo600.copy(
                                                        alpha = 0.2f
                                                    )
                                                ),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "📞 Note: The rider will call you to confirm your exact pickup location.",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = "Select Payment Method to Complete Ride:",
                                                color = slate400,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {},
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = emerald600
                                                    )
                                                ) { Text("Cash") }

                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            var updateSucceeded = false
                                                            try {
                                                                activeBookingId?.let { bookingId ->
                                                                    NetworkQuality.timed {
                                                                        withTimeout(10000) {
                                                                            SupabaseClient.client.from(
                                                                                "bookings"
                                                                            ).update(
                                                                                mapOf(
                                                                                    "status" to "completed",
                                                                                    "updated_at" to isoTimestamp
                                                                                )
                                                                            ) {
                                                                                filter {
                                                                                    eq(
                                                                                        "id",
                                                                                        bookingId
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                    updateSucceeded = true
                                                                }
                                                            } catch (e: TimeoutCancellationException) {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Connection too slow. Please try again.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Couldn't reach server. Please try again.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                            if (updateSucceeded) {
                                                                val fare = estimatedFare
                                                                clearBookingState()
                                                                Toast.makeText(
                                                                    context,
                                                                    "Ride completed! ₱${
                                                                        String.format(
                                                                            "%.2f",
                                                                            fare
                                                                        )
                                                                    } via GCash.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                                fetchCurrentGPSLocation()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF0052FF)
                                                    )
                                                ) { Text("GCash") }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Ride in progress - Cancellation not available.",
                                                color = slate600,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val statusBgColor = when (success) {
                                true -> emerald400.copy(alpha = 0.15f)
                                false -> rose400.copy(alpha = 0.15f)
                                null -> slate900
                            }
                            val statusTextColor = when (success) {
                                true -> emerald400
                                false -> rose400
                                null -> slate300
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = statusBgColor),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, statusTextColor.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = statusMessage,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        success = null
                                        statusMessage = "Checking target destination routing matrix..."

                                        val finalLat = dropoffLat
                                        val finalLng = dropoffLng
                                        val finalAddress = selectedDropoffAddress.ifEmpty { dropoffInput }
                                        val finalFareText = String.format("%.2f", estimatedFare)

                                        try {
                                            val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id
                                            if (currentUserId == null) {
                                                throw Exception("Session expired. Please log back in.")
                                            }

                                            val finalBooking = BookingRequest(
                                                customerId = currentUserId,
                                                pickupAddress = pickupAddress,
                                                dropoffAddress = finalAddress,
                                                pickupLatitude = pickupLat,
                                                pickupLongitude = pickupLng,
                                                dropoffLatitude = finalLat,
                                                dropoffLongitude = finalLng,
                                                fare = finalFareText,
                                                status = "pending"
                                            )

                                            val insertedBooking = NetworkQuality.timed {
                                                withTimeout (8000) {
                                                    SupabaseClient.client.from("bookings")
                                                        .insert(finalBooking) { select() }
                                                        .decodeSingleOrNull<BookingStatusUpdate>()
                                                }
                                            }

                                            if (insertedBooking?.id != null) {
                                                val realBookingId = insertedBooking.id

                                                sharedPrefs.edit().putString("active_booking_id", realBookingId).apply()
                                                activeBookingId = realBookingId
                                                bookingStatus = "pending"
                                                success = true
                                                statusMessage = "Booking requested! Waiting for driver acceptance..."
                                            } else {
                                                throw Exception("Database failed to return valid confirmation signature reference.")
                                            }
                                        } catch (e: TimeoutCancellationException) {
                                            success = false
                                            statusMessage = "Your connection is very slow — the request may still be processing. Please wait a moment before retrying to avoid a duplicate booking."
                                            Log.e("RideGoSubmit", "Booking insert timed out", e)
                                        } catch (e: Exception) {
                                            success = false
                                            statusMessage = "Transmission error: ${e.localizedMessage ?: "Network block error"}"
                                            Log.e("RideGoSubmit", "Supabase error core", e)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                },
                                enabled = !loading && dropoffLat != 0.0 && dropoffLng != 0.0,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = indigo600,
                                    disabledContainerColor = slate700
                                )
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    val buttonText = if (dropoffLat == 0.0 || dropoffLng == 0.0) {
                                        "Select a Destination to Book"
                                    } else {
                                        "Confirm Booking Request"
                                    }
                                    Text(text = buttonText, fontWeight = FontWeight.Bold, color = if (dropoffLat == 0.0) slate400 else Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                // Dashboard when its hidden
                Button(
                    onClick = { isDashboardVisible = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF283593))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Show Dashboard",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Show Booking Panel", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
    termsRecord?.let { terms ->
        if (showTermsDialog) {
            TermsOfServiceDialog(
                terms = terms,
                isReadOnlyReview = isTermsReviewMode,
                onAccept = { showTermsDialog = false },
                onDismissReview = { showTermsDialog = false }
            )
        }
    }
}