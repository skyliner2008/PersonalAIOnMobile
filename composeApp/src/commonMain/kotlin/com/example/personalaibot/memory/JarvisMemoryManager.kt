package com.example.personalaibot.memory

import com.example.personalaibot.data.ConversationTurn
import com.example.personalaibot.db.JarvisDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JarvisMemoryManager — ระบบ Memory ครบทั้ง 4 Layer
 *
 * Layer 1: Core Memory       — ข้อมูล user profile (always in context)
 * Layer 2: Working Memory    — บทสนทนาล่าสุด (SQLite + StateFlow)
 * Layer 3: Archival Memory   — facts ระยะยาว + embedding vector
 * Layer 4: GraphRAG          — knowledge graph (nodes + edges)
 */
class JarvisMemoryManager(private val database: JarvisDatabase) {

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 2: Working Memory — บทสนทนา
    // ═══════════════════════════════════════════════════════════════════

    suspend fun storeMessage(role: String, content: String, metadata: String? = null) {
        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertMessage(
                role = role,
                content = content,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                metadata = metadata
            )
        }
    }

    suspend fun getRecentHistory(limit: Long = 50) = withContext(Dispatchers.IO) {
        database.jarvisDatabaseQueries.getRecentHistory(limit).executeAsList()
    }

    /**
     * ดึง conversation history สำหรับส่งเป็น context ให้ Gemini API
     * เรียงจากเก่าสุดไปใหม่สุด ไม่รวม message ล่าสุด (ที่กำลังส่ง)
     */
    suspend fun getConversationContext(maxTurns: Int = 10): List<ConversationTurn> =
        withContext(Dispatchers.IO) {
            val limit = (maxTurns * 2).toLong()
            val rows = database.jarvisDatabaseQueries
                .getRecentHistory(limit)
                .executeAsList()

            rows.drop(1)
                .reversed()
                .map { row -> ConversationTurn(role = row.role, content = row.content) }
        }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 1: Core Memory — ข้อมูล user ที่ Jarvis ต้องจำเสมอ
    // ═══════════════════════════════════════════════════════════════════

    /**
     * บันทึก/อัปเดต Core Memory fact
     * @param key   ชื่อ fact เช่น "user_name", "language", "occupation"
     * @param value ค่าของ fact
     */
    suspend fun setCoreMemory(key: String, value: String) {
        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.upsertCoreMemory(
                key = key,
                value_ = value,
                updated_at = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /** ดึง Core Memory ทั้งหมดในรูป Map */
    suspend fun getCoreMemoryMap(): Map<String, String> = withContext(Dispatchers.IO) {
        database.jarvisDatabaseQueries.getCoreMemory()
            .executeAsList()
            .associate { it.key to it.value_ }
    }

    /**
     * สร้าง core memory context string สำหรับใส่ใน system prompt
     * Format: "user_name: สมชาย\noccupation: Developer\n..."
     */
    suspend fun buildCoreMemoryContext(): String {
        val memory = getCoreMemoryMap()
        if (memory.isEmpty()) return ""

        return buildString {
            appendLine("=== ข้อมูลที่ JARVIS จำเกี่ยวกับผู้ใช้ ===")
            memory.entries.forEach { (key, value) ->
                val label = coreMemoryLabel(key)
                appendLine("$label: $value")
            }
        }.trimEnd()
    }

    private fun coreMemoryLabel(key: String): String = when (key) {
        "user_name"   -> "ชื่อผู้ใช้"
        "language"    -> "ภาษาที่ใช้"
        "occupation"  -> "อาชีพ"
        "interests"   -> "ความสนใจ"
        "location"    -> "ที่อยู่/เขตเวลา"
        "preferences" -> "ความชอบ"
        "goals"       -> "เป้าหมาย"
        else          -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    /**
     * วิเคราะห์บทสนทนาและสกัด Core Memory facts อัตโนมัติ
     * (heuristic-based extraction — Phase 3+ จะใช้ LLM extraction)
     */
    suspend fun extractAndUpdateCoreMemory(userMessage: String, aiResponse: String) {
        withContext(Dispatchers.IO) {
            val combined = "$userMessage $aiResponse".lowercase()

            // ตรวจจับชื่อผู้ใช้
            extractName(userMessage)?.let { setCoreMemory("user_name", it) }

            // ตรวจจับภาษาที่ใช้
            val langKey = detectLanguagePreference(userMessage)
            if (langKey != null) setCoreMemory("language", langKey)

            // ตรวจจับอาชีพ
            extractOccupation(combined)?.let { setCoreMemory("occupation", it) }

            // ตรวจจับ interests จาก keywords
            extractInterests(combined)?.let { interest ->
                val existing = database.jarvisDatabaseQueries
                    .getCoreMemoryByKey("interests")
                    .executeAsOneOrNull() ?: ""
                val updated = if (existing.contains(interest)) existing
                              else "$existing, $interest".trimStart(',', ' ')
                if (updated.isNotBlank()) setCoreMemory("interests", updated)
            }
        }
    }

    private fun extractName(text: String): String? {
        val patterns = listOf(
            Regex("(?:ชื่อ|ผม|ฉัน|หนู|เรา)[\\s]+([ก-๙a-zA-Z]{2,15})"),
            Regex("(?:my name is|i am|i'm|call me)\\s+([a-zA-Z]{2,20})", RegexOption.IGNORE_CASE),
            Regex("(?:เรียกฉันว่า|เรียกว่า)\\s+([ก-๙a-zA-Z]{2,15})")
        )
        return patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }
    }

    private fun detectLanguagePreference(text: String): String? {
        val thaiCount = text.count { it in 'ก'..'๙' }
        val engCount = text.count { it.isLetter() && it !in 'ก'..'๙' }
        return when {
            thaiCount > engCount * 2 -> "ภาษาไทย"
            engCount > thaiCount * 2 -> "English"
            else -> "ไทย/English (ผสม)"
        }
    }

    private fun extractOccupation(text: String): String? {
        val occupations = mapOf(
            "developer" to "Developer", "programmer" to "Programmer", "engineer" to "Engineer",
            "designer" to "Designer", "student" to "Student", "teacher" to "Teacher",
            "doctor" to "Doctor", "นักเรียน" to "นักเรียน", "นักศึกษา" to "นักศึกษา",
            "นักพัฒนา" to "นักพัฒนา", "วิศวกร" to "วิศวกร", "โปรแกรมเมอร์" to "Programmer",
            "ครู" to "ครู", "อาจารย์" to "อาจารย์", "หมอ" to "แพทย์"
        )
        return occupations.entries.firstOrNull { text.contains(it.key) }?.value
    }

    private fun extractInterests(text: String): String? {
        val interestMap = mapOf(
            "ai" to "AI/ML", "machine learning" to "AI/ML", "deep learning" to "AI/ML",
            "music" to "ดนตรี", "เพลง" to "ดนตรี", "game" to "เกม", "เกม" to "เกม",
            "travel" to "ท่องเที่ยว", "ท่องเที่ยว" to "ท่องเที่ยว",
            "cooking" to "การทำอาหาร", "ทำอาหาร" to "การทำอาหาร",
            "sport" to "กีฬา", "กีฬา" to "กีฬา", "reading" to "อ่านหนังสือ",
            "อ่านหนังสือ" to "อ่านหนังสือ", "photography" to "ถ่ายภาพ"
        )
        return interestMap.entries.firstOrNull { text.contains(it.key) }?.value
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 3: Archival Memory — ข้อมูลระยะยาว
    // ═══════════════════════════════════════════════════════════════════

    /**
     * บันทึก fact สำคัญลง Archival Memory
     * @param content      ข้อความ fact
     * @param sourceRole   "user" | "model" | "system"
     * @param importance   ระดับความสำคัญ 0.0-1.0
     */
    suspend fun archiveFact(
        content: String,
        sourceRole: String = "system",
        importance: Float = 0.5f
    ) {
        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertArchival(
                content = content,
                embedding_json = null,  // Phase 3+: Gemini embedding API
                source_role = sourceRole,
                importance = importance.toDouble(),
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /** ดึง archival facts ที่สำคัญที่สุด */
    suspend fun getTopArchivalFacts(limit: Long = 10): List<String> = withContext(Dispatchers.IO) {
        database.jarvisDatabaseQueries
            .getArchivalByImportance(limit)
            .executeAsList()
            .map { it.content }
    }

    /**
     * ค้นหาความจำที่เกี่ยวข้องจาก Archival Memory โดยใช้ Semantic Search (Vector Similarity)
     */
    suspend fun searchRelevantFacts(
        geminiService: com.example.personalaibot.data.GeminiService,
        query: String,
        limit: Int = 5,
        minSimilarity: Float = 0.65f
    ): List<String> = withContext(Dispatchers.IO) {
        val queryVector = geminiService.embedText(query, "RETRIEVAL_QUERY")
        if (queryVector.isEmpty()) {
            return@withContext getTopArchivalFacts(limit.toLong())
        }

        val allFacts = database.jarvisDatabaseQueries.getArchivalWithEmbeddings().executeAsList()
        if (allFacts.isEmpty()) return@withContext emptyList()

        // Rank by similarity
        val scoredFacts = allFacts.mapNotNull { row ->
            val vector = row.embedding_json?.let { decodeVector(it) } ?: return@mapNotNull null
            val score = cosineSimilarity(queryVector, vector)
            if (score >= minSimilarity) {
                row.content to score
            } else null
        }.sortedByDescending { it.second }
         .take(limit)

        scoredFacts.forEach { (content, score) ->
            // Update access count as a side effect
            // (In a real app, we'd find the ID)
        }

        scoredFacts.map { it.first }
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        var normV1 = 0f
        var normV2 = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normV1 += v1[i] * v1[i]
            normV2 += v2[i] * v2[i]
        }
        return if (normV1 > 0 && normV2 > 0) {
            dotProduct / (kotlin.math.sqrt(normV1) * kotlin.math.sqrt(normV2))
        } else 0f
    }

    private fun decodeVector(json: String): List<Float>? = try {
        Json.decodeFromString<List<Float>>(json)
    } catch (_: Exception) { null }

    private fun encodeVector(vector: List<Float>): String =
        Json.encodeToString(vector)

    /**
     * Updated archiveFact: บันทึกพร้อมสร้าง Embedding ทันที
     */
    suspend fun archiveFactWithEmbedding(
        geminiService: com.example.personalaibot.data.GeminiService,
        content: String,
        sourceRole: String = "system",
        importance: Float = 0.5f
    ) {
        val vector = geminiService.embedText(content, "RETRIEVAL_DOCUMENT")
        val vectorJson = if (vector.isNotEmpty()) encodeVector(vector) else null

        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.insertArchival(
                content = content,
                embedding_json = vectorJson,
                source_role = sourceRole,
                importance = importance.toDouble(),
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /**
     * ดึงข้อมูลเก่าที่ยังไม่มีเวกเตอร์ และทำ Backfill ให้ครบเพื่อให้ค้นหาแบบ Semantic ได้
     */
    suspend fun backfillEmbeddings(geminiService: com.example.personalaibot.data.GeminiService): Int = withContext(Dispatchers.IO) {
        val legacyFacts = database.jarvisDatabaseQueries.getArchivalByImportance(100).executeAsList()
            .filter { it.embedding_json == null }
        
        var count = 0
        legacyFacts.forEach { fact ->
            val vector = geminiService.embedText(fact.content, "RETRIEVAL_DOCUMENT")
            if (vector.isNotEmpty()) {
                val json = encodeVector(vector)
                // We need a query to update by ID
                database.jarvisDatabaseQueries.updateArchivalEmbedding(json, fact.id)
                count++
            }
        }
        count
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 4: GraphRAG — Knowledge Graph
    // ═══════════════════════════════════════════════════════════════════

    /**
     * เพิ่ม entity node ลง knowledge graph
     */
    suspend fun addKnowledgeNode(
        name: String,
        nodeType: String,
        properties: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        database.jarvisDatabaseQueries.insertKnowledgeNode(
            name = name,
            node_type = nodeType,
            properties = properties,
            created_at = now,
            updated_at = now
        )
        // ดึง id ของ node ที่เพิ่งสร้าง
        database.jarvisDatabaseQueries.getNodeByName(name)
            .executeAsOneOrNull()?.id ?: -1L
    }

    /**
     * เพิ่ม relation ระหว่าง entities
     */
    suspend fun addKnowledgeEdge(
        sourceName: String,
        targetName: String,
        relation: String,
        weight: Float = 1.0f
    ) {
        withContext(Dispatchers.IO) {
            val sourceId = database.jarvisDatabaseQueries.getNodeByName(sourceName)
                .executeAsOneOrNull()?.id ?: return@withContext
            val targetId = database.jarvisDatabaseQueries.getNodeByName(targetName)
                .executeAsOneOrNull()?.id ?: return@withContext

            database.jarvisDatabaseQueries.insertKnowledgeEdge(
                source_id = sourceId,
                target_id = targetId,
                relation = relation,
                weight = weight.toDouble(),
                created_at = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /**
     * GraphRAG: สกัด entities จากบทสนทนาและอัปเดต graph
     * (heuristic version — Phase 3+ จะใช้ Gemini NER)
     */
    suspend fun updateKnowledgeGraph(text: String) {
        withContext(Dispatchers.IO) {
            val keywords = extractKeywords(text)
            val now = Clock.System.now().toEpochMilliseconds()

            keywords.forEach { keyword ->
                if (keyword.length > 2) {
                    database.jarvisDatabaseQueries.insertKnowledgeNode(
                        name = keyword,
                        node_type = "concept",
                        properties = null,
                        created_at = now,
                        updated_at = now
                    )
                }
            }

            // เชื่อม keywords ที่อยู่ในบทสนทนาเดียวกัน (co-occurrence)
            if (keywords.size >= 2) {
                val sourceId = database.jarvisDatabaseQueries
                    .getNodeByName(keywords[0])
                    .executeAsOneOrNull()?.id

                keywords.drop(1).take(3).forEach { targetKeyword ->
                    val targetId = database.jarvisDatabaseQueries
                        .getNodeByName(targetKeyword)
                        .executeAsOneOrNull()?.id

                    if (sourceId != null && targetId != null && sourceId != targetId) {
                        database.jarvisDatabaseQueries.insertKnowledgeEdge(
                            source_id = sourceId,
                            target_id = targetId,
                            relation = "co_occurs",
                            weight = 0.5,
                            created_at = now
                        )
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // USER PROFILE
    // ═══════════════════════════════════════════════════════════════════

    suspend fun upsertUserProfile(field: String, value: String, confidence: Float = 0.8f) {
        withContext(Dispatchers.IO) {
            database.jarvisDatabaseQueries.upsertUserProfile(
                field_ = field,
                value_ = value,
                confidence = confidence.toDouble(),
                updated_at = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    suspend fun getAllUserProfile(): Map<String, String> = withContext(Dispatchers.IO) {
        database.jarvisDatabaseQueries.getAllUserProfile()
            .executeAsList()
            .associate { it.field_ to it.value_ }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Improved keyword extraction — รองรับทั้งภาษาไทยและอังกฤษ
     *
     * ภาษาไทยไม่มี space คั่นคำ → ใช้ character-class segmentation:
     *  1. แยก text เป็น segments ตามชนิดตัวอักษร (Thai / English / อื่นๆ)
     *  2. Segment ภาษาอังกฤษ → split ด้วย space ปกติ
     *  3. Segment ภาษาไทย → ใช้ n-gram (bigram 2-4 ตัวอักษร) + pattern matching
     *  4. กรอง stop words ทั้งสองภาษาออก
     */
    private fun extractKeywords(text: String): List<String> {
        val englishStopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "have", "has",
            "had", "do", "does", "did", "will", "would", "shall", "should", "can", "could",
            "may", "might", "must", "i", "you", "he", "she", "it", "we", "they",
            "my", "your", "his", "her", "its", "our", "their", "me", "him", "us", "them",
            "and", "or", "but", "not", "if", "then", "so", "to", "of", "in", "on", "at",
            "for", "with", "from", "by", "about", "as", "this", "that", "what", "which",
            "who", "how", "when", "where", "why", "all", "each", "every", "no", "any",
            "user", "model", "jarvis", "tool"
        )

        val thaiStopWords = setOf(
            "ที่", "ใน", "ของ", "และ", "หรือ", "แต่", "เพราะ", "ว่า", "ให้", "ได้",
            "กับ", "จาก", "ไป", "มา", "จะ", "ก็", "ด้วย", "แล้ว", "ถ้า", "เมื่อ",
            "คือ", "เป็น", "อยู่", "มี", "ไม่", "ครับ", "ค่ะ", "นะ", "ล่ะ", "สิ",
            "นี้", "นั้น", "ซึ่ง", "ดังนั้น", "ต้อง", "ไว้", "เขา", "เรา", "คุณ", "ผม",
            "ฉัน", "ดิฉัน", "มัน", "พวก", "บาง", "ทุก", "แค่", "เลย", "กัน", "กัน",
            "อะ", "นะครับ", "นะคะ", "ครับผม", "จ้า", "จ้ะ", "อืม", "โอเค"
        )

        val keywords = mutableListOf<String>()

        // ── 1. Extract English words ──
        val englishWords = Regex("[a-zA-Z]+").findAll(text)
            .map { it.value.lowercase() }
            .filter { it.length > 2 && it !in englishStopWords && !it.all { c -> c.isDigit() } }
            .toList()
        keywords.addAll(englishWords)

        // ── 2. Extract Thai segments ──
        val thaiSegments = Regex("[\\u0E00-\\u0E7F]+").findAll(text).map { it.value }.toList()

        for (segment in thaiSegments) {
            if (segment.length <= 1) continue

            // Thai segments ≤ 6 chars → treat as single word
            if (segment.length in 2..6 && segment !in thaiStopWords) {
                keywords.add(segment)
                continue
            }

            // Longer Thai segments → extract overlapping n-grams (sizes 2,3,4)
            for (n in listOf(4, 3, 2)) {
                if (segment.length >= n) {
                    for (i in 0..segment.length - n) {
                        val gram = segment.substring(i, i + n)
                        if (gram !in thaiStopWords && gram.length >= 2) {
                            keywords.add(gram)
                        }
                    }
                }
            }
        }

        // ── 3. Deduplicate & limit ──
        return keywords
            .distinct()
            .take(12)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LAYER 5: Memory Consolidation (Sleep Cycle)
    // ═══════════════════════════════════════════════════════════════════

    @Serializable
    data class ConsolidationFact(val content: String, val importance: Float)

    @Serializable
    data class ConsolidationNode(val name: String, val type: String = "concept", val weight_delta: Float = 1.0f)

    @Serializable
    data class ConsolidationEdge(val source: String, val target: String, val relation: String, val weight_delta: Float = 1.0f)

    @Serializable
    data class ConsolidationResult(
        val summary: String,
        val newArchivalFacts: List<ConsolidationFact> = emptyList(),
        val nodesToCreateOrUpdate: List<ConsolidationNode> = emptyList(),
        val edgesToCreateOrUpdate: List<ConsolidationEdge> = emptyList()
    )

    private val consolidationJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun performSleepCycle(geminiService: com.example.personalaibot.data.GeminiService, limitMsgs: Long = 100): Boolean {
        return withContext(Dispatchers.IO) {
            val oldMessages = database.jarvisDatabaseQueries.getOldMessages(limitMsgs).executeAsList()
            if (oldMessages.size <= 2) return@withContext false // น้อยเกินกว่าจะวิเคราะห์

            val transcript = oldMessages.joinToString("\n") { "${it.role}: ${it.content}" }
            
            val prompt = """
                You are Jarvis's Dream Engine (Memory Consolidation Phase).
                Analyze the following conversation block and extract meaningful knowledge to commit to long-term memory.
                Return ONLY valid JSON matching this exact schema:
                {
                  "summary": "Brief summary of what was talked about today",
                  "newArchivalFacts": [{"content": "Important user fact", "importance": 0.8}],
                  "nodesToCreateOrUpdate": [{"name": "Python", "type": "concept", "weight_delta": 1.0}],
                  "edgesToCreateOrUpdate": [{"source": "Python", "target": "Programming", "relation": "is_a", "weight_delta": 0.5}]
                }
                
                Transcript:
                $transcript
            """.trimIndent()

            try {
                // Call Gemini without grounding (internal task)
                val rawResponse = geminiService.generateResponse(prompt = prompt, enableGrounding = false)
                val jsonString = rawResponse.replace("```json", "").replace("```", "").trim()
                
                val result = consolidationJson.decodeFromString<ConsolidationResult>(jsonString)
                
                // 1. Insert Summary to Archival (Very High Importance)
                archiveFactWithEmbedding(geminiService, "Daily Summary: ${result.summary}", "system", 0.95f)
                
                // 2. Insert Archival Facts
                result.newArchivalFacts.forEach { fact ->
                    archiveFactWithEmbedding(geminiService, fact.content, "system", fact.importance)
                }

                val now = Clock.System.now().toEpochMilliseconds()

                // 3. Update or Insert Nodes
                result.nodesToCreateOrUpdate.forEach { node ->
                    val existing = database.jarvisDatabaseQueries.getNodeByName(node.name).executeAsOneOrNull()
                    if (existing != null) {
                        database.jarvisDatabaseQueries.updateKnowledgeNodeWeight(node.weight_delta.toDouble(), now, existing.id)
                    } else {
                        // Insert new node (id is auto-generated)
                        database.jarvisDatabaseQueries.insertKnowledgeNode(node.name, node.type, null, now, now)
                        // Then update its weight if we wanted to dynamically set it, but default is 1.0.
                    }
                }

                // 4. Update or Insert Edges
                result.edgesToCreateOrUpdate.forEach { edge ->
                    val sourceNode = database.jarvisDatabaseQueries.getNodeByName(edge.source).executeAsOneOrNull()
                    val targetNode = database.jarvisDatabaseQueries.getNodeByName(edge.target).executeAsOneOrNull()
                    
                    if (sourceNode != null && targetNode != null) {
                        try {
                            // Try insert first (IGNORE if exists)
                            database.jarvisDatabaseQueries.insertKnowledgeEdge(sourceNode.id, targetNode.id, edge.relation, 1.0, now)
                            // Then increment weight
                            database.jarvisDatabaseQueries.updateKnowledgeEdgeWeight(edge.weight_delta.toDouble(), sourceNode.id, targetNode.id)
                        } catch(e: Exception) {
                            com.example.personalaibot.logError("DreamEngine", "Edge update failed", e)
                        }
                    }
                }

                // 5. Delete processed messages from Working Memory
                val messageIds = oldMessages.map { it.id }
                if (messageIds.isNotEmpty()) {
                    database.jarvisDatabaseQueries.deleteMessagesByIds(messageIds)
                }

                true
            } catch(e: Exception) {
                com.example.personalaibot.logError("DreamEngine", "Sleep cycle failed: ${e.message}", e)
                false
            }
        }
    }
}
