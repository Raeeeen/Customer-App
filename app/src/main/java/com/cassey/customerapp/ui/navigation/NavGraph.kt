package com.cassey.customerapp.ui.navigation

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cassey.customerapp.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import com.cassey.customerapp.ui.auth.LoginScreen
import com.cassey.customerapp.ui.home.HomeScreen
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.jsonPrimitive

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val sessionStatus by SupabaseClient.client.auth.sessionStatus.collectAsState()

    when (sessionStatus) {
        is SessionStatus.Initializing -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SessionStatus.Authenticated -> {
            val context = LocalContext.current
            val myDeviceId = remember {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "unknown-device"
            }

            var deviceCheckPassed by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(myDeviceId) {
                if (PasswordResetFlag.get(context)) {
                    deviceCheckPassed = true
                    return@LaunchedEffect
                }

                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    SupabaseClient.client.auth.signOut()
                    return@LaunchedEffect
                }

                try {
                    val profile = SupabaseClient.client.postgrest["profiles"]
                        .select {
                            filter { eq("id", userId) }
                        }
                        .decodeSingleOrNull<Map<String, kotlinx.serialization.json.JsonElement>>()

                    if (profile == null) {
                        SupabaseClient.client.auth.signOut()
                        return@LaunchedEffect
                    }

                    val boundDeviceId = profile["bound_device_id"]?.jsonPrimitive?.content

                    if (boundDeviceId == null || boundDeviceId == myDeviceId) {
                        deviceCheckPassed = true
                    } else {
                        deviceCheckPassed = false
                        SupabaseClient.client.auth.signOut()
                    }
                } catch (e: Exception) {
                    deviceCheckPassed = true
                }
            }

            LaunchedEffect(myDeviceId) {
                while (true) {
                    kotlinx.coroutines.delay(10_000)
                    if (PasswordResetFlag.get(context)) continue

                    if (deviceCheckPassed != true) continue

                    val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: continue

                    try {
                        val profile = SupabaseClient.client.postgrest["profiles"]
                            .select { filter { eq("id", userId) } }
                            .decodeSingleOrNull<Map<String, kotlinx.serialization.json.JsonElement>>()

                        if (profile == null) {
                            SupabaseClient.client.auth.signOut()
                            break
                        }

                        val boundDeviceId = profile["bound_device_id"]?.jsonPrimitive?.content

                        if (boundDeviceId != null && boundDeviceId != myDeviceId) {

                            val activeBookings = SupabaseClient.client.postgrest["bookings"]
                                .select {
                                    filter {
                                        eq("customer_id", userId)
                                        isIn("status", listOf("accepted", "in_progress"))
                                    }
                                }
                                .decodeList<Map<String, kotlinx.serialization.json.JsonElement>>()

                            if (activeBookings.isEmpty()) {
                                SupabaseClient.client.auth.signOut()
                                break
                            } else {
                                Log.d("DeviceCheck", "Active trip — will check again next cycle")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DeviceCheck", "Periodic check error: ${e.message}")
                    }
                }
            }

            when (deviceCheckPassed) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                true -> {
                    DeviceWatcher(myDeviceId = myDeviceId)

                    val needsNewPassword = remember { PasswordResetFlag.get(context) }

                    NavHost(
                        navController = navController,
                        startDestination = if (needsNewPassword) Screen.SetNewPassword.route else Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen()
                        }
                        composable(Screen.SetNewPassword.route) {
                            com.cassey.customerapp.ui.auth.SetNewPasswordScreen(
                                onPasswordSet = {
                                    PasswordResetFlag.set(context, false)
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.SetNewPassword.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }

                false -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        is SessionStatus.NotAuthenticated,
        is SessionStatus.RefreshFailure -> {
            NavHost(
                navController = navController,
                startDestination = Screen.Login.route
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(onLoginSuccess = {})
                }
            }
        }
    }
}

object PasswordResetFlag {
    private const val PREFS = "RideGoAuthPrefs"
    private const val KEY = "needs_new_password"

    fun set(context: android.content.Context, value: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }

    fun get(context: android.content.Context): Boolean {
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }
}

@Composable
private fun DeviceWatcher(myDeviceId: String) {
    LaunchedEffect(myDeviceId) {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect

        val profileChannel = SupabaseClient.client.realtime.channel("profile-watch-$userId")
        val profileFlow = profileChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
            filter("id", FilterOperator.EQ, userId)
        }
        profileChannel.subscribe()

        profileFlow.collect { change ->
            if (change is PostgresAction.Delete) {
                SupabaseClient.client.auth.signOut()
                return@collect
            }

            if (change !is PostgresAction.Update) return@collect

            val rawValue = change.record["bound_device_id"]
            if (rawValue == null || rawValue is kotlinx.serialization.json.JsonNull) return@collect

            val newDeviceId = rawValue.toString().trim('"')
            if (newDeviceId.isEmpty() || newDeviceId == myDeviceId) return@collect

            try {
                val activeBookings = SupabaseClient.client.postgrest["bookings"]
                    .select {
                        filter {
                            eq("customer_id", userId)
                            or {
                                eq("status", "accepted")
                                eq("status", "in_progress")
                            }
                        }
                    }
                    .decodeList<Map<String, kotlinx.serialization.json.JsonElement>>()


                if (activeBookings.isEmpty()) {
                    SupabaseClient.client.auth.signOut()
                } else {

                    val bookingChannel = SupabaseClient.client.realtime.channel("booking-watch-$userId")
                    val bookingFlow = bookingChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "bookings"
                        filter("customer_id", FilterOperator.EQ, userId)
                    }
                    bookingChannel.subscribe()

                    bookingFlow.collect { bookingChange ->
                        if (bookingChange !is PostgresAction.Update) return@collect
                        val status = bookingChange.record["status"]?.toString()?.trim('"')
                        if (status == "completed" || status == "cancelled") {
                            SupabaseClient.client.auth.signOut()
                        }
                    }
                }
            } catch (e: Exception) {
                SupabaseClient.client.auth.signOut()
            }
        }
    }
}