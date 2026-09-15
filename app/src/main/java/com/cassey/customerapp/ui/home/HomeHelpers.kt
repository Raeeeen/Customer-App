package com.cassey.customerapp.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.cassey.customerapp.data.SupabaseClient
import com.cassey.customerapp.network.NetworkQuality
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*
import android.location.LocationManager
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import android.widget.Toast
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

fun reverseGeocodePickup(
    lat: Double,
    lng: Double,
    context: Context,
    scope: CoroutineScope,
    sharedPrefs: android.content.SharedPreferences,
    onResult: (String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            withContext(Dispatchers.Main) {
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val name = addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: "Current Location"
                    val result = "$name, ${addr.locality ?: ""}".trimEnd(',', ' ')
                    onResult(result)
                    sharedPrefs.edit().putString("saved_pickup_address", result).apply()
                } else {
                    onResult("Lat: %.4f, Lng: %.4f".format(lat, lng))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult("Lat: %.4f, Lng: %.4f".format(lat, lng))
            }
        }
    }
}

private var currentGpsJob: Job? = null

@SuppressLint("MissingPermission")
fun fetchCurrentGPSLocation(
    activeBookingIdProvider: () -> String?,
    fusedLocationClient: FusedLocationProviderClient,
    scope: CoroutineScope,
    sharedPrefs: android.content.SharedPreferences,
    context: Context,
    cameraPositionState: CameraPositionState,
    shouldAnimateCameraToPickup: () -> Boolean,
    onStatusMessage: (String) -> Unit,
    onPickupLatLng: (Double, Double) -> Unit,
    onReverseGeocode: (Double, Double) -> Unit,
    onGpsReadyChanged: (Boolean) -> Unit,
    onFixTimestampUpdated: (Long) -> Unit
) {
    if (activeBookingIdProvider() != null) return
    currentGpsJob?.cancel()

    onStatusMessage("Fetching your location...")

    val moveThresholdMeters = 15.0
    val goodAccuracyMeters = 50f
    var lastLat: Double? = null
    var lastLng: Double? = null
    var hasFirstFix = false
    var hasWarnedLocationServicesOff = false

    fun applyFix(location: Location, isConfirmedLive: Boolean) {
        val movedMeters = if (lastLat != null && lastLng != null) {
            calculateDistanceInKm(lastLat!!, lastLng!!, location.latitude, location.longitude) * 1000
        } else Double.MAX_VALUE

        if (!hasFirstFix || movedMeters >= moveThresholdMeters) {
            onPickupLatLng(location.latitude, location.longitude)
            onReverseGeocode(location.latitude, location.longitude)
            if (shouldAnimateCameraToPickup()) {
                scope.launch {
                    try {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f)
                        )
                    } catch (e: Exception) {
                        Log.e("CustomerApp", "Camera animate failed, ignoring", e)
                    }
                }
            }
        }

        if (!isConfirmedLive) {
            onGpsReadyChanged(false)
            onStatusMessage("Showing your last known location — confirming your current position...")
            return
        }

        lastLat = location.latitude
        lastLng = location.longitude
        sharedPrefs.edit()
            .putFloat("saved_pickup_lat", location.latitude.toFloat())
            .putFloat("saved_pickup_lng", location.longitude.toFloat())
            .apply()
        hasFirstFix = true
        onGpsReadyChanged(true)
        onFixTimestampUpdated(System.currentTimeMillis())
        onStatusMessage(
            if (location.accuracy <= goodAccuracyMeters)
                "Location ready. Long-press map to pin destination!"
            else
                "Rough location — refining accuracy..."
        )
    }

    currentGpsJob = scope.launch {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { cached ->
                val maxCacheAgeMs = 2 * 60 * 1000L
                val isFresh = cached != null &&
                        (System.currentTimeMillis() - cached.time) <= maxCacheAgeMs
                if (isFresh && !hasFirstFix) {
                    applyFix(cached!!, isConfirmedLive = false)
                }
            }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Cached location fetch failed", e)
        }

        var callback: LocationCallback? = null
        try {
            while (isActive) {
                if (activeBookingIdProvider() != null) return@launch

                val servicesOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (!servicesOn) {
                    callback?.let { fusedLocationClient.removeLocationUpdates(it) }
                    callback = null
                    onStatusMessage("Location services are off. Turn on GPS to continue")
                    onGpsReadyChanged(false)
                    if (!hasWarnedLocationServicesOff) {
                        Toast.makeText(
                            context,
                            "Turn on Location Services\nto book a ride.",
                            Toast.LENGTH_LONG
                        ).show()
                        hasWarnedLocationServicesOff = true
                    }
                } else {
                    hasWarnedLocationServicesOff = false
                    if (callback == null) {
                        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                            .setMinUpdateIntervalMillis(2000L)
                            .build()
                        val cb = object : LocationCallback() {
                            override fun onLocationResult(result: LocationResult) {
                                result.lastLocation?.let { applyFix(it, isConfirmedLive = true) }
                            }
                            override fun onLocationAvailability(availability: LocationAvailability) {
                                Log.d("GPSDebug", "isLocationAvailable=${availability.isLocationAvailable}")
                            }
                        }
                        fusedLocationClient.requestLocationUpdates(request, cb, context.mainLooper)
                            .addOnFailureListener { e ->
                                Log.e("GPSDebug", "requestLocationUpdates registration FAILED: ${e.javaClass.simpleName} - ${e.message}", e)
                            }
                        callback = cb
                    }
                }
                delay(3000L)
            }
        } finally {
            callback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
    }
}

suspend fun fetchDriverDetails(
    riderId: String?,
    onResult: (name: String, phone: String) -> Unit
) {
    if (riderId.isNullOrEmpty()) { onResult("", ""); return }
    try {
        val profile = NetworkQuality.timed {
            withTimeout(6000) {
                SupabaseClient.client.from("profiles")
                    .select { filter { eq("id", riderId) } }
                    .decodeSingleOrNull<RiderProfile>()
            }
        }
        if (profile != null) onResult(profile.name, profile.phoneNumber)
    } catch (e: Exception) {
        Log.e("CustomerApp", "Failed to fetch rider profile", e)
    }
}

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

fun calculateDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return earthRadiusKm * 2 * asin(sqrt(a))
}

fun updateTripDetails(
    pLat: Double, pLng: Double,
    dLat: Double, dLng: Double,
    bookingStatus: String?,
    activeBookingId: String?,
    scope: CoroutineScope,
    sharedPrefs: android.content.SharedPreferences,
    onResult: (distance: Double, fare: Double) -> Unit
) {
    if (bookingStatus != null || activeBookingId != null) return
    if (pLat != 0.0 && pLng != 0.0 && dLat != 0.0 && dLng != 0.0) {
        val distance = calculateDistanceInKm(pLat, pLng, dLat, dLng)
        sharedPrefs.edit().putFloat("saved_trip_distance", distance.toFloat()).apply()
        scope.launch {
            val settings = fetchLiveFareSettings()
            val base = settings?.base_fare ?: 15.0
            val succeeding = settings?.succeeding_km_rate ?: 2.0
            val fare = if (distance <= 1.0) base else base + (ceil(distance - 1.0) * succeeding)
            withContext(Dispatchers.Main) {
                sharedPrefs.edit().putFloat("saved_estimated_fare", fare.toFloat()).apply()
                onResult(distance, fare)
            }
        }
    }
}

fun smartSnapDropoff(
    lat: Double, lng: Double,
    mapsApiKey: String,
    context: Context,
    scope: CoroutineScope,
    sharedPrefs: android.content.SharedPreferences,
    cameraPositionState: CameraPositionState,
    pickupLat: Double, pickupLng: Double,
    bookingStatus: String?,
    activeBookingId: String?,
    onJobCreated: (Job) -> Unit,
    onResult: (snappedLat: Double, snappedLng: Double, displayName: String) -> Unit,
    onTripUpdate: (distance: Double, fare: Double) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val job = scope.launch(Dispatchers.IO) {
        try {
            val nearbyUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=$lat,$lng&rankby=distance&type=establishment&key=$mapsApiKey"
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
                    val distToPlace = calculateDistanceInKm(lat, lng, placeLat, placeLng) * 1000
                    val snappedLat = if (distToPlace <= 250) placeLat else lat
                    val snappedLng = if (distToPlace <= 250) placeLng else lng
                    val displayName = buildString {
                        if (name.isNotEmpty()) append(name)
                        if (vicinity.isNotEmpty()) { if (name.isNotEmpty()) append(", "); append(vicinity) }
                        if (isEmpty()) append("Pinned: (%.4f, %.4f)".format(lat, lng))
                    }

                    withContext(Dispatchers.Main) {
                        sharedPrefs.edit()
                            .putFloat("saved_dropoff_lat", snappedLat.toFloat())
                            .putFloat("saved_dropoff_lng", snappedLng.toFloat())
                            .putString("saved_dropoff_address", displayName)
                            .apply()
                        onResult(snappedLat, snappedLng, displayName)
                        updateTripDetails(
                            pickupLat, pickupLng, snappedLat, snappedLng,
                            bookingStatus, activeBookingId, scope, sharedPrefs, onTripUpdate
                        )
                        onStatusMessage("📍 Drop-off to: $displayName")
                    }
                    return@launch
                }
            }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Nearby search failed, falling back to geocoder", e)
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            withContext(Dispatchers.Main) {
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.featureName?.takeIf { it.first().isLetter() }
                        ?: addr.thoroughfare ?: addr.subLocality ?: "Pinned Location"
                    val locality = addr.locality ?: "Pagadian City"
                    val displayName = "$feature, $locality"
                    sharedPrefs.edit().putString("saved_dropoff_address", displayName).apply()
                    onResult(lat, lng, displayName)
                    updateTripDetails(
                        pickupLat, pickupLng, lat, lng,
                        bookingStatus, activeBookingId, scope, sharedPrefs, onTripUpdate
                    )
                    onStatusMessage("📍 Pinned to: $displayName")
                    scope.launch {
                        try {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
                        } catch (e: Exception) {
                            Log.e("CustomerApp", "Camera animate failed during snap, ignoring", e)
                        }
                    }
                } else {
                    val coordString = "Pinned: (%.4f, %.4f)".format(lat, lng)
                    sharedPrefs.edit().putString("saved_dropoff_address", coordString).apply()
                    onResult(lat, lng, coordString)
                    updateTripDetails(
                        pickupLat, pickupLng, lat, lng,
                        bookingStatus, activeBookingId, scope, sharedPrefs, onTripUpdate
                    )
                    onStatusMessage("📍 Pinned to custom coordinates")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val coordString = "Pinned: (%.4f, %.4f)".format(lat, lng)
                onResult(lat, lng, coordString)
            }
        }
    }
    onJobCreated(job)
}

suspend fun resolvePlaceGeometry(
    placeId: String,
    mapsApiKey: String
): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try {
        val detailsUrl = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=$placeId&fields=geometry&key=$mapsApiKey"
        val detailsConnection = URL(detailsUrl).openConnection() as HttpURLConnection
        detailsConnection.connectTimeout = 5000
        detailsConnection.readTimeout = 5000

        if (detailsConnection.responseCode == 200) {
            val detailsResponse = detailsConnection.inputStream.bufferedReader().use { it.readText() }
            val location = JSONObject(detailsResponse)
                .optJSONObject("result")
                ?.optJSONObject("geometry")
                ?.optJSONObject("location")

            if (location != null) {
                return@withContext location.getDouble("lat") to location.getDouble("lng")
            }
        }
        null
    } catch (e: Exception) {
        Log.e("CustomerApp", "Place details fetch failed for $placeId", e)
        null
    }
}

fun searchDestination(
    query: String,
    mapsApiKey: String,
    scope: CoroutineScope,
    currentJob: Job?,
    onJobCreated: (Job) -> Unit,
    onSuggestions: (List<PlaceSuggestion>) -> Unit
) {
    currentJob?.cancel()
    val cleanQuery = query.trim()
    if (cleanQuery.length < 2) { onSuggestions(emptyList()); return }

    val job = scope.launch(Dispatchers.IO) {
        delay(200)
        try {
            val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val urlString = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                    "?input=$encodedQuery&location=7.830,123.420&radius=7500&strictbounds=true&key=$mapsApiKey"
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val predictions = json.optJSONArray("predictions") ?: JSONArray()

                val loaded = (0 until predictions.length())
                    .map { predictions.getJSONObject(it) }
                    .mapNotNull { prediction ->
                        val placeId = prediction.optString("place_id", "")
                        val description = prediction.optString("description", "").trim()
                        if (placeId.isEmpty() || description.isEmpty()) null
                        else PlaceSuggestion(displayName = description, placeId = placeId)
                    }
                    .take(5)

                withContext(Dispatchers.Main) { onSuggestions(loaded) }
            }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Search failed", e)
        }
    }
    onJobCreated(job)
}