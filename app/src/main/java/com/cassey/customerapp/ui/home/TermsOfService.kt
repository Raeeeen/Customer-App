package com.cassey.customerapp.ui.home

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cassey.customerapp.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TermsOfServiceRecord(
    val id: Long,
    val version: String,
    val content: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val role: String
)

private const val TOS_PREFS = "RideGoPrefs"
private const val KEY_TOS_ACCEPTED = "tos_accepted"
private const val KEY_TOS_ACCEPTED_VERSION = "tos_accepted_version"

fun hasAcceptedCurrentTerms(context: Context, activeVersion: String?): Boolean {
    val prefs = context.getSharedPreferences(TOS_PREFS, Context.MODE_PRIVATE)
    val accepted = prefs.getBoolean(KEY_TOS_ACCEPTED, false)
    if (!accepted) return false
    if (activeVersion != null) {
        val acceptedVersion = prefs.getString(KEY_TOS_ACCEPTED_VERSION, null)
        return acceptedVersion == activeVersion
    }
    return true
}

private fun markTermsAccepted(context: Context, version: String) {
    context.getSharedPreferences(TOS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_TOS_ACCEPTED, true)
        .putString(KEY_TOS_ACCEPTED_VERSION, version)
        .apply()
}

suspend fun fetchCustomerTermsOfService(): TermsOfServiceRecord? {
    return withContext(Dispatchers.IO) {
        try {
            SupabaseClient.client.from("terms_of_service")
                .select {
                    filter {
                        eq("role", "customer")
                        eq("is_active", true)
                    }
                }
                .decodeSingleOrNull<TermsOfServiceRecord>()
        } catch (e: Exception) {
            Log.e("TermsOfService", "Failed to fetch terms of service: ${e.message}", e)
            null
        }
    }
}

@Composable
fun TermsOfServiceDialog(
    terms: TermsOfServiceRecord,
    isReadOnlyReview: Boolean = false,
    onAccept: () -> Unit,
    onDismissReview: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var hasScrolledToEnd by remember { mutableStateOf(isReadOnlyReview) }

    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate400 = Color(0xFF94A3B8)
    val emerald600 = Color(0xFF229954)
    val slate700 = Color(0xFF334155)

    LaunchedEffect(scrollState.maxValue, scrollState.value) {
        if (!isReadOnlyReview && scrollState.maxValue > 0) {
            if (scrollState.value >= scrollState.maxValue - 24) {
                hasScrolledToEnd = true
            }
        } else if (!isReadOnlyReview && scrollState.maxValue == 0) {
            hasScrolledToEnd = true
        }
    }

    Dialog(
        onDismissRequest = { if (isReadOnlyReview) onDismissReview() },
        properties = DialogProperties(
            dismissOnBackPress = isReadOnlyReview,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = slate900
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(slate800)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = terms.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                HorizontalDivider(color = slate700)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isReadOnlyReview) {
                        Button(
                            onClick = onDismissReview,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = emerald600)
                        ) {
                            Text(text = "Close", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        if (!hasScrolledToEnd) {
                            Text(
                                text = "Please scroll down to read the full Terms of Service before accepting.",
                                style = MaterialTheme.typography.labelSmall,
                                color = slate400,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = {
                                markTermsAccepted(context, terms.version)
                                onAccept()
                            },
                            enabled = hasScrolledToEnd,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = emerald600,
                                disabledContainerColor = slate700
                            )
                        ) {
                            Text(
                                text = if (hasScrolledToEnd) "I Accept" else "Scroll to Continue Reading",
                                fontWeight = FontWeight.Bold,
                                color = if (hasScrolledToEnd) Color.White else slate400
                            )
                        }
                    }
                }
            }
        }
    }
}