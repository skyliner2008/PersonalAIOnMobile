package com.example.personalaibot.automation

/**
 * ปลุก background automation service ให้กลับมาทำงาน
 * เรียกทุกครั้งที่มีการสร้าง/เปิด/แก้ไข alert หรือ scheduled task
 * เพราะ JarvisAutomationService จะ stopSelf() เองเมื่อไม่มีงาน active ติดกัน 5 รอบ
 * ถ้าไม่ปลุกกลับ alert ที่เพิ่งสร้างจะไม่ถูกเช็คจนกว่าผู้ใช้จะเปิดแอปใหม่
 * (Android เท่านั้น — platform อื่นเป็น no-op)
 */
expect fun wakeupAutomationService()
