# Phase 5.2 — API Provider Connectivity Fix (2026-05-02)

## ปัญหาที่รายงาน
1. **Gemini**: กด Test เชื่อมต่อได้ แต่ไม่แสดงรายการโมเดล
2. **OpenRouter**: แสดงรายการโมเดล แต่พิมพ์คุยแล้วเงียบ (ทั้งโมเดลฟรีและเสียเงิน)

## Root Cause Analysis

### 1. Gemini ไม่แสดงรายการโมเดล
- `GeminiLlmProvider.listModels(apiKey)` **ไม่ใช้ parameter `apiKey`** → delegate ไปที่ `geminiService.listModels()` ซึ่งใช้ internal key ที่อาจยังว่างหรือเก่า
- Settings UI เรียก `getModelsForProvider()` **ก่อนกด Save** → GeminiService ยังไม่ได้ update key → return empty

### 2. OpenRouter พิมพ์คุยแล้วเงียบ
- SSE Streaming ใช้ `client.post()` + byte-by-byte parsing ที่มีปัญหากับ Ktor
- ต้องใช้ `client.preparePost().execute()` + `readUTF8Line()` เพื่อ parse SSE อย่างถูกต้อง
- Tool calls จาก OpenRouter ไม่ถูก parse → response ค้าง

### 3. Provider ไม่ถูก register ใน Registry
- เมื่อ user ใส่ key ใหม่ใน Settings แต่ยังไม่กด Save → `LlmProviderRegistry` ไม่มี provider registered → `listModels()` return empty ทันที

## การแก้ไข

### ไฟล์ที่แก้ไข

#### 1. `GeminiLlmProvider.kt`
- เพิ่ม `HttpClient` ใน constructor
- `listModels()` เปลี่ยนจาก delegate ผ่าน GeminiService → **REST call ตรงโดยใช้ apiKey parameter**
- เพิ่ม imports: `io.ktor.client.*`, `io.ktor.client.call.*`, `io.ktor.client.request.*`

#### 2. `OpenRouterLlmProvider.kt`
- SSE streaming: เปลี่ยนจาก `client.post()` + byte-by-byte → `client.preparePost().execute()` + `readUTF8Line()`
- เพิ่ม Tool Call parsing จาก `delta.tool_calls` → `LlmToolCall`

#### 3. `OpenAILlmProvider.kt`
- SSE streaming: เปลี่ยนจาก byte-by-byte → `preparePost` + `readUTF8Line()`
- เพิ่ม Tool Call parsing เหมือน OpenRouter

#### 4. `ClaudeLlmProvider.kt`
- SSE streaming: เปลี่ยนจาก byte-by-byte → `preparePost` + `readUTF8Line()`

#### 5. `LiteLlmProvider.kt`
- SSE streaming: เปลี่ยนจาก byte-by-byte → `preparePost` + `readUTF8Line()`

#### 6. `LlmProviderRegistry.kt`
- `listModels()`: เพิ่ม fallback สร้าง **temporary provider** เมื่อ provider ยังไม่ถูก register
- เพิ่ม `createTempProvider()` สำหรับ OpenAI, Claude, OpenRouter, LiteLLM

#### 7. `JarvisOrchestrator.kt`
- `listModelsForProvider()`: เพิ่ม `apiKeyOverride` parameter
- Constructor: pass `client` ไปที่ `GeminiLlmProvider`

#### 8. `JarvisViewModel.kt`
- `getModelsForProvider()`: เพิ่ม `apiKeyOverride` + resolve key จาก StateFlow ตาม provider
- `getLiveCapableModels()`: เพิ่ม `apiKeyOverride`

#### 9. `SettingsDialog.kt`
- `LaunchedEffect`: ส่ง key ที่ user กำลังพิมพ์ใน Settings (ยังไม่ Save) เป็น `keyOverride`
- `onTest`: เมื่อ test สำเร็จ → auto-refresh model list ด้วย key ที่ทดสอบ

## สรุปรูปแบบ SSE ที่ถูกต้อง (ทุก Provider)
```kotlin
// ❌ ผิด — byte-by-byte parsing ค้างกับ Ktor
val response = client.post(url) { ... }
val channel: ByteReadChannel = response.body()
while (!channel.isClosedForRead) {
    val byte = channel.readByte()
    ...
}

// ✅ ถูก — preparePost + readUTF8Line
client.preparePost(url) { ... }.execute { response ->
    val channel = response.bodyAsChannel()
    while (!channel.isClosedForRead) {
        val line = channel.readUTF8Line()?.trim() ?: break
        if (line.startsWith("data: ") && line != "data: [DONE]") {
            // parse JSON
        }
    }
}
```

## Status: ✅ แก้ไขเสร็จ — รอ Build ยืนยัน
