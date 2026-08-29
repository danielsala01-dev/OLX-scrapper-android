package com.olx.scraper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.olx.scraper.api.ApiClient
import com.olx.scraper.auth.TokenManager
import com.olx.scraper.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenManager = TokenManager(applicationContext)
        ApiClient.init(tokenManager)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var isLoggedIn by remember { mutableStateOf(tokenManager.hasToken()) }

                    if (!isLoggedIn) {
                        LoginScreen(
                            tokenManager = tokenManager,
                            onLoginSuccess = { isLoggedIn = true }
                        )
                    } else {
                        MainNavigation(
                            tokenManager = tokenManager,
                            onLogout = { isLoggedIn = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainNavigation(tokenManager: TokenManager, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Category, "Categories") },
                    label = { Text("Categories") },
                    selected = currentRoute == "categories",
                    onClick = { navController.navigate("categories") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Favorite, "Favorites") },
                    label = { Text("Favorites") },
                    selected = currentRoute == "favorites",
                    onClick = { navController.navigate("favorites") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") {
                    HomeScreen(navController = navController, onUnauthorized = {
                        tokenManager.clearToken()
                        onLogout()
                    })
                }
                composable("categories") {
                    CategoriesScreen(navController)
                }
                composable("favorites") {
                    FavoritesScreen(navController)
                }
                composable("settings") {
                    SettingsScreen(
                        navController = navController,
                        tokenManager = tokenManager,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}
