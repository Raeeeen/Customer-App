package com.cassey.customerapp.ui.auth

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.cassey.customerapp.data.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalContext
import io.github.jan.supabase.auth.providers.builtin.Phone
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.jan.supabase.postgrest.rpc
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.cassey.customerapp.ui.navigation.PasswordResetFlag

@Serializable
data class Profile(
    val id: String? = null,
    val name: String? = null,
    val phone_number: String? = null,
    val role: String? = null,
    val created_at: String? = null
)

private fun getFriendlyErrorMessage(rawError: String): String {
    val lower = rawError.lowercase()
    return when {
        lower.contains("invalid") && lower.contains("credential") -> "Incorrect phone number or password."
        lower.contains("otp") && lower.contains("expired") -> "Your OTP code has expired."
        lower.contains("network") || lower.contains("timeout") -> "Connection issue. Check your internet."
        lower.contains("rate limit") || lower.contains("too many requests") -> "Too many attempts. Try again later."
        lower.contains("bad request") || lower.contains("format") -> "Please check your phone number format."
        else -> "An authentication anomaly occurred. Please retry."
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("PHONE") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val formattedPhone = phone.filter { it.isDigit() }.let {
        when {
            it.startsWith("0") -> "+63${it.drop(1)}"
            else -> "+63$it"
        }
    }
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
    }

    fun sendSupabaseOtp(onSuccess: () -> Unit) {
        scope.launch {
            try {
                SupabaseClient.client.auth.signInWith(OTP) {
                    this.phone = formattedPhone
                }
                onSuccess()
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF00A348),
                            Color(0xFF00C257)
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Ride&Go",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-1.0).sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Move with ease",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.9f),
                    letterSpacing = 0.2.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(90.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when(step) {
                                    "PHONE" -> "Welcome Back"
                                    "SIGNUP" -> "Create Account"
                                    "LOGIN_PASSWORD" -> "Enter Password"
                                    "FORGOT_OTP" -> "Reset Password"
                                    "FORGOT_NEW_PASS" -> "New Password"
                                    else -> "Verify Identity"
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = when(step) {
                                    "PHONE" -> "Enter your mobile number to begin."
                                    "SIGNUP" -> "Tell us your legal full name."
                                    "LOGIN_PASSWORD" -> "Enter your password to continue."
                                    "FORGOT_OTP" -> "Enter the code sent to your phone."
                                    "FORGOT_NEW_PASS" -> "Choose a new password."
                                    else -> "Enter the code sent to your phone."
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }

                        if (step != "PHONE") {
                            IconButton(
                                onClick = {
                                    error = null
                                    step = "PHONE"
                                    otp = ""
                                    newPassword = ""
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate Back",
                                    tint = Color(0xFF374151),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    error?.let { rawErrorString ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFEE2E2), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = getFriendlyErrorMessage(
                                    rawErrorString
                                ),
                                color = Color(0xFF991B1B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    AnimatedVisibility(
                        visible = (step == "PHONE"),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }
                                    if (digits.length <= 11) {
                                        phone = digits
                                    }
                                    if (error != null) error = null
                                },
                                label = { Text("Mobile Phone Number") },
                                placeholder = { Text("09XXXXXXXXX") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = Color(0xFF00B14F),
                                    focusedLabelColor = Color(0xFF00B14F),
                                    unfocusedBorderColor = Color(0xFFE5E7EB)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B14F)),
                                enabled = (phone.length == 11 && phone.startsWith("09") && !loading),
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            val isRegistered = SupabaseClient.client.postgrest.rpc(
                                                "phone_is_registered",
                                                mapOf("check_phone" to formattedPhone)
                                            ).decodeAs<Boolean>()

                                            step = if (isRegistered) "LOGIN_PASSWORD" else "SIGNUP"
                                            loading = false
                                        } catch (e: Exception) {
                                            error = e.message
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Text("Continue Session", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = (step == "SIGNUP"),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    if (error != null) error = null
                                },
                                label = { Text("Full Name") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = Color(0xFF00B14F),
                                    focusedLabelColor = Color(0xFF00B14F),
                                    unfocusedBorderColor = Color(0xFFE5E7EB)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    if (error != null) error = null
                                },
                                label = { Text("Create Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = Color(0xFF00B14F),
                                    focusedLabelColor = Color(0xFF00B14F),
                                    unfocusedBorderColor = Color(0xFFE5E7EB)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B14F)),
                                enabled = (name.trim().isNotEmpty() && password.length >= 6 && !loading),
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            SupabaseClient.client.auth.signUpWith(Phone) {
                                                this.phone = formattedPhone
                                                this.password = password
                                            }
                                            step = "OTP"
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Text("Register Profile", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = (step == "LOGIN_PASSWORD"),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {

                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    if (error != null) error = null
                                },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = Color(0xFF6B7280)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = Color(0xFF00B14F),
                                    focusedLabelColor = Color(0xFF00B14F),
                                    unfocusedBorderColor = Color(0xFFE5E7EB)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            SupabaseClient.client.auth.signInWith(OTP) {
                                                this.phone = formattedPhone
                                            }
                                            otp = ""
                                            step = "FORGOT_OTP"
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Forgot Password?", color = Color(0xFF00B14F), fontWeight = FontWeight.Bold)
                            }

                            Button(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B14F)),
                                enabled = (password.isNotEmpty() && !loading),
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            SupabaseClient.client.auth.signInWith(Phone) {
                                                this.phone = formattedPhone
                                                this.password = password
                                            }

                                            val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                                            if (currentUser != null) {
                                                val allowed = SupabaseClient.client.postgrest.rpc(
                                                    "check_device_binding",
                                                    mapOf(
                                                        "user_id" to currentUser.id,
                                                        "requesting_device_id" to deviceId
                                                    )
                                                ).decodeAs<Boolean>()

                                                if (!allowed) {
                                                    SupabaseClient.client.auth.signOut()
                                                    error = "This account is already logged in on another device."
                                                    loading = false
                                                    return@launch
                                                }

                                                SupabaseClient.client.postgrest["profiles"].upsert(
                                                    mapOf(
                                                        "id" to currentUser.id,
                                                        "bound_device_id" to deviceId,
                                                        "bound_at" to java.time.Instant.now().toString()
                                                    )
                                                ) { onConflict = "id" }
                                            }

                                            onLoginSuccess()
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }


                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Text("Log In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = (step == "FORGOT_OTP"),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = otp,
                                onValueChange = {
                                    otp = it.filter { digit -> digit.isDigit() }
                                    if (error != null) error = null
                                },
                                label = { Text("6-Digit Code") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedBorderColor = Color(0xFF00B14F),
                                    focusedLabelColor = Color(0xFF00B14F),
                                    unfocusedBorderColor = Color(0xFFE5E7EB)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B14F)),
                                enabled = (otp.isNotEmpty() && !loading),
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
                                            PasswordResetFlag.set(context, true)
                                            otp = ""
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Text("Verify Code", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            SupabaseClient.client.auth.signInWith(OTP) {
                                                this.phone = formattedPhone
                                            }
                                            error = "Verification code resent."
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Resend Verification Code", color = Color(0xFF00B14F), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = (step == "OTP"),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = otp,
                                onValueChange = {
                                    otp = it.filter { digit -> digit.isDigit() }
                                    if (error != null) error = null
                                },
                                label = { Text("6-Digit Code") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,

                                    focusedBorderColor = Color(0xFF00B14F),
                                    focusedLabelColor = Color(0xFF00B14F),
                                    unfocusedBorderColor = Color(0xFFE5E7EB)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B14F)),
                                enabled = (otp.isNotEmpty() && !loading),
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
                                            handleDatabaseAndFinish(name, formattedPhone, deviceId, onLoginSuccess)
                                        } catch (e: Exception) {
                                            error = e.message
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Text("Verify Identity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    loading = true
                                    sendSupabaseOtp { error = "Verification code resent." }
                                }
                            ) {
                                Text("Resend Verification Code", color = Color(0xFF00B14F), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = (step == "FORGOT_NEW_PASS"),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column {
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                                            SupabaseClient.client.auth.updateUser {
                                                password = newPassword
                                            }
                                            onLoginSuccess()
                                        } catch (e: Exception) {
                                            error = e.message
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
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private suspend fun handleDatabaseAndFinish(
    name: String,
    formattedPhone: String,
    deviceId: String,
    onLoginSuccess: () -> Unit
) {
    val currentUser = SupabaseClient.client.auth.currentUserOrNull()
    if (currentUser != null) {
        try {
            if (name.isNotEmpty()) {
                SupabaseClient.client.postgrest["profiles"].insert(
                    mapOf(
                        "id" to currentUser.id,
                        "name" to name,
                        "phone_number" to formattedPhone,
                        "role" to "customer",
                        "bound_device_id" to deviceId,
                        "bound_at" to java.time.Instant.now().toString()
                    )
                )
            } else {
                SupabaseClient.client.postgrest["profiles"].upsert(
                    mapOf(
                        "id" to currentUser.id,
                        "phone_number" to formattedPhone,
                        "role" to "customer",
                        "bound_device_id" to deviceId,
                        "bound_at" to java.time.Instant.now().toString()
                    )
                ) {
                    onConflict = "id"
                }
            }
            onLoginSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            onLoginSuccess()
        }
    }
}