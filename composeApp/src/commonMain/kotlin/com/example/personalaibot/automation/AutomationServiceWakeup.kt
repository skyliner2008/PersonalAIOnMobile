package com.example.personalaibot.automation

/**
 * ปลุก background automation service ให้กลับมาทำงาน
 * เรียกทุกครั้งที่มีการสร้าง/เปิด/แก้ไข alert หรือ scheduled task
 * เพราะ JarvisAutomationService จะ stopSelf() เองเมื่อไม่มีงาน active ติดกัน 5 รอบ
 * ถ้าไม่ปลุกกลับ alert ที่เพิ่งสร้างจะไม่ถูกเช็คจนกว่าผู้ใช้จะเปิดแอปใหม่
 * (Android เท่านั้น — platform อื่นเป็น no-op)
 */
expect fun wakeupAutomationService()

/**
 * สั่ง automation service ให้ "ประกาศผลงานพื้นหลังที่เสร็จแล้ว" แบบ one-shot —
 * notification + พูดสรุปผ่าน alert voice chain (Live → fallback) + การ์ดเข้าแชท
 * ใช้ตอนผู้ใช้ไม่ได้เปิด live ค้างไว้ (ถ้า live เปิดอยู่ VM จะส่งเข้า session แทน)
 * (Android เท่านั้น — platform อื่นเป็น no-op)
 */
expect fun announceLongTaskCompletion(
    title: String, cardBody: String, metaJson: String, shortSpeech: String, fullSpeech: String
)
