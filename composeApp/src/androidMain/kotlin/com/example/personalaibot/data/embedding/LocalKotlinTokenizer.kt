package com.example.personalaibot.data.embedding

import kotlinx.serialization.json.*
import java.io.File

/**
 * A pure Kotlin HuggingFace tokenizer รองรับทั้ง 2 ประเภท:
 *
 * 1) **Unigram (SentencePiece)** — ใช้ Viterbi best-path segmentation
 *    (paraphrase-multilingual-MiniLM-L12-v2 ใช้ประเภทนี้ — XLM-R style)
 * 2) **BPE + merges** — greedy merge ตามลำดับ merges
 *
 * เดิมรองรับแค่ BPE — เมื่อโหลด tokenizer ของ MiniLM Multilingual (Unigram)
 * merges จะว่างเปล่า ทำให้ทุกตัวอักษรกลายเป็น token เดี่ยว char-level
 * embedding quality พังโดยเฉพาะภาษาไทย (บั๊กที่แก้ในรอบนี้)
 */
class LocalKotlinTokenizer(tokenizerJsonPath: String) {

    private data class UnigramEntry(val token: String, val id: Int, val score: Float)

    private val isUnigram: Boolean
    private val vocab: Map<String, Int>
    private val merges: List<Pair<String, String>>
    private val addedTokens: Map<String, Int>

    /** Unigram: token จัดกลุ่มตามตัวอักษรตัวแรก เพื่อเร็วตอน Viterbi */
    private val unigramByFirstChar: Map<Char, List<UnigramEntry>>
    private val unkScore: Float

    init {
        val json = Json { ignoreUnknownKeys = true }
        val content = File(tokenizerJsonPath).readText()
        val root = json.parseToJsonElement(content).jsonObject

        val modelNode = root["model"]?.jsonObject ?: throw Exception("Invalid tokenizer.json: missing 'model'")
        val modelType = modelNode["type"]?.jsonPrimitive?.contentOrNull ?: "BPE"
        isUnigram = modelType.equals("Unigram", ignoreCase = true)
        val vocabElement = modelNode["vocab"] ?: throw Exception("Invalid tokenizer.json: missing 'vocab'")

        if (isUnigram) {
            // Unigram vocab = array of [token, score]; id คือ index ใน array
            val entries = (vocabElement as? JsonArray)?.mapIndexedNotNull { index, el ->
                val arr = el as? JsonArray ?: return@mapIndexedNotNull null
                val token = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@mapIndexedNotNull null
                val score = arr.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: 0f
                UnigramEntry(token, index, score)
            } ?: throw Exception("Invalid tokenizer.json: Unigram vocab must be an array")

            vocab = entries.associate { it.token to it.id }
            unigramByFirstChar = entries
                .filter { it.token.isNotEmpty() }
                .groupBy { it.token[0] }
                .mapValues { (_, list) -> list.sortedByDescending { it.token.length } }
            unkScore = (entries.minOfOrNull { it.score } ?: -10f) - 10f
            merges = emptyList()
        } else {
            vocab = when (vocabElement) {
                is JsonObject -> vocabElement.mapValues { it.value.jsonPrimitive.int }
                is JsonArray -> {
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
            unigramByFirstChar = emptyMap()
            unkScore = 0f

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
            is JsonObject -> addedTokensNode.mapValues { it.value.jsonPrimitive.int }
            else -> emptyMap()
        }
    }

    fun encode(text: String): List<Long> {
        // XLM-R / SentencePiece style: space → ▁ และเติม ▁ นำหน้า
        var normalized = text.replace(" ", "▁")
        if (!normalized.startsWith("▁")) {
            normalized = "▁$normalized"
        }

        val pieces: List<String?> = if (isUnigram) segmentUnigram(normalized)
                                    else segmentBpe(normalized)

        val encoded = mutableListOf<Long>()
        // BOS (<s>)
        encoded.add(addedTokens["<s>"]?.toLong() ?: vocab["<s>"]?.toLong() ?: 0L)
        val unkId = addedTokens["<unk>"]?.toLong() ?: vocab["<unk>"]?.toLong() ?: 3L
        pieces.forEach { piece ->
            encoded.add(if (piece == null) unkId else vocab[piece]?.toLong() ?: addedTokens[piece]?.toLong() ?: unkId)
        }
        // EOS (</s>)
        encoded.add(addedTokens["</s>"]?.toLong() ?: vocab["</s>"]?.toLong() ?: 2L)
        return encoded
    }

    // ─── Unigram: Viterbi best-path (max sum of scores) ─────────────────────

    private fun segmentUnigram(normalized: String): List<String?> {
        val n = normalized.length
        if (n == 0) return emptyList()

        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val prev = IntArray(n + 1) { -1 }
        val tokAt = arrayOfNulls<String>(n + 1)
        best[0] = 0.0

        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            val candidates = unigramByFirstChar[normalized[i]].orEmpty()
            var matched = false
            for (e in candidates) {
                val j = i + e.token.length
                if (j <= n && normalized.startsWith(e.token, i)) {
                    val score = best[i] + e.score
                    if (score > best[j]) {
                        best[j] = score
                        prev[j] = i
                        tokAt[j] = e.token
                    }
                    matched = true
                }
            }
            if (!matched) {
                // fallback: char นี้ไม่มีใน vocab → unk ทีละตัว
                val j = i + 1
                val score = best[i] + unkScore
                if (score > best[j]) {
                    best[j] = score
                    prev[j] = i
                    tokAt[j] = null
                }
            }
        }

        val pieces = ArrayDeque<String?>()
        var j = n
        while (j > 0) {
            val p = prev[j]
            if (p < 0) break
            pieces.addFirst(tokAt[j])
            j = p
        }
        return pieces.toList()
    }

    // ─── BPE: greedy merge ──────────────────────────────────────────────────

    private fun segmentBpe(normalized: String): List<String?> {
        var tokens = normalized.map { it.toString() }.toMutableList()
        for (merge in merges) {
            val (p1, p2) = merge
            var i = 0
            val nextTokens = mutableListOf<String>()
            while (i < tokens.size) {
                if (i < tokens.size - 1 && tokens[i] == p1 && tokens[i + 1] == p2) {
                    nextTokens.add(p1 + p2)
                    i += 2
                } else {
                    nextTokens.add(tokens[i])
                    i += 1
                }
            }
            tokens = nextTokens
        }
        return tokens
    }
}
