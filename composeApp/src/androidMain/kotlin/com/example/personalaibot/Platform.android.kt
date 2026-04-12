package com.example.personalaibot

import android.os.Build
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.TimeUnit

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(60, TimeUnit.SECONDS)
            readTimeout(90, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 90_000
    }
    install(WebSockets) {
        pingIntervalMillis = 20_000
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }
}
