package com.example.personalaibot.automation

/** iOS ไม่มี background automation service — no-op */
actual fun wakeupAutomationService() {}

/** iOS ไม่มี service สำหรับ announce — no-op */
actual fun announceLongTaskCompletion(
    title: String, cardBody: String, metaJson: String, shortSpeech: String, fullSpeech: String
) {}
