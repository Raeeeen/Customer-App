package com.cassey.customerapp.ui.auth

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cassey.customerapp.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.annotation.RequiresApi
import io.github.jan.supabase.postgrest.postgrest

private fun getFriendlyPasswordError(rawError: String): String {
    val lower = rawError.lowercase()
    return when {
        lower.contains("same password") || lower.contains("different from the old") || lower.contains("should be different") -> "Your new password must be different from your current one."
        lower.contains("network") || lower.contains("timeout") -> "Connection issue. Check your internet."
        lower.contains("weak") || lower.contains("too short") -> "Password is too weak. Use at least 6 characters."
        else -> "Something went wrong. Please try again."
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SetNewPasswordScreen(onPasswordSet: () -> Unit) {
    val scope = rememberCoroutineScope()
    var newPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Text("Set a New Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                Text("Choose a new password to finish resetting your account.", fontSize = 13.sp, color = Color(0xFF6B7280))
                Spacer(modifier = Modifier.height(16.dp))

                error?.let {
                    Text(it, color = Color(0xFF991B1B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        if (error != null) error = null
                    },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color(0xFF00B14F),
                        focusedLabelColor = Color(0xFF00B14F),
                        unfocusedBorderColor = Color(0xFFE5E7EB)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B14F)),
                    enabled = (newPassword.length >= 6 && !loading),
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                                if (currentUser != null) {
                                    SupabaseClient.client.postgrest["profiles"]
                                        .update(
                                            mapOf(
                                                "bound_device_id" to null,
                                                "bound_at" to null
                                            )
                                        ) {
                                            filter { eq("id", currentUser.id) }
                                        }
                                }

                                SupabaseClient.client.auth.updateUser {
                                    password = newPassword
                                }

                                if (currentUser != null) {
                                    SupabaseClient.client.postgrest["profiles"]
                                        .update(
                                            mapOf(
                                                "bound_device_id" to deviceId,
                                                "bound_at" to java.time.Instant.now().toString()
                                            )
                                        ) {
                                            filter { eq("id", currentUser.id) }
                                        }
                                }

                                onPasswordSet()
                            } catch (e: Exception) {
                                error = getFriendlyPasswordError(e.message ?: "")
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        Text("Set New Password", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}