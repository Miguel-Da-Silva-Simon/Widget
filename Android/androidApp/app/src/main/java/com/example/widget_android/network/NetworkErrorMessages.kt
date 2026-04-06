package com.example.widget_android.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject
import retrofit2.HttpException

fun Throwable.toUserMessage(): String =
    when (this) {
        is HttpException -> {
            val serverMessage = serverMessage()
            when (code()) {
                401 -> messageFor401(serverMessage)
                in 400..499 -> serverMessage ?: "Error del servidor (${code()})"
                in 500..599 -> serverMessage ?: "Error del servidor (${code()})"
                else -> serverMessage ?: "Error del servidor (${code()})"
            }
        }
        is UnknownHostException ->
            "No se puede conectar al servidor. Comprueba tu conexión a internet."
        is ConnectException ->
            "No se pudo conectar al servidor. Comprueba que el servidor esté en marcha y tu conexión."
        is SocketTimeoutException ->
            "El servidor no responde. Comprueba tu conexión e inténtalo de nuevo."
        else ->
            message?.takeIf { it.isNotBlank() }
                ?: "Error inesperado. Inténtalo de nuevo."
    }

private fun HttpException.messageFor401(serverMessage: String?): String {
    val path = response()?.raw()?.request?.url?.encodedPath.orEmpty()
    val isLogin = path.endsWith("/auth/login")
    return if (isLogin) {
        serverMessage ?: "Email o contraseña incorrectos"
    } else {
        "Error de sesión. Pulsa reintentar."
    }
}

private fun HttpException.serverMessage(): String? {
    val body = response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val json = JSONObject(body)
        json.optString("message")
            .takeIf { it.isNotBlank() }
            ?: json.optString("error").takeIf { it.isNotBlank() }
    }.getOrNull()
}
