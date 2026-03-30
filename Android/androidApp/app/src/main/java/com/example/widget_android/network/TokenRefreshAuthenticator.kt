package com.example.widget_android.network

import android.content.Context
import com.example.widget_android.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Tras un 401, intenta POST /auth/refresh con el mismo Bearer, guarda el nuevo token y reintenta la petición.
 * El backend acepta JWT recién caducados si la sesión en BD sigue activa (parse con skew).
 */
class TokenRefreshAuthenticator(
    private val appContext: Context,
    private val baseUrl: String
) : Authenticator {

    private val refreshRetrofit: Retrofit by lazy {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val refreshApi: AuthRefreshApi by lazy { refreshRetrofit.create(AuthRefreshApi::class.java) }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header(HEADER_AUTH_RETRY) != null) return null
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/login")) return null
        if (path.endsWith("/auth/refresh")) return null

        val auth = response.request.header("Authorization") ?: return null
        if (!auth.startsWith("Bearer ")) return null

        val retrofitResp =
            try {
                refreshApi.refresh(auth).execute()
            } catch (_: Exception) {
                return null
            }
        if (!retrofitResp.isSuccessful) return null
        val body = retrofitResp.body() ?: return null

        val session = SessionRepository(appContext)
        runBlocking(Dispatchers.IO) {
            session.saveSession(
                token = body.token,
                sessionId = body.sessionId,
                expiresAt = body.expiresAt,
                userName = body.user.name,
                userEmail = body.user.email
            )
        }

        return response.request.newBuilder()
            .removeHeader("Authorization")
            .header(HEADER_AUTH_RETRY, "1")
            .build()
    }

    companion object {
        private const val HEADER_AUTH_RETRY = "X-Auth-Retry"
    }
}
