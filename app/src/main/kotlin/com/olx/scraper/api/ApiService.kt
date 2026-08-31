package com.olx.scraper.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @GET("api/listings")
    fun getListings(): Call<ListingsResponse>

    @GET("api/listings/{id}")
    fun getListing(@Path("id") id: Int): Call<Listing>

    @GET("api/search")
    fun search(
        @Query("q") query: String,
        @Query("category") category: String? = null,
        @Query("marketplace") marketplace: String? = null
    ): Call<ListingsResponse>

    @GET("api/health")
    fun health(): Call<HealthResponse>

    @GET("api/marketplaces")
    fun getMarketplaces(): Call<MarketplacesResponse>

    @GET("api/categories")
    fun getCategories(@Query("marketplace") marketplace: String): Call<CategoriesResponse>

    @POST("api/auth/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    @POST("api/auth/register")
    fun register(@Body request: RegisterRequest): Call<AuthResponse>
}

data class ListingsResponse(
    val count: Int,
    val results: List<Listing>
)

data class HealthResponse(
    val status: String,
    val service: String? = null,
    val timestamp: String? = null
)

data class Listing(
    val id: Int,
    val title: String,
    val price: Int,
    val category: String,
    val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class Marketplace(
    val key: String,
    val name: String
)

data class MarketplacesResponse(
    val count: Int,
    val results: List<Marketplace>
)

data class Category(
    val key: String,
    val label: String,
    val path: String
)

data class CategoriesResponse(
    val count: Int,
    val results: List<Category>
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String,
    val user: UserData?
)

data class UserData(
    val id: Int?,
    val email: String?
)
