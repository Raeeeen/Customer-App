package com.cassey.customerapp.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.cassey.customerapp.data.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String? = null,
    val name: String? = null,
    val phone_number: String? = null,
    val role: String? = null,
    val created_at: String? = null
)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("PHONE") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    val formattedPhone = phone.filter { it.isDigit() }.let {
        when {
            it.startsWith("0") -> "+63${it.drop(1)}"
            it.startsWith("63") -> "+$it"
            else -> "+63$it"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00B14F))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            "Ride&Go",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {

                error?.let {
                    Text(it, color = Color.Red)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                AnimatedVisibility(step == "PHONE") {
                    Column {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null

                                    try {
                                        val profile = SupabaseClient.client
                                            .postgrest["profiles"]
                                            .select {
                                                filter { eq("phone_number", formattedPhone) }
                                            }
                                            .decodeSingleOrNull<Profile>()

                                        if (profile == null) {
                                            // Dont send OTP yet we need their name first
                                            step = "SIGNUP"
                                        } else {
                                            // Profile exists Send OTP and go to verification
                                            SupabaseClient.client.auth.signInWith(OTP) {
                                                this.phone = formattedPhone
                                            }
                                            step = "OTP"
                                        }

                                    } catch (e: Exception) {
                                        error = e.message
                                    }

                                    loading = false
                                }
                            }
                        ) {
                            Text(if (loading) "Loading..." else "Continue")
                        }
                    }
                }

                // SIGNUP STEP
                AnimatedVisibility(step == "SIGNUP") {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null

                                    try {
                                        SupabaseClient.client.auth.signInWith(OTP) {
                                            this.phone = formattedPhone
                                        }
                                        // Move to OTP screen
                                        step = "OTP"

                                    } catch (e: Exception) {
                                        error = e.message
                                    }

                                    loading = false
                                }
                            }
                        ) {
                            Text(if (loading) "Loading..." else "Sign Up")
                        }
                    }
                }

                // OTP STEP
                AnimatedVisibility(step == "OTP") {
                    Column {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it },
                            label = { Text("OTP Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    loading = true
                                    error = null

                                    try {
                                        SupabaseClient.client.auth.verifyPhoneOtp(
                                            type = OtpType.Phone.SMS,
                                            phone = formattedPhone,
                                            token = otp
                                        )

                                        if (name.isNotEmpty()) {
                                            val currentUser = SupabaseClient.client.auth.currentUserOrNull()

                                            if (currentUser != null) {
                                                SupabaseClient.client.postgrest["profiles"].insert(
                                                    mapOf(
                                                        "id" to currentUser.id,
                                                        "name" to name,
                                                        "phone_number" to formattedPhone,
                                                        "role" to "customer"
                                                    )
                                                )
                                            }
                                        }

                                        onLoginSuccess()

                                    } catch (e: Exception) {
                                        error = e.message
                                    }

                                    loading = false
                                }
                            }
                        ) {
                            Text(if (loading) "Verifying..." else "Verify OTP")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = {
                                scope.launch {
                                    SupabaseClient.client.auth.signInWith(OTP) {
                                        this.phone = formattedPhone
                                    }
                                }
                            }
                        ) {
                            Text("Resend OTP")
                        }
                    }
                }
            }
        }
    }
}