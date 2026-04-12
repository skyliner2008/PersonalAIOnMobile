package com.example.personalaibot.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Gemini Function Calling Schema ──────────────────────────────────────────

@Serializable
data class GeminiTool(
    val functionDeclarations: List<FunctionDeclaration>
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: FunctionParameters? = null
)

@Serializable
data class FunctionParameters(
    val type: String,  // Must always be explicit (e.g. "OBJECT") — no default to ensure serialization
    val properties: Map<String, ParameterProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

@Serializable
data class ParameterProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

// ─── Tool Execution Contract ──────────────────────────────────────────────────

data class ToolCall(
    val name: String,
    val args: Map<String, String>
)

data class ToolResult(
    val toolName: String,
    val result: String,
    val isError: Boolean = false
)

// ─── Skill / Plugin Descriptor ───────────────────────────────────────────────

/**
 * Descriptor สำหรับ skill ที่สร้างโดย user หรือ plugin
 * ทุก skill มี unique name + คำอธิบาย + system prompt ของตัวเอง
 */
data class SkillDescriptor(
    val name: String,
    val description: String,
    val systemPromptAddon: String,
    val triggerKeywords: List<String> = emptyList(),
    val author: String = "user",
    val version: String = "1.0"
)
