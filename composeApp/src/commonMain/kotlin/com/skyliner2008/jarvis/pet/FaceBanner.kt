package com.skyliner2008.jarvis.pet

/**
 * The two lines at the bottom of Avatar mode (ผู้ใช้ 2026-10-09: "ให้แสดงชื่อ หรือ event ใบหน้าที่ตรวจจับได้" → "ตรวจจับใบหน้า
 * อยู่บริเวณด้านล่างสุด ชิดซ้าย, ตรวจจับมือ วัตถุ ให้อยู่ด้านล่าง ชิดขวา"):
 * - bottom left, [face]: who is in front of the camera and what their face does — "👤 บอส (72%) · 😊 ยิ้ม"
 * - bottom right, [sight]: hands and things — "✌️ ชูสองนิ้ว · ☕ แก้ว"
 */
object FaceBanner {
    /** events shown at most on each line */
    const val MAX_EVENTS = 2

    /**
     * @param recognizedSlot [PetModeController.currentRecognizedSlot] — -1 = no enrolled match
     * @param recognizedPerson [PetModeController.currentRecognizedPerson] — the name with its score, or the detector's
     *   "someone else" / "not enrolled" / "scan again" label
     * @param faceVisible a face is in front of the camera now
     * @param labels the detector's labels this frame ("บอส Smile 😊 91%", "บอส Face #3", "Hand ✌️ ชูสองนิ้ว (V Sign)" …)
     * @return null when no face is in view
     */
    fun face(recognizedSlot: Int, recognizedPerson: String?, faceVisible: Boolean, labels: List<String>): String? {
        if (!faceVisible) return null
        val who = when {
            recognizedSlot >= 0 && !recognizedPerson.isNullOrBlank() -> "👤 $recognizedPerson"
            recognizedPerson == null -> "👤 ตรวจพบใบหน้า"
            "บุคคลอื่น" in recognizedPerson -> "👤 บุคคลอื่น"
            "ยังไม่ลงทะเบียน" in recognizedPerson -> "👤 ใบหน้าใหม่ (ยังไม่ลงทะเบียน)"
            "สแกนใหม่" in recognizedPerson -> "👤 ต้องสแกนใบหน้าใหม่"
            else -> "👤 $recognizedPerson"
        }
        return (listOf(who) + faceEvents(labels)).joinToString(" · ")
    }

    /** hands first, then things; null when neither is in view */
    fun sight(labels: List<String>): String? =
        (handEvents(labels) + things(labels)).distinct().take(MAX_EVENTS).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /** what the face does, in Thai */
    fun faceEvents(labels: List<String>): List<String> = labels.mapNotNull { label ->
        when {
            "Wink" in label -> "😉 ขยิบตา"
            "Smile" in label -> "😊 ยิ้ม"
            else -> null
        }
    }.distinct().take(MAX_EVENTS)

    /** what the hands do, in Thai: a gesture, else the hand's pose */
    fun handEvents(labels: List<String>): List<String> = labels.mapNotNull { label ->
        val gesture = HandGesture.entries.firstOrNull { it != HandGesture.NONE && label.contains(it.displayNameTh) }
        when {
            gesture != null -> "${gesture.icon} ${gesture.displayNameTh.substringBefore(" (").trim()}"
            // "Hand ✊ กำมือ" (HandPose.poseLabel) shows as it is; a bare "Hand ✋" is a raised hand
            label.startsWith("Hand") -> label.removePrefix("Hand").trim().takeIf { ' ' in it } ?: "✋ ยกมือ"
            else -> null
        }
    }.distinct()

    /** the things in view, already in Thai ("Thing ☕ แก้ว" → "☕ แก้ว", [Things.label]) */
    fun things(labels: List<String>): List<String> =
        labels.filter { it.startsWith(Things.PREFIX) }.map { it.removePrefix(Things.PREFIX) }.distinct()

    /**
     * How high the robot's task pills reach from the bottom (pet_godot task_hud.gd: 28 px from the bottom, at most
     * 3 pills of ~52 px each 8 px apart, then a "+N งาน" line), in the stage's 720×1280 units — the lines sit above
     * them. 0 without tasks.
     */
    fun taskListHeightUnits(tasks: Int): Int = when {
        tasks <= 0 -> 0
        else -> 28 + minOf(tasks, 3) * 60 + if (tasks > 3) 39 else 0
    }
}
