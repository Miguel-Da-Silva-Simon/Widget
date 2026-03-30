package com.example.widget_android.network

import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.POST

/** Cliente síncrono sin [TokenRefreshAuthenticator] para evitar bucles en POST /auth/refresh. */
interface AuthRefreshApi {
    @POST("auth/refresh")
    fun refresh(@Header("Authorization") authorization: String): Call<LoginResponseDto>
}
