package com.cassey.customerapp.ui.home

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val placeId: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
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