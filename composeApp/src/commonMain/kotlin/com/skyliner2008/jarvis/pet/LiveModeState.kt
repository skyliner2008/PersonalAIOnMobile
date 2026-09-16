package com.skyliner2008.jarvis.pet

import kotlin.jvm.Volatile

/**
 * โหมดที่ Live session กำลังทำงานอยู่ — แหล่งความจริงเดียวสำหรับทั้งแอป
 *
 * ทุกโหมดคือผู้ช่วยตัวเดียวกัน ใช้เครื่องมือได้เหมือนกันหมด ต่างกันที่บทบาท/น้ำเสียง
 * และสิทธิ์ "ควบคุมเครื่องเต็มรูปแบบ" ที่เปิดเฉพาะโหมดขับรถ (2026-09-16)
 */
object LiveModeState {

    @Volatile
    var profile: AlwaysLiveProfile = AlwaysLiveProfile.CONTROL
        private set

    fun update(newProfile: AlwaysLiveProfile) {
        profile = newProfile
        com.skyliner2008.jarvis.ai.JarvisPersona.isPetMode = (newProfile == AlwaysLiveProfile.PET)
    }

    /** โหมดนี้สั่งแตะจอ/พิมพ์/เปิดแอป/ปลุก-พักจอ แทนผู้ใช้ได้หรือไม่ */
    val canControlDevice: Boolean
        get() = profile == AlwaysLiveProfile.DRIVE
}
