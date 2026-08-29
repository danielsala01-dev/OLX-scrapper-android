package com.olx.scraper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.olx.scraper.api.ApiClient
import com.olx.scraper.api.Listing
import com.olx.scraper.api.ListingsResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun HomeScreen(navController: NavController, onUnauthorized: () -> Unit = {}) {
    var listings by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        ApiClient.apiService.getListings().enqueue(object : Callback<ListingsResponse> {
            override fun onResponse(call: Call<ListingsResponse>, response: Response<ListingsResponse>) {
                loading = false
                when {
                    response.code() == 401 -> {
                        onUnauthorized()
                    }
                    response.isSuccessful -> {
                        listings = response.body()?.data ?: emptyList()
                    }
                    else -> {
                        error = "Error: ${response.code()}"
                    }
                }
            }

            override fun onFailure(call: Call<ListingsResponse>, t: Throwable) {
                loading = false
                error = "Error: ${t.message}"
            }
        })
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Home", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> Text("Loading...")
            error != null -> Text("Error: $error")
            listings.isEmpty() -> Text("No listings")
            else -> {
                LazyColumn {
                    items(listings) { listing ->
                        ListingItem(listing)
                    }
                }
            }
        }
    }
}

@Composable
fun ListingItem(listing: Listing) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(listing.title, style = MaterialTheme.typography.bodyLarge)
            Text("${listing.price}zł", style = MaterialTheme.typography.bodySmall)
            Text(listing.category, style = MaterialTheme.typography.labelSmall)
        }
    }
}
