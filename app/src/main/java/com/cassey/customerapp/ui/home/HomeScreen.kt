package com.cassey.customerapp.ui.home

import android.Manifest
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cassey.customerapp.BuildConfig
import com.cassey.customerapp.data.SupabaseClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import io.github.jan.supabase.postgrest.postgrest
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    @SerialName("customer_id") val customer_id: String,
    @SerialName("status") val status: String,
    @SerialName("rider_id") val rider_id: String? = null
)

@kotlinx.serialization.Serializable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Initialize MapLibre Engine Instance
    remember { MapLibre.getInstance(context) }

    // App State Monitoring
    var loading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Locating user session & locking GPS...") }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var isDashboardVisible by remember { mutableStateOf(true) }
    val sharedPrefs = remember { context.getSharedPreferences("RideGoPrefs", android.content.Context.MODE_PRIVATE) }
    var driverName by remember { mutableStateOf("") }
    var driverPhone by remember { mutableStateOf("") }
    val savedBookingId = sharedPrefs.getString("active_booking_id", null)
    var activeBookingId by remember {
        mutableStateOf(sharedPrefs.getString("active_booking_id", null))
    }
    var bookingStatus by remember { mutableStateOf(if (savedBookingId != null) "pending" else null) }
    var currentRiderId by remember { mutableStateOf<String?>(null) }
    var riderVehicleMarker by remember { mutableStateOf<Marker?>(null) }
    var dropoffLat by remember {
        mutableStateOf(sharedPrefs.getFloat("saved_dropoff_lat", 0.0f).toDouble())
    }
    var dropoffLng by remember {
        mutableStateOf(sharedPrefs.getFloat("saved_dropoff_lng", 0.0f).toDouble())
    }
    var selectedDropoffAddress by remember {
        mutableStateOf(sharedPrefs.getString("saved_dropoff_address", "") ?: "")
    }
    var dropoffInput by remember {
        mutableStateOf(sharedPrefs.getString("saved_dropoff_address", "") ?: "")
    }
    var tripDistance by remember {
        mutableStateOf(sharedPrefs.getFloat("saved_trip_distance", 0.0f).toDouble())
    }
    var estimatedFare by remember {
        mutableStateOf(sharedPrefs.getFloat("saved_estimated_fare", 0.0f).toDouble())
    }
    var pickupAddress by remember {
        mutableStateOf(sharedPrefs.getString("saved_pickup_address", "Locating your position...") ?: "Locating your position...")
    }
    var pickupLat by remember {
        mutableStateOf(sharedPrefs.getFloat("saved_pickup_lat", 7.8285f).toDouble())
    }
    var pickupLng by remember {
        mutableStateOf(sharedPrefs.getFloat("saved_pickup_lng", 123.4332f).toDouble())
    }

    // MapLibre Maps variables
    val maptilerApiKey = BuildConfig.MAPTILER_API_KEY
    var nativeMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var pickupMarker by remember { mutableStateOf<Marker?>(null) }
    var dropoffMarker by remember { mutableStateOf<Marker?>(null) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var pagadianFeaturesCache: List<PlaceSuggestion> = emptyList()

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
                        val addressObj = addresses[0]
                        val fallbackName = addressObj.thoroughfare ?: addressObj.subLocality ?: addressObj.locality ?: "Current Location"
                        pickupAddress = "$fallbackName, ${addressObj.locality ?: ""}".trimEnd(',', ' ')
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
                    statusMessage = "GPS lock established. Drop pin or click book!"
                } else {
                    statusMessage = "Failed to lock GPS automatically. Using Pagadian City fallback center."
                }
            }
            .addOnFailureListener {
                statusMessage = "GPS error: ${it.localizedMessage}. Using default fallback point."
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
        if (riderId.isNullOrEmpty()) {
            driverName = ""
            driverPhone = ""
            return
        }
        try {
            val profile = SupabaseClient.client.from("profiles")
                .select { filter { eq("id", riderId) } }
                .decodeSingleOrNull<RiderProfile>()

            if (profile != null) {
                driverName = profile.name
                driverPhone = profile.phoneNumber
            }
        } catch (e: Exception) {
            Log.e("SupabaseRealtime", "Failed to look up rider profile details", e)
        }
    }

    // Get fare from the fare table in database
    suspend fun fetchLiveFareSettings(): FareSettings? {
        return withContext(Dispatchers.IO) {
            try {
                // ✅ Modern syntax: use .from() instead of .postgrest[]
                SupabaseClient.client.postgrest.from("fare")
                    .select {
                        filter {
                            eq("id", 1)
                        }
                    }.decodeSingle<FareSettings>()
            } catch (e: Exception) {
                Log.e("SupabaseFare", "Failed to fetch remote fare configurations", e)
                null
            }
        }
    }

    // Calculate distance between two coordinates
    fun calculateDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val originLatRad = Math.toRadians(lat1)
        val destinationLatRad = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(originLatRad) * cos(destinationLatRad)

        val c = 2 * asin(sqrt(a))

        return earthRadiusKm * c
    }

    // Get the coordinates of the pickup and dropoff points
    fun updateTripDetails(pickupLat: Double, pickupLng: Double, dropoffLat: Double, dropoffLng: Double) {
        // If there's an active booking, preserve the stored values!
        if (bookingStatus != null || activeBookingId != null) return

        if (pickupLat != 0.0 && pickupLng != 0.0 && dropoffLat != 0.0 && dropoffLng != 0.0) {
            val distance = calculateDistanceInKm(pickupLat, pickupLng, dropoffLat, dropoffLng)
            tripDistance = distance

            sharedPrefs.edit().putFloat("saved_trip_distance", distance.toFloat()).apply()

            scope.launch {
                val liveSettings = fetchLiveFareSettings()
                val base = liveSettings?.base_fare ?: 15.0
                val succeeding = liveSettings?.succeeding_km_rate ?: 2.0

                val computedFare = if (distance <= 1.0) {
                    base
                } else {
                    val succeedingDistance = distance - 1.0
                    val chargeableSucceedingKm = ceil(succeedingDistance)
                    base + (chargeableSucceedingKm * succeeding)
                }

                withContext(Dispatchers.Main) {
                    estimatedFare = computedFare
                    sharedPrefs.edit().putFloat("saved_estimated_fare", computedFare.toFloat()).apply()
                }
            }
        }
    }

    // Reverse Geocode helper to convert MapLibre pins to text address strings
    fun reverseGeocodeDropoff(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                withContext(Dispatchers.Main) {
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val feature = addr.thoroughfare ?: addr.subLocality ?: "Pinned Location"
                        selectedDropoffAddress = "$feature, ${addr.locality ?: "Pagadian City"}"
                        dropoffInput = selectedDropoffAddress
                        statusMessage = "📍 Pinned to: $selectedDropoffAddress"
                    } else {
                        // 🟢 FIX: Set both to the exact same readable coordinates so the text field changes immediately
                        val coordString = "Pinned Point: (%.4f, %.4f)".format(lat, lng)
                        selectedDropoffAddress = coordString
                        dropoffInput = coordString
                        statusMessage = "📍 Pinned to custom map coordinates"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // 🟢 FIX: Handle geocoder network/system failures gracefully by updating the fields anyway
                    val coordString = "Pinned Point: (%.4f, %.4f)".format(lat, lng)
                    selectedDropoffAddress = coordString
                    dropoffInput = coordString
                    statusMessage = "📍 Pinned to custom map coordinates"
                }
            }
        }
    }

    // Suggestions then auto mark in the map
    fun searchDestination(query: String) {
        searchJob?.cancel()
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) {
            suggestions = emptyList()
            return
        }

        searchJob = scope.launch(Dispatchers.IO) {
            delay(200)

            try {
                if (pagadianFeaturesCache.isEmpty()) {
                    val urlString = "https://api.maptiler.com/data/019e90d8-5ba2-7d50-b21d-21d1040111aa/features.json?key=$maptilerApiKey"

                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("Accept", "application/json")

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonObject = org.json.JSONObject(response)
                        val featuresArray = jsonObject.optJSONArray("features") ?: org.json.JSONArray()
                        val loadedFeatures = mutableListOf<PlaceSuggestion>()

                        for (i in 0 until featuresArray.length()) {
                            val feature = featuresArray.getJSONObject(i)
                            val properties = feature.optJSONObject("properties") ?: org.json.JSONObject()
                            val geometry = feature.optJSONObject("geometry") ?: org.json.JSONObject()

                            // Extract display name cleanly without generating placeholder "Unknown POI" titles
                            val name = properties.optString("name", "").trim()
                            if (name.isEmpty()) {
                                continue // Skip processing if there isn't a searchable name text attribute matching
                            }

                            val geometryType = geometry.optString("type", "Point")
                            val coordinatesArray = geometry.optJSONArray("coordinates")

                            if (coordinatesArray != null && coordinatesArray.length() > 0) {
                                var lon = 0.0
                                var lat = 0.0
                                var parseSuccess = false

                                try {
                                    if (geometryType == "Point") {
                                        lon = coordinatesArray.getDouble(0)
                                        lat = coordinatesArray.getDouble(1)
                                        parseSuccess = true
                                    } else if (geometryType == "Polygon" && coordinatesArray.length() > 0) {
                                        val exteriorRing = coordinatesArray.optJSONArray(0)
                                        if (exteriorRing != null && exteriorRing.length() > 0) {
                                            val firstPoint = exteriorRing.optJSONArray(0)
                                            if (firstPoint != null && firstPoint.length() >= 2) {
                                                lon = firstPoint.getDouble(0)
                                                lat = firstPoint.getDouble(1)
                                                parseSuccess = true
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MapTilerDebug", "Skipping corrupt coordinate layout format structures", e)
                                }

                                if (parseSuccess) {
                                    loadedFeatures.add(PlaceSuggestion(displayName = name, latitude = lat, longitude = lon))
                                }
                            }
                        }
                        pagadianFeaturesCache = loadedFeatures
                    } else {
                        Log.e("MapTilerDebug", "Server error code response payload tracking check: ${connection.responseCode}")
                    }
                }

                val filteredSuggestions = pagadianFeaturesCache.filter { poi ->
                    poi.displayName.contains(cleanQuery, ignoreCase = true)
                }.take(5)

                withContext(Dispatchers.Main) {
                    suggestions = filteredSuggestions
                }

            } catch (e: Exception) {
                Log.e("MapTilerDebug", "Search exception", e)
            }
        }
    }

    fun animateMarkerLinear(marker: Marker?, targetPosition: LatLng, durationMs: Long = 1500) {
        if (marker == null) return
        val startPosition = marker.position

        val latLngEvaluator = TypeEvaluator<LatLng> { fraction, startValue, endValue ->
            LatLng(
                startValue.latitude + (endValue.latitude - startValue.latitude) * fraction,
                startValue.longitude + (endValue.longitude - startValue.longitude) * fraction
            )
        }

        ValueAnimator.ofObject(latLngEvaluator, startPosition, targetPosition).apply {
            duration = durationMs
            addUpdateListener { animator ->
                marker.position = animator.animatedValue as LatLng
            }
            start()
        }
    }

    // Instantiating the MapLibre lifecycle-aware component wrapper container
    val mapView = remember {
        MapView(context).apply {
            getMapAsync { mapLibreMap ->

                val mapStyleUrl = "https://api.maptiler.com/maps/streets-v2/style.json?key=$maptilerApiKey"

                mapLibreMap.setStyle(mapStyleUrl) { style ->
                    try {
                        val customDataUrl = "https://api.maptiler.com/data/019e90d8-5ba2-7d50-b21d-21d1040111aa/features.json?key=$maptilerApiKey"
                        val geoJsonSource = org.maplibre.android.style.sources.GeoJsonSource("pagadian-pois-source", java.net.URL(customDataUrl))
                        style.addSource(geoJsonSource)

                        val symbolLayer = org.maplibre.android.style.layers.SymbolLayer("pagadian-pois-layer", "pagadian-pois-source")
                        symbolLayer.setProperties(
                            org.maplibre.android.style.layers.PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("name")),
                            org.maplibre.android.style.layers.PropertyFactory.textSize(12f),
                            org.maplibre.android.style.layers.PropertyFactory.textColor(android.graphics.Color.BLACK),
                            org.maplibre.android.style.layers.PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                            org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(1.5f),
                            org.maplibre.android.style.layers.PropertyFactory.textVariableAnchor(
                                arrayOf(org.maplibre.android.style.layers.Property.TEXT_ANCHOR_TOP, org.maplibre.android.style.layers.Property.TEXT_ANCHOR_BOTTOM)
                            )
                        )

                        style.addLayer(symbolLayer)
                        Log.d("MapTilerDebug", "Custom POI dataset layer injected successfully.")
                    } catch (e: Exception) {
                        Log.e("MapTilerDebug", "Failed to append custom vector layer", e)
                    }
                }

                val pagadianBounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                    .include(LatLng(7.92, 123.36))
                    .include(LatLng(7.78, 123.51))
                    .build()

                mapLibreMap.setLatLngBoundsForCameraTarget(pagadianBounds)
                mapLibreMap.setMinZoomPreference(11.0)
                mapLibreMap.setMaxZoomPreference(18.0)

                mapLibreMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(7.8285, 123.4332))
                    .zoom(13.0)
                    .build()

                val effectivePickupLat = if (pickupLat != 0.0) pickupLat else sharedPrefs.getFloat("saved_pickup_lat", 0.0f).toDouble()
                val effectivePickupLng = if (pickupLng != 0.0) pickupLng else sharedPrefs.getFloat("saved_pickup_lng", 0.0f).toDouble()

                // 1. Restore Pickup Pin if it exists in SharedPreferences
                if (effectivePickupLat != 0.0 && effectivePickupLng != 0.0) {
                    val savedPickupLatLng = LatLng(effectivePickupLat, effectivePickupLng)

                    pickupMarker?.let { mapLibreMap.removeMarker(it) }
                    pickupMarker = mapLibreMap.addMarker(
                        MarkerOptions()
                            .position(savedPickupLatLng)
                            .title("Your Pickup Location")
                    )
                }

                if (dropoffLat != 0.0 && dropoffLng != 0.0) {
                    val savedTargetLatLng = LatLng(dropoffLat, dropoffLng)

                    dropoffMarker = mapLibreMap.addMarker(
                        MarkerOptions()
                            .position(savedTargetLatLng)
                            .title(selectedDropoffAddress.ifEmpty { "Target Destination" })
                    )

                    // Smoothly focus on your active destination point layout
                    mapLibreMap.moveCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(savedTargetLatLng, 15.0)
                    )
                }

                // 🎯 SMART TAP INTERCEPTION
                mapLibreMap.addOnMapLongClickListener { point ->
                    if (pagadianBounds.contains(point)) {
                        dropoffLat = point.latitude
                        dropoffLng = point.longitude

                        sharedPrefs.edit()
                            .putFloat("saved_dropoff_lat", point.latitude.toFloat())
                            .putFloat("saved_dropoff_lng", point.longitude.toFloat())
                            .apply()

                        // 🟢 TRIGGER CALCULATOR HERE: Pass your current pickup coordinates and the new drop-off point
                        updateTripDetails(pickupLat, pickupLng, point.latitude, point.longitude)

                        // Clear old dropoff pin marker
                        dropoffMarker?.let { mapLibreMap.removeMarker(it) }

                        // Convert the geographic LatLng point into sharp physical screen pixels
                        val screenPoint = mapLibreMap.projection.toScreenLocation(point)

                        // Query features in a tiny box around the finger placement (10x10 pixels padding)
                        val rect = android.graphics.RectF(
                            screenPoint.x - 5f, screenPoint.y - 5f,
                            screenPoint.x + 5f, screenPoint.y + 5f
                        )

                        // Pull vector metadata strictly matching our custom text label layer ID
                        val features = mapLibreMap.queryRenderedFeatures(rect, "pagadian-pois-layer")

                        if (features.isNotEmpty()) {
                            // 🟢 Success: User pinned a vector point directly belonging to your dataset!
                            val properties = features[0].properties()
                            val tappedPoiName = properties?.get("name")?.asString ?: ""

                            if (tappedPoiName.isNotEmpty()) {
                                selectedDropoffAddress = tappedPoiName
                                dropoffInput = tappedPoiName
                                statusMessage = "📍 Pinned to: $tappedPoiName"

                                // Drop destination marker pin directly over the exact POI coordinates
                                dropoffMarker = mapLibreMap.addMarker(
                                    MarkerOptions().position(point).title(tappedPoiName)
                                )

                                selectedDropoffAddress = tappedPoiName
                                dropoffInput = tappedPoiName

                                sharedPrefs.edit().putString("saved_dropoff_address", selectedDropoffAddress).apply()
                            } else {
                                // Fallback if the map element somehow lacked a valid name attribute value string
                                dropoffMarker = mapLibreMap.addMarker(MarkerOptions().position(point).title("Target Destination"))
                                reverseGeocodeDropoff(point.latitude, point.longitude)
                            }
                        } else {
                            // 🔵 No overlapping features detected: Fall back safely to standard raw GPS reverse geocoding
                            dropoffMarker = mapLibreMap.addMarker(MarkerOptions().position(point).title("Target Destination"))
                            reverseGeocodeDropoff(point.latitude, point.longitude)
                        }
                    } else {
                        statusMessage = "⚠️ Outside operational zone. Please pin inside Pagadian City."
                    }
                    true
                }
                nativeMapInstance = mapLibreMap
            }
        }
    }

    // Trigger Permissions on Screen Load automatically
    LaunchedEffect(Unit) {
        if (activeBookingId != null) {
            val cachedAddress = sharedPrefs.getString("saved_pickup_address", "")
            if (!cachedAddress.isNullOrEmpty()) {
                pickupAddress = cachedAddress
            } else {
                reverseGeocodePickup(pickupLat, pickupLng)
            }

            pickupLat = sharedPrefs.getFloat("saved_pickup_lat", 0.0f).toDouble()
            pickupLng = sharedPrefs.getFloat("saved_pickup_lng", 0.0f).toDouble()

            dropoffLat = sharedPrefs.getFloat("saved_dropoff_lat", 0.0f).toDouble()
            dropoffLng = sharedPrefs.getFloat("saved_dropoff_lng", 0.0f).toDouble()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Updated every 5sec for the status in bookings (it stay even when the app closed)
    LaunchedEffect(activeBookingId) {
        val currentId = activeBookingId ?: return@LaunchedEffect

        while (isActive) {
            try {
                val updatedBooking = SupabaseClient.client.from("bookings")
                    .select { filter { eq("customer_id", currentId) } }
                    .decodeSingleOrNull<BookingStatusUpdate>()


                if (updatedBooking != null) {
                    bookingStatus = updatedBooking.status
                    if (updatedBooking.status == "accepted") {
                        currentRiderId = updatedBooking.rider_id
                        fetchDriverDetails(updatedBooking.rider_id)
                    }

                    // 🏁 BRANCH A: Driver marked it completed/cancelled from their end
                    if (updatedBooking.status in listOf("completed", "cancelled")) {

                        withContext(Dispatchers.Main) {
                            nativeMapInstance?.let { map ->
                                riderVehicleMarker?.let { map.removeMarker(it) }
                                dropoffMarker?.let { map.removeMarker(it) }
                                pickupMarker?.let { map.removeMarker(it) }
                            }
                            // Explicitly clear references
                            riderVehicleMarker = null
                            dropoffMarker = null
                            pickupMarker = null

                            Toast.makeText(context, "Your ride is complete!", Toast.LENGTH_LONG).show()
                        }

                        sharedPrefs.edit()
                            .remove("active_booking_id")
                            .remove("saved_dropoff_lat")
                            .remove("saved_dropoff_lng")
                            .remove("saved_dropoff_address")
                            .remove("saved_pickup_address")
                            .remove("saved_trip_distance")
                            .remove("saved_estimated_fare")
                            .apply()

                        currentRiderId = null
                        activeBookingId = null
                        bookingStatus = null

                        fetchCurrentGPSLocation()
                        break
                    }
                } else {

                    withContext(Dispatchers.Main) {
                        nativeMapInstance?.let { map ->
                            riderVehicleMarker?.let { map.removeMarker(it) }
                            dropoffMarker?.let { map.removeMarker(it) }
                            pickupMarker?.let { map.removeMarker(it) }
                        }
                        riderVehicleMarker = null
                        dropoffMarker = null
                        pickupMarker = null
                    }

                    sharedPrefs.edit()
                        .remove("active_booking_id")
                        .remove("saved_dropoff_lat")
                        .remove("saved_dropoff_lng")
                        .remove("saved_dropoff_address")
                        .remove("saved_pickup_address")
                        .remove("saved_trip_distance")
                        .remove("saved_estimated_fare")
                        .apply()
                    currentRiderId = null
                    activeBookingId = null
                    bookingStatus = null

                    fetchCurrentGPSLocation()
                    break
                }
            } catch (e: Exception) {
                Log.e("SupabasePolling", "Polling failed", e)
            }
            delay(5000)
        }
    }

    // Handles Android View memory lifecycle mapping
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Side-Effect observer tracking changes to auto-adjust pickup tracking variables
    LaunchedEffect(pickupLat, pickupLng, nativeMapInstance) {
        val currentLatLng = LatLng(pickupLat, pickupLng)
        nativeMapInstance?.let { map ->
            val existingMarker = pickupMarker
            if (existingMarker == null) {
                pickupMarker = map.addMarker(
                    MarkerOptions()
                        .position(currentLatLng)
                        .title("Your Current Location")
                )
            } else {
                existingMarker.position = currentLatLng
            }

            if (activeBookingId == null) {
                map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(currentLatLng))
            }
        }
    }

    // Rider location and marker
    LaunchedEffect(currentRiderId) {
        val targetRiderId = currentRiderId ?: return@LaunchedEffect

        while (isActive) {
            try {
                // Fetch the rider's latest location telemetry metrics
                val locationData = withContext(Dispatchers.IO) {
                    SupabaseClient.client.from("rider_locations")
                        .select { filter { eq("rider_id", targetRiderId) } }
                        .decodeSingleOrNull<RiderLocationUpdate>()
                }

                if (locationData != null) {
                    val updatedPosition = LatLng(locationData.lat, locationData.lng)

                    // Switch back to Main Thread to modify UI map assets safely
                    withContext(Dispatchers.Main) {
                        nativeMapInstance?.let { map ->
                            if (riderVehicleMarker == null) {
                                // Generate the vehicle marker node on its initial response packet
                                riderVehicleMarker = map.addMarker(
                                    MarkerOptions()
                                        .position(updatedPosition)
                                        .title("$driverName is on the way")
                                )
                            } else {
                                // Smoothly slide the vehicle marker across intermediate GPS intervals
                                animateMarkerLinear(
                                    marker = riderVehicleMarker,
                                    targetPosition = updatedPosition,
                                    durationMs = 2500 // Keeps the slide fluid across the 5s interval window
                                )

                                // Update marker snippet info live
                                riderVehicleMarker?.snippet = "Heading: ${locationData.heading.toInt()}°"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabasePolling", "Telemetry lookup missed this tick", e)
            }

            // Wait exactly 5 seconds before checking again
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Ride&Go", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
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

            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // OVERLAY INTERFACE DASHBOARD OVER MAP STACK
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
                                            statusMessage = "Hold/Long-press on the map to pin your destination!"
                                            nativeMapInstance?.let { map ->
                                                dropoffMarker?.let { map.removeMarker(it) }
                                                dropoffMarker = null
                                            }
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

                                // Suggestion Dropdown List Overlay
                                if (suggestions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = slate900),
                                        border = BorderStroke(1.dp, slate700)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            suggestions.forEach { suggestion ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = suggestion.displayName,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    onClick = {
                                                        dropoffLat = suggestion.latitude
                                                        dropoffLng = suggestion.longitude
                                                        selectedDropoffAddress = suggestion.displayName
                                                        dropoffInput = suggestion.displayName
                                                        suggestions = emptyList()
                                                        statusMessage = "Destination set from search suggestion!"

                                                        updateTripDetails(pickupLat, pickupLng, suggestion.latitude, suggestion.longitude)

                                                        // Dynamically insert marker into the MapLibre engine instance
                                                        nativeMapInstance?.let { map ->
                                                            dropoffMarker?.let { map.removeMarker(it) }

                                                            val targetLatLng = LatLng(suggestion.latitude, suggestion.longitude)
                                                            dropoffMarker = map.addMarker(
                                                                MarkerOptions()
                                                                    .position(targetLatLng)
                                                                    .title("Target Destination")
                                                                    .snippet(suggestion.displayName)
                                                            )

                                                            map.animateCamera(
                                                                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(targetLatLng, 15.0)
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        Spacer(modifier = Modifier.height(8.dp))

                        // Show Active Tracking Card if Booking exists, else Show standard Form Actions
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
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = emerald400, strokeWidth = 3.dp)
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
                                                            val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id
                                                            if (currentUserId != null) {
                                                                SupabaseClient.client.from("bookings").delete {
                                                                    filter { eq("customer_id", currentUserId) }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("RideGoCancel", "Failed to delete booking row", e)
                                                        } finally {
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

                                                            nativeMapInstance?.let { map ->
                                                                riderVehicleMarker?.let { map.removeMarker(it); riderVehicleMarker = null }
                                                                dropoffMarker?.let { map.removeMarker(it); dropoffMarker = null }
                                                                pickupMarker?.let { map.removeMarker(it); pickupMarker = null }
                                                            }

                                                            Toast.makeText(
                                                                context,
                                                                "Booking has been Cancelled.",
                                                                Toast.LENGTH_LONG
                                                            ).show()

                                                            // Reset states
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
                                                            statusMessage = "Hold/Long-press on the map to pin your destination!"

                                                            fetchCurrentGPSLocation()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Text(text = "Cancel Booking Request", color = rose400, style = MaterialTheme.typography.labelMedium)
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

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(text = "Rider Assigned:", color = slate400, style = MaterialTheme.typography.bodyMedium)
                                                Text(text = driverName.ifEmpty { "Rider Found" }, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(text = "Contact Number:", color = slate400, style = MaterialTheme.typography.bodyMedium)
                                                Text(text = driverPhone.ifEmpty { "Updating..." }, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = indigo600.copy(alpha = 0.2f)),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "📞 Note: The rider will call you to confirm your exact picking location coordinates.",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            // Payment Options
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
                                                // Cash Button
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                SupabaseClient.client.from("bookings").delete {
                                                                    filter { eq("customer_id", activeBookingId!!) }
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("RideGo", "Failed to clear booking", e)
                                                            } finally {
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

                                                                nativeMapInstance?.let { map ->
                                                                    riderVehicleMarker?.let { map.removeMarker(it); riderVehicleMarker = null }
                                                                    dropoffMarker?.let { map.removeMarker(it); dropoffMarker = null }
                                                                    pickupMarker?.let { map.removeMarker(it); pickupMarker = null }
                                                                }

                                                                Toast.makeText(
                                                                    context,
                                                                    "Ride completed! Collected ₱${String.format("%.2f", estimatedFare)} via Cash.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()

                                                                // Reset states
                                                                estimatedFare = 0.0
                                                                tripDistance = 0.0
                                                                dropoffLat = 0.0
                                                                dropoffLng = 0.0
                                                                pickupLat = 0.0
                                                                pickupLng = 0.0
                                                                selectedDropoffAddress = ""
                                                                dropoffInput = ""
                                                                suggestions = emptyList()

                                                                fetchCurrentGPSLocation()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = emerald600)
                                                ) {
                                                    Text("Cash")
                                                }

                                                // GCash Button
                                                Button(
                                                    onClick = {
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    enabled = true,
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0052FF))
                                                ) {
                                                    Text("GCash")
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Ride in progress - Cancellation not available.",
                                                color = slate600,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

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

                                            SupabaseClient.client.from("bookings").insert(finalBooking)

                                            sharedPrefs.edit().putString("active_booking_id", currentUserId).apply()
                                            activeBookingId = currentUserId
                                            bookingStatus = "pending"

                                            success = true
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
            }


             else {
                // Bottom anchor button shown when the dashboard is hidden
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
}