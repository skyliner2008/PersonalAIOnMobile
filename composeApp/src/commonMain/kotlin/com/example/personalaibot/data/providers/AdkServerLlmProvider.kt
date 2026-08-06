package com.example.personalaibot.data.providers

import com.example.personalaibot.logError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

class AdkServerLlmProvider(
    private val client: HttpClient,
    private val serverBaseUrl: String,
    private val clientToken: String,
) : LlmProvider {

    override val providerId: String = "adk_vertex"
    override val displayName: String = "Vertex AI ADK (Experimental)"
    override val supportsStreaming: Boolean = false
    override val supportsFunctionCalling: Boolean = true

    private fun getFallbackModels(): List<LlmModelInfo> {
        return listOf(
            LlmModelInfo("gemini-3.1-flash-live-preview", "Gemini 3.1 Flash Live (ADK Primary)", supportsVision = true, supportsLive = true),
            LlmModelInfo("gemini-2.5-flash-native-audio-preview-09-2025", "Gemini 2.5 Flash Native Audio (ADK Secondary)", supportsVision = true, supportsLive = true),
            LlmModelInfo("gemini-3.5-live-translate-preview", "Gemini 3.5 Live Translate (ADK)", supportsVision = true, supportsLive = true),
            LlmModelInfo("gemini-2.0-flash-exp", "Gemini 2.0 Flash (ADK)", supportsVision = true, supportsLive = false),
            LlmModelInfo("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite (ADK)", supportsVision = true, supportsLive = false)
        )
    }

    override suspend fun listModels(apiKey: String): List<LlmModelInfo> {
        val url = "${serverBaseUrl.trimEnd('/')}/api/adk/models"
        return try {
            val response = client.get(url) {
                if (clientToken.isNotEmpty()) {
                    header("x-client-token", clientToken)
                }
            }
            if (!response.status.isSuccess()) return getFallbackModels()
            
            val responseBody = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true }
            val jsonObject = json.parseToJsonElement(responseBody).jsonObject
            val modelsArray = jsonObject["models"]?.jsonArray ?: return getFallbackModels()
            
            val list = modelsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id
                val supportsVision = obj["supportsVision"]?.jsonPrimitive?.booleanOrNull ?: true
                val supportsLive = obj["supportsLive"]?.jsonPrimitive?.booleanOrNull ?: false
                LlmModelInfo(
                    id = id,
                    displayName = name,
                    supportsVision = supportsVision,
                    supportsLive = supportsLive
                )
            }
            if (list.isEmpty()) getFallbackModels() else list
        } catch (e: Exception) {
            logError("AdkServerLlmProvider", "listModels error, using fallback", e)
            getFallbackModels()
        }
    }

    override suspend fun generate(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): LlmResult {
        val url = "${serverBaseUrl.trimEnd('/')}/api/adk/chat"
        val modelName = options.model ?: "gemini-2.0-flash-exp"

        val requestBody = buildJsonObject {
            put("model", modelName.substringAfterLast("/"))
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == "assistant") "model" else msg.role)
                        
                        // Handle tool role (Function Response)
                        if (msg.role == "tool") {
                            putJsonArray("parts") {
                                addJsonObject {
                                    putJsonObject("functionResponse") {
                                        put("name", msg.toolCallId ?: "")
                                        putJsonObject("response") {
                                            put("result", msg.content)
                                        }
                                    }
                                }
                            }
                        } else {
                            putJsonArray("parts") {
                                if (msg.content.isNotEmpty()) {
                                    addJsonObject {
                                        put("text", msg.content)
                                    }
                                }
                                if (!msg.toolCalls.isNullOrEmpty()) {
                                    msg.toolCalls.forEach { tc ->
                                        addJsonObject {
                                            putJsonObject("functionCall") {
                                                put("name", tc.name)
                                                val argsObj = try {
                                                    Json.parseToJsonElement(tc.arguments).jsonObject
                                                } catch (_: Exception) {
                                                    buildJsonObject {}
                                                }
                                                put("args", argsObj)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (options.systemPrompt != null) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", options.systemPrompt)
                        }
                    }
                }
            }
            if (!options.tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    options.tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            val paramsObj = try {
                                Json.parseToJsonElement(t.parametersJson).jsonObject
                            } catch (_: Exception) {
                                buildJsonObject {}
                            }
                            put("parameters", paramsObj)
                        }
                    }
                }
            }
        }

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                if (clientToken.isNotEmpty()) {
                    header("x-client-token", clientToken)
                }
                setBody(requestBody)
            }

            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw Exception("ADK HTTP ${response.status}: $responseBody")
            }

            val jsonElement = Json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject
            
            if (jsonObject.containsKey("error")) {
                throw Exception("ADK Server Error: ${jsonObject["error"]?.jsonPrimitive?.content}")
            }

            val candidates = jsonObject["candidates"]?.jsonArray
            val firstCandidate = candidates?.getOrNull(0)?.jsonObject
            val contentObj = firstCandidate?.get("content")?.jsonObject
            val partsArray = contentObj?.get("parts")?.jsonArray

            var text = ""
            val toolCallsList = mutableListOf<LlmToolCall>()

            partsArray?.forEach { partElement ->
                val partObj = partElement.jsonObject
                if (partObj.containsKey("text")) {
                    text += partObj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                }
                if (partObj.containsKey("functionCall")) {
                    val fc = partObj["functionCall"]?.jsonObject
                    val name = fc?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                    val argsObj = fc?.get("args")?.jsonObject
                    val argsJson = argsObj?.toString() ?: "{}"
                    val id = fc?.get("id")?.jsonPrimitive?.contentOrNull ?: "call_${kotlin.random.Random.nextInt(100000)}"
                    toolCallsList.add(LlmToolCall(id = id, name = name, arguments = argsJson))
                }
            }

            return LlmResult(
                text = text,
                toolCalls = if (toolCallsList.isEmpty()) null else toolCallsList,
                modelUsed = modelName
            )
        } catch (e: Exception) {
            logError("AdkServerLlmProvider", "generate error", e)
            return LlmResult(text = "⚠️ Error: ${e.message}", modelUsed = modelName)
        }
    }

    override fun generateStream(
        messages: List<LlmMessage>,
        options: LlmOptions
    ): Flow<LlmChunk> = flow {
        val result = generate(messages, options)
        emit(LlmChunk(text = result.text, toolCalls = result.toolCalls))
    }

    override suspend fun isAvailable(): Boolean = true
}
