package com.cassey.customerapp.ui.home

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cassey.customerapp.data.SupabaseClient
import com.cassey.customerapp.network.NetworkQuality
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun BookingStatusPanel(
    bookingStatus: String?,
    activeBookingId: String?,
    driverName: String,
    driverPhone: String,
    estimatedFare: Double,
    isoTimestamp: String,
    scope: CoroutineScope,
    emerald400: Color,
    emerald600: Color,
    rose400: Color,
    indigo600: Color,
    slate400: Color,
    slate600: Color,
    slate700: Color,
    slate800: Color,
    slate900: Color,
    onClearBooking: () -> Unit,
    onFetchGPS: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = slate900),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, indigo600.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                                        SupabaseClient.client.from("bookings").delete {
                                            filter {
                                                eq("id", currentBookingId)
                                                eq("status", "pending")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("RideGoCancel", "Failed to delete booking row", e)
                                } finally {
                                    onClearBooking()
                                    Toast.makeText(context, "Booking cancelled.", Toast.LENGTH_LONG).show()
                                    onFetchGPS()
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = emerald600)
                        ) { Text("Cash") }

                        Button(
                            onClick = {
                                scope.launch {
                                    var updateSucceeded = false
                                    try {
                                        activeBookingId?.let { bookingId ->
                                            NetworkQuality.timed {
                                                withTimeout(10000) {
                                                    SupabaseClient.client.from("bookings").update(
                                                        mapOf("status" to "completed", "updated_at" to isoTimestamp)
                                                    ) { filter { eq("id", bookingId) } }
                                                }
                                            }
                                            updateSucceeded = true
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        Toast.makeText(context, "Connection too slow. Please try again.", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Couldn't reach server. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                    if (updateSucceeded) {
                                        val fare = estimatedFare
                                        onClearBooking()
                                        Toast.makeText(
                                            context,
                                            "Ride completed! ₱${"%.2f".format(fare)} via GCash.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onFetchGPS()

                                        try {
                                            val currentUser =
                                                SupabaseClient.client.auth.currentUserOrNull()
                                            if (currentUser != null) {
                                                val myDeviceId =
                                                    android.provider.Settings.Secure.getString(
                                                        context.contentResolver,
                                                        android.provider.Settings.Secure.ANDROID_ID
                                                    ) ?: "unknown-device"

                                                val profile =
                                                    SupabaseClient.client.postgrest["profiles"]
                                                        .select {
                                                            filter {
                                                                eq(
                                                                    "id",
                                                                    currentUser.id
                                                                )
                                                            }
                                                        }
                                                        .decodeSingle<Map<String, kotlinx.serialization.json.JsonElement>>()

                                                val boundDeviceId =
                                                    profile["bound_device_id"]?.jsonPrimitive?.content

                                                if (boundDeviceId != null && boundDeviceId != myDeviceId) {
                                                    SupabaseClient.client.auth.signOut()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("DeviceWatcher", "Post-trip device check failed: ${e.message}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0052FF))
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
}