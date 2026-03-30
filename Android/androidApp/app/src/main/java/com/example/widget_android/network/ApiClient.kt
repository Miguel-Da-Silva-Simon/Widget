package com.example.widget_android.network

import android.content.Context
import android.os.Build
import com.example.widget_android.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    fun service(context: Context): ClockingApiService {
        val app = context.applicationContext
        return retrofit(app).create(ClockingApiService::class.java)
    }

    fun effectiveBaseUrl(context: Context): String {
        val configured = BuildConfig.API_BASE_URL
        val isEmulator = isProbablyEmulator()
        // Móvil físico con variante "emulator" (10.0.2.2): usar localhost + adb reverse.
        if (!isEmulator && configured.contains("10.0.2.2")) {
            return "http://127.0.0.1:8080/"
        }
        // Emulador con variante "device" (127.0.0.1): 127.0.0.1 en el AVD no es el PC; el host es 10.0.2.2.
        if (isEmulator && (configured.contains("127.0.0.1") || configured.contains("localhost"))) {
            return "http://10.0.2.2:8080/"
        }
        return configured
    }

    private fun retrofit(appContext: Context): Retrofit {
        val baseUrl = effectiveBaseUrl(appContext)
        val existing = retrofit
        val cached = cachedBaseUrl
        if (existing != null && cached == baseUrl) return existing

        return synchronized(this) {
            val current = retrofit
            val currentBase = cachedBaseUrl
            if (current != null && currentBase == baseUrl) {
                current
            } else {
                buildRetrofit(baseUrl, appContext).also {
                    retrofit = it
                    cachedBaseUrl = baseUrl
                }
            }
        }
    }

    private fun buildRetrofit(baseUrl: String, appContext: Context): Retrofit {
        val app = appContext.applicationContext
        val client =
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor())
                .authenticator(TokenRefreshAuthenticator(app, baseUrl))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()

        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk")
    }
}
