package com.example.personalaibot

import io.ktor.client.*

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun createHttpClient(): HttpClient
