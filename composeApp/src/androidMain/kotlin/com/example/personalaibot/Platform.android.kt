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
            // บังคับ HTTP/1.1 — CDN ของ FRED (fred.stlouisfed.org) reset HTTP/2 stream
            // ("stream was reset: INTERNAL_ERROR") ทำให้ดึงข้อมูลเศรษฐกิจไม่ได้
            // endpoint อื่นทำงานบน HTTP/1.1 ได้ปกติ (WebSocket ใช้ HTTP/1.1 upgrade อยู่แล้ว)
            protocols(listOf(okhttp3.Protocol.HTTP_1_1))
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
