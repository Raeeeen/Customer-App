package com.cassey.customerapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen()
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
                    LoginScreen(
                        onLoginSuccess = {}
                    )
                }
            }
        }
    }
}