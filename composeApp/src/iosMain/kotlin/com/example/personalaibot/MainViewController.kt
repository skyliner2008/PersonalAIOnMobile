package com.example.personalaibot

import androidx.compose.ui.window.ComposeUIViewController
import com.example.personalaibot.db.DatabaseDriverFactory
import com.example.personalaibot.voice.VoiceManager

fun MainViewController() = ComposeUIViewController {
    val driverFactory = DatabaseDriverFactory()
    val voiceManager = VoiceManager()
    App(driverFactory, voiceManager)
}
