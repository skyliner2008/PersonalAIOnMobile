package com.skyliner2008.jarvis

import androidx.compose.ui.window.ComposeUIViewController
import com.skyliner2008.jarvis.db.DatabaseDriverFactory
import com.skyliner2008.jarvis.voice.VoiceManager

fun MainViewController() = ComposeUIViewController {
    val driverFactory = DatabaseDriverFactory()
    val voiceManager = VoiceManager()
    App(driverFactory, voiceManager)
}
