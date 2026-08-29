package com.olx.scraper.api

import com.olx.scraper.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "http://192.168.0.160:5000/"
    private const val AUTH_SCHEME = "Bearer"

    private var tokenManager: TokenManager? = null

    fun init(tm: TokenManager) {
        tokenManager = tm
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = tokenManager?.getToken()
        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "$AUTH_SCHEME $token")
                .build()
        } else {
            originalRequest
        }
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
