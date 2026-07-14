package com.cassey.customerapp.ui.home

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeDashboard(
    state: HomeState,
    sharedPrefs: android.content.SharedPreferences,
    scope: CoroutineScope,
    cameraPositionState: CameraPositionState,
    isoTimestamp: String,
    emerald400: Color,
    rose400: Color,
    indigo600: Color,
    slate300: Color,
    slate400: Color,
    slate600: Color,
    slate700: Color,
    slate800: Color,
    slate900: Color,
    emerald600: Color,
    onClearBooking: () -> Unit,
    onFetchGPS: () -> Unit,
    onSearchDestination: (String) -> Unit,
    onUpdateTripDetails: (Double, Double, Double, Double) -> Unit,
    onHideDashboard: () -> Unit,
    onConfirmBooking: () -> Unit
) {
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
            // Header
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
                TextButton(onClick = onHideDashboard) {
                    Text(text = "Hide", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Trip info card
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
                    Text(text = state.pickupAddress, style = MaterialTheme.typography.bodyMedium, color = Color.White)

                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "ESTIMATED FARE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = slate400
                        )
                        Text(
                            text = "₱${String.format("%.2f", state.estimatedFare)}",
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

                    // Dropoff input
                    OutlinedTextField(
                        value = state.dropoffInput,
                        onValueChange = { newValue ->
                            state.dropoffInput = newValue
                            if (newValue.trim().isEmpty()) {
                                state.dropoffLat = 0.0
                                state.dropoffLng = 0.0
                                state.selectedDropoffAddress = ""
                                state.suggestions = emptyList()
                                state.tripDistance = 0.0
                                state.estimatedFare = 0.0
                                state.statusMessage = "Long-press on the map to pin your destination!"
                            } else {
                                onSearchDestination(newValue)
                            }
                        },
                        enabled = state.activeBookingId == null,
                        placeholder = {
                            Text(
                                text = if (state.activeBookingId != null) "Booking in progress..."
                                else "Type destination or long-press map...",
                                color = slate400
                            )
                        },
                        trailingIcon = {
                            if (state.dropoffInput.isNotEmpty() && state.activeBookingId == null) {
                                IconButton(onClick = {
                                    state.dropoffInput = ""
                                    state.dropoffLat = 0.0
                                    state.dropoffLng = 0.0
                                    state.selectedDropoffAddress = ""
                                    state.suggestions = emptyList()
                                    state.tripDistance = 0.0
                                    state.estimatedFare = 0.0
                                    state.statusMessage = "Long-press on the map to pin your destination!"
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
                            disabledTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            disabledBorderColor = slate700.copy(alpha = 0.5f),
                            focusedBorderColor = indigo600,
                            unfocusedBorderColor = slate700,
                            cursorColor = Color.White,
                            focusedPlaceholderColor = slate400,
                            unfocusedPlaceholderColor = slate400,
                            disabledPlaceholderColor = slate400
                        )
                    )

                    // Autocomplete suggestion list
                    AnimatedVisibility(
                        visible = state.suggestions.isNotEmpty() && state.activeBookingId == null,
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
                            state.suggestions.forEachIndexed { index, suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            state.dropoffLat = suggestion.latitude
                                            state.dropoffLng = suggestion.longitude
                                            state.selectedDropoffAddress = suggestion.displayName
                                            state.dropoffInput = suggestion.displayName
                                            state.suggestions = emptyList()
                                            sharedPrefs.edit()
                                                .putFloat("saved_dropoff_lat", suggestion.latitude.toFloat())
                                                .putFloat("saved_dropoff_lng", suggestion.longitude.toFloat())
                                                .putString("saved_dropoff_address", suggestion.displayName)
                                                .apply()
                                            onUpdateTripDetails(
                                                state.pickupLat, state.pickupLng,
                                                suggestion.latitude, suggestion.longitude
                                            )
                                            scope.launch {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(suggestion.latitude, suggestion.longitude), 22f
                                                    )
                                                )
                                            }
                                            state.statusMessage = "Destination set!"
                                        }
                                        .background(if (index % 2 == 0) slate800 else slate700.copy(alpha = 0.2f))
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
                                if (index < state.suggestions.size - 1) {
                                    HorizontalDivider(color = slate700.copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active booking panel or booking button
            if (state.activeBookingId != null) {
                BookingStatusPanel(
                    bookingStatus = state.bookingStatus,
                    activeBookingId = state.activeBookingId,
                    driverName = state.driverName,
                    driverPhone = state.driverPhone,
                    estimatedFare = state.estimatedFare,
                    isoTimestamp = isoTimestamp,
                    scope = scope,
                    emerald400 = emerald400,
                    emerald600 = emerald600,
                    rose400 = rose400,
                    indigo600 = indigo600,
                    slate400 = slate400,
                    slate600 = slate600,
                    slate700 = slate700,
                    slate800 = slate800,
                    slate900 = slate900,
                    onClearBooking = onClearBooking,
                    onFetchGPS = onFetchGPS
                )
            } else {
                val staleFixThresholdMs = 60_000L
                val isFixStale = state.lastFixTimestampMillis == 0L ||
                        (System.currentTimeMillis() - state.lastFixTimestampMillis) > staleFixThresholdMs

                val statusBgColor = when (state.success) {
                    true  -> emerald400.copy(alpha = 0.15f)
                    false -> rose400.copy(alpha = 0.15f)
                    null  -> slate900
                }
                val statusTextColor = when (state.success) {
                    true  -> emerald400
                    false -> rose400
                    null  -> slate300
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = (!state.isPickupGpsReady || isFixStale) && !state.loading) {
                            onFetchGPS()
                        },
                    colors = CardDefaults.cardColors(containerColor = statusBgColor),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusTextColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = state.statusMessage,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusTextColor,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onConfirmBooking,
                    enabled = !state.loading && state.isPickupGpsReady && !isFixStale &&
                            state.dropoffLat != 0.0 && state.dropoffLng != 0.0 &&
                            state.estimatedFare > 0.0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = indigo600,
                        disabledContainerColor = slate700
                    )
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        val buttonText = when {
                            !state.isPickupGpsReady -> "Waiting for Location lock (Please Wait)..."
                            isFixStale -> "Refreshing your location..."
                            state.dropoffLat == 0.0 || state.dropoffLng == 0.0 -> "Select a Destination to Book"
                            else -> "Confirm Booking Request"
                        }
                        Text(
                            text = buttonText,
                            fontWeight = FontWeight.Bold,
                            color = if (state.dropoffLat == 0.0) slate400 else Color.White
                        )
                    }
                }
            }
        }
    }
}