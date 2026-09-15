package com.cassey.customerapp.ui.home

import android.Manifest
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cassey.customerapp.data.SupabaseClient
import com.cassey.customerapp.network.NetworkQuality
import com.cassey.customerapp.network.NetworkTypeObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val isoTimestamp = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())
    }

    NetworkTypeObserver()

    val sharedPrefs = remember {
        context.getSharedPreferences("RideGoPrefs", android.content.Context.MODE_PRIVATE)
    }

    val state = remember { HomeState(sharedPrefs) }

    val pagadianBounds = remember {
        LatLngBounds(LatLng(7.800, 123.360), LatLng(7.860, 123.480))
    }

    val cameraPositionState = rememberCameraPositionState {
        position = if (state.dropoffLat != 0.0 && state.dropoffLng != 0.0) {
            CameraPosition.fromLatLngZoom(LatLng(state.dropoffLat, state.dropoffLng), 22f)
        } else {
            CameraPosition.fromLatLngZoom(PagadianDefaults.CAMERA_CENTER, PagadianDefaults.CAMERA_ZOOM)
        }
    }

    // Color
    val slate900   = Color(0xFF0F172A)
    val slate800   = Color(0xFF1E293B)
    val slate700   = Color(0xFF334155)
    val slate400   = Color(0xFF94A3B8)
    val slate300   = Color(0xFFCBD5E1)
    val slate600   = Color(0xFF718096)
    val emerald400 = Color(0xFF34D399)
    val emerald600 = Color(0xFF229954)
    val rose400    = Color(0xFFFB7185)
    val indigo600  = Color(0xFF4F46E5)

    fun doReverseGeocode(lat: Double, lng: Double) {
        reverseGeocodePickup(lat, lng, context, scope, sharedPrefs) { state.pickupAddress = it }
    }

    fun doFetchGPS() {
        fetchCurrentGPSLocation(
            activeBookingIdProvider = { state.activeBookingId },
            fusedLocationClient = fusedLocationClient,
            scope = scope,
            sharedPrefs = sharedPrefs,
            context = context,
            cameraPositionState = cameraPositionState,
            shouldAnimateCameraToPickup = { !state.hasDestinationStatus },
            onStatusMessage = { if (!state.hasDestinationStatus) state.statusMessage = it },
            onPickupLatLng = { lat, lng -> state.pickupLat = lat; state.pickupLng = lng },
            onReverseGeocode = ::doReverseGeocode,
            onGpsReadyChanged = { state.isPickupGpsReady = it },
            onFixTimestampUpdated = { state.lastFixTimestampMillis = it }
        )
    }

    fun doClearBooking() = state.clear(sharedPrefs)

    fun doUpdateTripDetails(pLat: Double, pLng: Double, dLat: Double, dLng: Double) {
        updateTripDetails(
            pLat, pLng, dLat, dLng,
            state.bookingStatus, state.activeBookingId, scope, sharedPrefs
        ) { distance, fare ->
            state.tripDistance = distance
            state.estimatedFare = fare
        }
    }

    fun doSmartSnap(lat: Double, lng: Double) {
        state.dropoffSnapJob?.cancel()
        smartSnapDropoff(
            lat = lat, lng = lng,
            mapsApiKey = com.cassey.customerapp.BuildConfig.MAPS_API_KEY,
            context = context,
            scope = scope,
            sharedPrefs = sharedPrefs,
            cameraPositionState = cameraPositionState,
            pickupLat = state.pickupLat,
            pickupLng = state.pickupLng,
            bookingStatus = state.bookingStatus,
            activeBookingId = state.activeBookingId,
            onJobCreated = { state.dropoffSnapJob = it },
            onResult = { snappedLat, snappedLng, displayName ->
                state.dropoffLat = snappedLat
                state.dropoffLng = snappedLng
                state.selectedDropoffAddress = displayName
                state.dropoffInput = displayName
            },
            onTripUpdate = { distance, fare ->
                state.tripDistance = distance
                state.estimatedFare = fare
            },
            onStatusMessage = {
                state.hasDestinationStatus = true
                state.statusMessage = it
            }
        )
    }

    fun doSelectSuggestion(suggestion: PlaceSuggestion) {
        state.dropoffInput = suggestion.displayName
        state.suggestions = emptyList()
        state.hasDestinationStatus = true
        state.statusMessage = "Locating ${suggestion.displayName}..."

        scope.launch {
            val resolved = resolvePlaceGeometry(suggestion.placeId, com.cassey.customerapp.BuildConfig.MAPS_API_KEY)
            if (resolved == null) {
                state.statusMessage = "Couldn't locate that destination. Please try again."
                return@launch
            }

            val (lat, lng) = resolved
            val inBounds = lat in 7.800..7.860 && lng in 123.360..123.480
            if (!inBounds) {
                state.statusMessage = "⚠️ That destination is outside our service area (Pagadian City)."
                state.dropoffInput = ""
                Toast.makeText(context, "That destination is outside Pagadian City.", Toast.LENGTH_LONG).show()
                return@launch
            }

            state.dropoffLat = lat
            state.dropoffLng = lng
            state.selectedDropoffAddress = suggestion.displayName
            sharedPrefs.edit()
                .putFloat("saved_dropoff_lat", lat.toFloat())
                .putFloat("saved_dropoff_lng", lng.toFloat())
                .putString("saved_dropoff_address", suggestion.displayName)
                .apply()
            doUpdateTripDetails(state.pickupLat, state.pickupLng, lat, lng)
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 22f)
                )
            } catch (e: Exception) {
                Log.e("CustomerApp", "Camera animate failed, ignoring", e)
            }
            state.statusMessage = "📍 Drop-off to: ${suggestion.displayName}"
        }
    }

    fun doSearchDestination(query: String) {
        searchDestination(
            query = query,
            mapsApiKey = com.cassey.customerapp.BuildConfig.MAPS_API_KEY,
            scope = scope,
            currentJob = state.searchJob,
            onJobCreated = { state.searchJob = it },
            onSuggestions = { state.suggestions = it }
        )
    }

    fun doConfirmBooking() {
        scope.launch {
            state.loading = true
            state.success = null
            state.statusMessage = "Checking target destination routing matrix..."

            val finalLat = state.dropoffLat
            val finalLng = state.dropoffLng
            val finalAddress = state.selectedDropoffAddress.ifEmpty { state.dropoffInput }
            val finalFareText = String.format("%.2f", state.estimatedFare)

            try {
                val currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("Session expired. Please log back in.")

                val finalBooking = BookingRequest(
                    customerId = currentUserId,
                    pickupAddress = state.pickupAddress,
                    dropoffAddress = finalAddress,
                    pickupLatitude = state.pickupLat,
                    pickupLongitude = state.pickupLng,
                    dropoffLatitude = finalLat,
                    dropoffLongitude = finalLng,
                    fare = finalFareText,
                    status = "pending"
                )

                val insertedBooking = NetworkQuality.timed {
                    withTimeout(8000) {
                        SupabaseClient.client.from("bookings")
                            .insert(finalBooking) { select() }
                            .decodeSingleOrNull<BookingStatusUpdate>()
                    }
                }

                if (insertedBooking?.id != null) {
                    sharedPrefs.edit().putString("active_booking_id", insertedBooking.id).apply()
                    state.activeBookingId = insertedBooking.id
                    state.bookingStatus = "pending"
                    state.hasVibratedForAcceptance = false
                    state.success = true
                    state.statusMessage = "Booking requested! Waiting for driver acceptance..."
                } else {
                    throw Exception("Database failed to return valid confirmation signature reference.")
                }
            } catch (e: TimeoutCancellationException) {
                state.success = false
                state.statusMessage = "Your connection is very slow — the request may still be processing. Please wait a moment before retrying to avoid a duplicate booking."
                Log.e("RideGoSubmit", "Booking insert timed out", e)
            } catch (e: Exception) {
                state.success = false
                state.statusMessage = when (e) {
                    is java.net.UnknownHostException,
                    is java.io.IOException -> "No internet connection. Please check your connection and try again."
                    else -> "Something went wrong while submitting your booking. Please try again."
                }
                Log.e("RideGoSubmit", "Supabase error core", e)
            } finally {
                state.loading = false
            }
        }
    }

    // Permission launcher

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) doFetchGPS()
        else {
            state.statusMessage = "Location access denied. Using center city defaults."
            state.pickupAddress = "Pagadian City Center (Manual Access)"
        }
    }

    // Effects
    HomeEffects(
        state = state,
        sharedPrefs = sharedPrefs,
        context = context,
        scope = scope,
        fusedLocationClient = fusedLocationClient,
        cameraPositionState = cameraPositionState,
        onRequestLocationPermission = {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        },
        onFetchGPS = ::doFetchGPS,
        onReverseGeocode = ::doReverseGeocode,
        onTripUpdate = { distance, fare ->
            state.tripDistance = distance
            state.estimatedFare = fare
        },
        onClearBooking = ::doClearBooking
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Ride&Go",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (state.termsRecord != null) {
                                    state.isTermsReviewMode = true
                                    state.showTermsDialog = true
                                } else {
                                    Toast.makeText(context, "Terms of Service unavailable right now.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "View Terms of Service",
                                tint = slate400
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try { SupabaseClient.client.auth.signOut() }
                                    catch (e: Exception) { Log.e("RideGoAuth", "Logout failed", e) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Log Out",
                                tint = slate400
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = slate900,
                        titleContentColor = Color.White
                    )
                )
                HorizontalDivider(color = slate800, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            HomeMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                pagadianBounds = pagadianBounds,
                pickupLat = state.pickupLat,
                pickupLng = state.pickupLng,
                pickupAddress = state.pickupAddress,
                dropoffLat = state.dropoffLat,
                dropoffLng = state.dropoffLng,
                selectedDropoffAddress = state.selectedDropoffAddress,
                riderLatLng = state.riderLatLng,
                driverName = state.driverName,
                nearbyRiders = state.nearbyRiders,
                activeBookingId = state.activeBookingId,
                onMapLongClick = { point ->
                    if (pagadianBounds.contains(point)) {
                        if (state.activeBookingId != null) return@HomeMap
                        state.dropoffLat = point.latitude
                        state.dropoffLng = point.longitude
                        state.hasDestinationStatus = true
                        state.statusMessage = "🔍 Finding nearest place..."
                        doSmartSnap(point.latitude, point.longitude)
                    } else {
                        state.statusMessage = "⚠️ Outside operational zone. Pin inside Pagadian City."
                    }
                }
            )

            if (state.isDashboardVisible) {
                HomeDashboard(
                    state = state,
                    sharedPrefs = sharedPrefs,
                    scope = scope,
                    cameraPositionState = cameraPositionState,
                    isoTimestamp = isoTimestamp,
                    emerald400 = emerald400,
                    rose400 = rose400,
                    indigo600 = indigo600,
                    slate300 = slate300,
                    slate400 = slate400,
                    slate600 = slate600,
                    slate700 = slate700,
                    slate800 = slate800,
                    slate900 = slate900,
                    emerald600 = emerald600,
                    onClearBooking = ::doClearBooking,
                    onFetchGPS = ::doFetchGPS,
                    onSearchDestination = ::doSearchDestination,
                    onSelectSuggestion = ::doSelectSuggestion,
                    onUpdateTripDetails = ::doUpdateTripDetails,
                    onHideDashboard = { state.isDashboardVisible = false },
                    onConfirmBooking = ::doConfirmBooking
                )
            } else {
                Button(
                    onClick = { state.isDashboardVisible = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF283593))
                ) {
                    Text(text = "Show Booking Panel", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    // Terms of Service
    state.termsRecord?.let { terms ->
        if (state.showTermsDialog) {
            TermsOfServiceDialog(
                terms = terms,
                isReadOnlyReview = state.isTermsReviewMode,
                onAccept = { state.showTermsDialog = false },
                onDismissReview = { state.showTermsDialog = false }
            )
        }
    }

    if (state.showGmsMissingDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Google Play Services Required") },
            text = { Text("This device doesn't have Google Play Services, which Cassey needs for GPS location. The app won't be able to detect nearby passengers on this device.") },
            confirmButton = {
                TextButton(onClick = { state.showGmsMissingDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}