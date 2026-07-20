package com.cassey.customerapp.ui.home

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

fun isGooglePlayServicesAvailable(context: Context): Boolean {
    val availability = GoogleApiAvailability.getInstance()
    val resultCode = availability.isGooglePlayServicesAvailable(context)
    return resultCode == ConnectionResult.SUCCESS
}