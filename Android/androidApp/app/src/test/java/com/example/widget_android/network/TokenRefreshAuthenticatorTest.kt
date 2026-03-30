package com.example.widget_android.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenRefreshAuthenticatorTest {

    @Test
    fun `buildRetryRequest replaces authorization with the refreshed token`() {
        val original = Request.Builder()
            .url("https://example.com/clockings/today")
            .header("Authorization", "Bearer old-token")
            .build()

        val retried = TokenRefreshAuthenticator.buildRetryRequest(original, "new-token")

        assertEquals("Bearer new-token", retried.header("Authorization"))
        assertEquals("1", retried.header(TokenRefreshAuthenticator.HEADER_AUTH_RETRY))
    }
}
