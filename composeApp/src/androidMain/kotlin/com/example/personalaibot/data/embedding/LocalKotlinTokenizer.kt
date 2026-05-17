package com.example.personalaibot.data.embedding

import kotlinx.serialization.json.*
import java.io.File

/**
 * A pure Kotlin implementation of a HuggingFace-compatible BPE Tokenizer.
 * This avoids the need for native JNI libraries on Android.
 */
class LocalKotlinTokenizer(tokenizerJsonPath: String) {
    private val vocab: Map<String, Int>
    private val merges: List<Pair<String, String>>
    private val addedTokens: Map<String, Int>
    
    init {
        val json = Json { ignoreUnknownKeys = true }
        val content = File(tokenizerJsonPath).readText()
        val root = json.parseToJsonElement(content).jsonObject
        
        // Parse Vocab
        val modelNode = root["model"]?.jsonObject ?: throw Exception("Invalid tokenizer.json: missing 'model'")
        val vocabElement = modelNode["vocab"] ?: throw Exception("Invalid tokenizer.json: missing 'vocab'")
        
        vocab = when (vocabElement) {
            is JsonObject -> vocabElement.mapValues { it.value.jsonPrimitive.int }
            is JsonArray -> {
                // Handle vocab as array of arrays [["token", id], ...] or just ["token", ...]
                vocabElement.mapIndexed { index, element ->
                    when (element) {
                        is JsonArray -> element[0].jsonPrimitive.content to (element[1].jsonPrimitive.intOrNull ?: index)
                        is JsonPrimitive -> element.content to index
                        else -> "" to index
                    }
                }.toMap()
            }
            else -> throw Exception("Unsupported vocab format")
        }
        
        // Parse Merges
        val mergesNode = modelNode["merges"]?.jsonArray ?: JsonArray(emptyList())
        merges = mergesNode.mapNotNull { 
            when (it) {
                is JsonPrimitive -> {
                    val parts = it.content.split(" ")
                    if (parts.size >= 2) Pair(parts[0], parts[1]) else null
                }
                is JsonArray -> {
                    if (it.size >= 2) Pair(it[0].jsonPrimitive.content, it[1].jsonPrimitive.content) else null
                }
                else -> null
            }
        }
        
        // Parse Added Tokens
        val addedTokensNode = root["added_tokens"] ?: JsonArray(emptyList())
        addedTokens = when (addedTokensNode) {
            is JsonArray -> {
                addedTokensNode.associate { 
                    val obj = it.jsonObject
                    obj["content"]!!.jsonPrimitive.content to obj["id"]!!.jsonPrimitive.int
                }
            }
            is JsonObject -> {
                addedTokensNode.mapValues { it.value.jsonPrimitive.int }
            }
            else -> emptyMap()
        }
    }

    fun encode(text: String): List<Long> {
        // XLMRoberta style normalization (MiniLM Multilingual)
        // 1. Initial split into characters (treating spaces as special characters)
        var normalized = text.replace(" ", "\u2581")
        if (!normalized.startsWith("\u2581")) {
            normalized = "\u2581" + normalized
        }
        
        // Split into characters
        var tokens = normalized.map { it.toString() }.toMutableList()
        
        // 2. Iteratively apply merges
        for (merge in merges) {
            val (p1, p2) = merge
            var i = 0
            val nextTokens = mutableListOf<String>()
            while (i < tokens.size) {
                if (i < tokens.size - 1 && tokens[i] == p1 && tokens[i+1] == p2) {
                    nextTokens.add(p1 + p2)
                    i += 2
                } else {
                    nextTokens.add(tokens[i])
                    i += 1
                }
            }
            tokens = nextTokens
        }
        
        // 3. Map to IDs and add special tokens
        val encoded = mutableListOf<Long>()
        
        // Add CLS/BOS (<s> for XLM-R / MiniLM Multilingual)
        val bosId = addedTokens["<s>"]?.toLong() ?: vocab["<s>"]?.toLong() ?: 0L
        encoded.add(bosId)
        
        tokens.forEach { 
            val id = vocab[it]?.toLong() ?: addedTokens[it]?.toLong() ?: vocab["<unk>"]?.toLong() ?: 3L
            encoded.add(id)
        }
        
        // Add SEP/EOS (</s>)
        val eosId = addedTokens["</s>"]?.toLong() ?: vocab["</s>"]?.toLong() ?: 2L
        encoded.add(eosId)
        
        return encoded
    }
}
