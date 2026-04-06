package com.example.widget_android.network

import com.example.widget_android.data.TokenHolder
import okhttp3.Interceptor
import okhttp3.Response

/** Añade Bearer desde [TokenHolder]. Excluye login para evitar enviar tokens antiguos/inválidos. */
class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (path.endsWith("/auth/login")) {
            return chain.proceed(chain.request())
        }

        val token = TokenHolder.token
        val request =
            if (!token.isNullOrBlank()) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
        return chain.proceed(request)
    }
}
