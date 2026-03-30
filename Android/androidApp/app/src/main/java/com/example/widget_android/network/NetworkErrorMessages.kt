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
            "No se encuentra el servidor. En movil fisico no uses 10.0.2.2: pon la IP de tu PC en build.gradle (API_BASE_URL)."
        is ConnectException ->
            "No se pudo conectar. En la carpeta backend: docker compose up (puerto 8080). " +
                "Móvil USB: variante deviceDebug y ejecuta reverse-8080.bat (adb reverse tcp:8080 tcp:8080)."
        is SocketTimeoutException ->
            "Tiempo de espera agotado: el móvil no recibe respuesta del PC en el puerto 8080. " +
                "Opciones: (1) En el PC: docker compose up en la carpeta backend. " +
                "(2) USB: adb reverse tcp:8080 tcp:8080 y variante deviceDebug. " +
                "(3) Misma WiFi sin USB: en androidApp/gradle.properties pon dev.api.base.url=http://IP_DE_TU_PC:8080/ " +
                "(IP con ipconfig / ifconfig en el PC)."
        else ->
            message?.takeIf { it.isNotBlank() }
                ?: "Error: ${this::class.simpleName}"
    }

private fun HttpException.messageFor401(serverMessage: String?): String {
    val path = response()?.raw()?.request?.url?.encodedPath.orEmpty()
    val isLogin = path.endsWith("/auth/login")
    return if (isLogin) {
        serverMessage ?: "Email o contrasena incorrectos"
    } else {
        "Sesión no válida o caducada. Cierra sesión y vuelve a entrar."
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
