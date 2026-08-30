package com.olx.scraper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.olx.scraper.api.ApiService
import com.olx.scraper.api.Category
import com.olx.scraper.api.Marketplace
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

@Composable
fun CategoriesScreen(navController: NavController) {
    // TODO: na produkcji przenieś to do jednego miejsca w projekcie
    val baseUrl = "http://192.168.0.160:5000/"

    val api = remember {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    var marketplaces by remember { mutableStateOf<List<Marketplace>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    var selectedMarketplaceKey by remember { mutableStateOf<String?>(null) }
    var selectedCategoryLabel by remember { mutableStateOf<String?>(null) }

    var loadingMarketplaces by remember { mutableStateOf(false) }
    var loadingCategories by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // 1) Pobierz marketplace po wejściu na ekran
    LaunchedEffect(Unit) {
        loadingMarketplaces = true
        errorText = null

        api.getMarketplaces().enqueue(object : Callback<com.olx.scraper.api.MarketplacesResponse> {
            override fun onResponse(
                call: Call<com.olx.scraper.api.MarketplacesResponse>,
                response: Response<com.olx.scraper.api.MarketplacesResponse>
            ) {
                loadingMarketplaces = false
                if (response.isSuccessful) {
                    val list = response.body()?.results ?: emptyList()
                    marketplaces = list

                    // domyślnie pierwszy marketplace
                    if (list.isNotEmpty() && selectedMarketplaceKey == null) {
                        selectedMarketplaceKey = list.first().key
                    }
                } else {
                    errorText = "Nie udało się pobrać marketplace (HTTP ${response.code()})"
                }
            }

            override fun onFailure(
                call: Call<com.olx.scraper.api.MarketplacesResponse>,
                t: Throwable
            ) {
                loadingMarketplaces = false
                errorText = "Błąd sieci: ${t.message}"
            }
        })
    }

    // 2) Gdy zmieni się marketplace, pobierz kategorie
    LaunchedEffect(selectedMarketplaceKey) {
        val key = selectedMarketplaceKey ?: return@LaunchedEffect
        loadingCategories = true
        errorText = null
        selectedCategoryLabel = null

        api.getCategories(key).enqueue(object : Callback<com.olx.scraper.api.CategoriesResponse> {
            override fun onResponse(
                call: Call<com.olx.scraper.api.CategoriesResponse>,
                response: Response<com.olx.scraper.api.CategoriesResponse>
            ) {
                loadingCategories = false
                if (response.isSuccessful) {
                    categories = response.body()?.results ?: emptyList()
                } else {
                    categories = emptyList()
                    errorText = "Nie udało się pobrać kategorii (HTTP ${response.code()})"
                }
            }

            override fun onFailure(
                call: Call<com.olx.scraper.api.CategoriesResponse>,
                t: Throwable
            ) {
                loadingCategories = false
                categories = emptyList()
                errorText = "Błąd sieci: ${t.message}"
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Kategorie", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Wybierz marketplace", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (loadingMarketplaces) {
            CircularProgressIndicator()
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(marketplaces) { marketplace ->
                    val selected = marketplace.key == selectedMarketplaceKey
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMarketplaceKey = marketplace.key
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = if (selected) CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) else CardDefaults.outlinedCardColors()
                    ) {
                        Text(
                            text = marketplace.name,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Wybierz kategorię", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (loadingCategories) {
            CircularProgressIndicator()
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val selected = category.label == selectedCategoryLabel
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategoryLabel = category.label },
                        shape = RoundedCornerShape(12.dp),
                        colors = if (selected) CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) else CardDefaults.outlinedCardColors()
                    ) {
                        Text(
                            text = category.label,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Szukaj w kategorii: ${
                        selectedCategoryLabel ?: "brak"
                    }"
                )
            }
        )

        if (errorText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText ?: "",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
