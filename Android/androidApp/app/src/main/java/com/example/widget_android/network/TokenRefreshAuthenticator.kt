package com.example.widget_android.network

import android.content.Context
import com.example.widget_android.data.SessionRepository
import com.example.widget_android.data.TokenHolder
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
 * Usa [refreshLock] para que solo un hilo refresque a la vez; los demás reutilizan el token ya renovado.
 */
class TokenRefreshAuthenticator(
    private val appContext: Context,
    private val baseUrl: String
) : Authenticator {

    private val refreshRetrofit: Retrofit by lazy {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
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

        val staleToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")?.trim()
        if (staleToken.isNullOrBlank()) return null

        synchronized(refreshLock) {
            val current = TokenHolder.token
            if (current != null && current != staleToken) {
                return buildRetryRequest(response.request, current)
            }

            val retrofitResp =
                try {
                    refreshApi.refresh("Bearer $staleToken").execute()
                } catch (_: Exception) {
                    return null
                }
            if (!retrofitResp.isSuccessful) return null
            val body = retrofitResp.body() ?: return null

            TokenHolder.token = body.token

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

            return buildRetryRequest(response.request, body.token)
        }
    }

    companion object {
        private val refreshLock = Any()
        internal const val HEADER_AUTH_RETRY = "X-Auth-Retry"

        internal fun buildRetryRequest(request: Request, refreshedToken: String): Request =
            request.newBuilder()
                .header("Authorization", "Bearer $refreshedToken")
                .header(HEADER_AUTH_RETRY, "1")
                .build()
    }
}
