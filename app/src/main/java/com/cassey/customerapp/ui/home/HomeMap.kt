package com.cassey.customerapp.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*

@Composable
fun HomeMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState,
    pagadianBounds: LatLngBounds,
    pickupLat: Double,
    pickupLng: Double,
    pickupAddress: String,
    dropoffLat: Double,
    dropoffLng: Double,
    selectedDropoffAddress: String,
    riderLatLng: LatLng?,
    driverName: String,
    nearbyRiders: List<NearbyRider>,
    activeBookingId: String?,
    onMapLongClick: (LatLng) -> Unit
) {
    GoogleMap(
        modifier = modifier,
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
        onMapLongClick = onMapLongClick
    ) {
        // Pickup marker
        if (pickupLat != 0.0 && pickupLng != 0.0) {
            val pickupMarkerState = rememberMarkerState(position = LatLng(pickupLat, pickupLng))
            LaunchedEffect(pickupLat, pickupLng) {
                pickupMarkerState.position = LatLng(pickupLat, pickupLng)
            }
            Marker(
                state = pickupMarkerState,
                title = "Your Pickup Location",
                snippet = pickupAddress,
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
        }

        // Dropoff marker
        if (dropoffLat != 0.0 && dropoffLng != 0.0) {
            val dropoffMarkerState = rememberMarkerState(position = LatLng(dropoffLat, dropoffLng))
            LaunchedEffect(dropoffLat, dropoffLng) {
                dropoffMarkerState.position = LatLng(dropoffLat, dropoffLng)
            }
            Marker(
                state = dropoffMarkerState,
                title = "Dropoff Destination",
                snippet = selectedDropoffAddress,
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }

        // Assigned rider marker
        riderLatLng?.let { rLatLng ->
            val riderMarkerState = rememberMarkerState(position = rLatLng)
            LaunchedEffect(rLatLng) { riderMarkerState.position = rLatLng }
            Marker(
                state = riderMarkerState,
                title = if (driverName.isNotEmpty()) "$driverName is on the way" else "Rider is on the way"
            )
        }

        // Nearby rider markers + proximity circle (only when no active booking)
        if (activeBookingId == null) {
            nearbyRiders.forEach { rider ->
                val nearbyMarkerState = rememberMarkerState(position = LatLng(rider.lat, rider.lng))
                Marker(
                    state = nearbyMarkerState,
                    title = "Nearby Rider",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
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
}