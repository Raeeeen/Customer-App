package com.cassey.customerapp.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
}