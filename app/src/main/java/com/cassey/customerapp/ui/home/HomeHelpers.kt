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
    activeBookingId: String?,
    fusedLocationClient: FusedLocationProviderClient,
    scope: CoroutineScope,
    sharedPrefs: android.content.SharedPreferences,
    context: Context,
    cameraPositionState: CameraPositionState,
    onStatusMessage: (String) -> Unit,
    onPickupLatLng: (Double, Double) -> Unit,
    onReverseGeocode: (Double, Double) -> Unit,
    onGpsReadyChanged: (Boolean) -> Unit
) {
    if (activeBookingId != null) return

    if (currentGpsJob?.isActive == true) return

    onStatusMessage("Fetching precise GPS coordinates... (Tap this to try again)")

    scope.launch {
        var attempt = 0
        val maxAttempts = 6
        var delayMs = 2000L

        while (attempt < maxAttempts) {
            attempt++
            try {
                val location = withTimeout(10000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val task = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        task.addOnSuccessListener { cont.resume(it) {} }
                        task.addOnFailureListener { cont.resume(null) {} }
                    }
                }

                if (location != null && location.accuracy <= 50f) {
                    onPickupLatLng(location.latitude, location.longitude)
                    sharedPrefs.edit()
                        .putFloat("saved_pickup_lat", location.latitude.toFloat())
                        .putFloat("saved_pickup_lng", location.longitude.toFloat())
                        .apply()
                    onReverseGeocode(location.latitude, location.longitude)
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f)
                    )
                    onStatusMessage("GPS lock established. Long-press map to pin destination!")
                    onGpsReadyChanged(true)
                    return@launch
                } else if (location != null) {
                    Log.d("CustomerApp", "Rejected low-accuracy fix: ${location.accuracy}m")
                }
            } catch (e: TimeoutCancellationException) {

            }

            if (attempt < maxAttempts) {
                onStatusMessage("Locating you... (Tap this to try again)")
                delay(delayMs)
                delayMs = (delayMs * 1.8).toLong().coerceAtMost(30000L)
            } else {
                onStatusMessage("Couldn't get a precise location. Tap to try again.")
                onGpsReadyChanged(false)
            }
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
    onResult: (snappedLat: Double, snappedLng: Double, displayName: String) -> Unit,
    onTripUpdate: (distance: Double, fare: Double) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
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
                        onStatusMessage("📍 Snapped to: $displayName")
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
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
                    }
                } else {
                    val coordString = "Pinned: (%.4f, %.4f)".format(lat, lng)
                    sharedPrefs.edit().putString("saved_dropoff_address", coordString).apply()
                    onResult(lat, lng, coordString)
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
                    "?input=$encodedQuery&location=7.8275,123.420&radius=9000&strictbounds=true&key=$mapsApiKey"
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
                                val plLat = location.getDouble("lat")
                                val plLng = location.getDouble("lng")
                                if (plLat in 7.800..7.860 && plLng in 123.360..123.480) {
                                    loaded.add(PlaceSuggestion(description, plLat, plLng))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CustomerApp", "Place details fetch failed for $placeId", e)
                        continue
                    }
                }

                withContext(Dispatchers.Main) { onSuggestions(loaded.take(5)) }
            }
        } catch (e: Exception) {
            Log.e("CustomerApp", "Search failed", e)
        }
    }
    onJobCreated(job)
}