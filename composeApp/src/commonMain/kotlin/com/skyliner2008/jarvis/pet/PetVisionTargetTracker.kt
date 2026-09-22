package com.skyliner2008.jarvis.pet

import kotlinx.datetime.Clock
import com.skyliner2008.jarvis.camera.BoundingBox
import com.skyliner2008.jarvis.camera.DetectedObject
import kotlin.math.hypot

/**
 * PetVisionTargetTracker — ควบคุมความนิ่ง การล็อกเป้าหมาย (Target Locking) และการกรองสัญญาณรบกวน
 * สำหรับระบบตรวจจับภาพในโหมดสัตว์เลี้ยง (Pet Mode Vision)
 *
 * แก้ไขปัญหา:
 * 1. วัตถุตรวจจับกระโดดสลับไปมา (Jitter & Target Swapping)
 * 2. ตรวจจับหลายสิ่งยิบย่อยเกินไป (Background clutter, walls, pipes, false face overlaps)
 * 3. กรอบกระพริบติดๆ ดับๆ เมื่อพลาดเฟรมเดียว (Flickering)
 */
class PetVisionTargetTracker(
    private val smoothingFactor: Float = 0.40f,
    private val persistenceWindowMs: Long = 350L,
    private val lockThresholdFrames: Int = 3,
    private val maxActiveTargets: Int = 3
) {

    data class TrackedState(
        val category: TargetCategory,
        var label: String,
        var confidence: Float,
        var color: String,
        var box: BoundingBox,
        var lastSeenMs: Long,
        var consecutiveFrames: Int = 1,
        var isLocked: Boolean = false
    )

    enum class TargetCategory {
        FACE,
        HAND,
        OBJECT
    }

    private val activeTracks = mutableListOf<TrackedState>()

    /**
     * กรองและจัดลำดับความสำคัญของวัตถุ candidate ในเฟรมปัจจุบัน
     */
    fun filterCandidates(
        rawObjects: List<DetectedObject>,
        faces: List<BoundingBox> = emptyList()
    ): List<DetectedObject> {
        val filtered = mutableListOf<DetectedObject>()

        // ขยายขอบเขตใบหน้ารวมลำคอ (Exclusion zone) เพื่อไม่ให้วัตถุตรวจจับซ้อนทับใบหน้า/คอ
        val faceExclusionZones = faces.map { fb ->
            val padX = fb.width * 0.15f
            val padTop = fb.height * 0.10f
            val padBottom = fb.height * 0.35f
            BoundingBox(
                x = (fb.x - padX).coerceAtLeast(0f),
                y = (fb.y - padTop).coerceAtLeast(0f),
                width = (fb.width + padX * 2f).coerceAtMost(1f),
                height = (fb.height + padTop + padBottom).coerceAtMost(1f)
            )
        }

        // 1. แยกหมวดหมู่
        val faceCandidates = mutableListOf<DetectedObject>()
        val handCandidates = mutableListOf<DetectedObject>()
        val objectCandidates = mutableListOf<DetectedObject>()

        for (obj in rawObjects) {
            val box = obj.boundingBox ?: continue
            val label = obj.label

            // กรองทิ้ง: สถานที่/ผนังห้อง (Place / Scenery) ที่ทำให้รกหน้าจอ
            if (label.contains("Place") || label.contains("Scenery")) {
                continue
            }

            when {
                label.contains("Face") || label.contains("Smile") || label.contains("Wink") -> {
                    faceCandidates.add(obj)
                }
                label.contains("Hand") || label.contains("Finger") -> {
                    handCandidates.add(obj)
                }
                else -> {
                    // กรองทิ้ง: วัตถุที่ทับกับโซนใบหน้า/ลำคอ (ป้องกัน Accessory/Item ครอบปากหรือแว่นตาผิดตำแหน่ง)
                    val centerX = box.x + box.width / 2f
                    val centerY = box.y + box.height / 2f
                    val isInsideFaceZone = faceExclusionZones.any { zone ->
                        centerX in zone.x..(zone.x + zone.width) &&
                        centerY in zone.y..(zone.y + zone.height)
                    }
                    if (isInsideFaceZone) continue

                    // กรองทิ้ง: วัตถุจิ๋วที่เป็น noise พื้นหลัง (เช่น รอยต่อท่อ, สวิตช์ไฟ)
                    val area = box.width * box.height
                    val isGeneric = label.contains("Object")
                    if (isGeneric && (area < 0.02f || box.width < 0.10f || box.height < 0.10f || obj.confidence < 0.70f)) {
                        continue
                    }
                    if (box.width < 0.06f || box.height < 0.06f) {
                        continue
                    }

                    objectCandidates.add(obj)
                }
            }
        }

        // 2. คัดเลือกเฉพาะเป้าหมายที่เด่นที่สุดในแต่ละหมวดหมู่
        // หน้า: เลือกหน้าที่ใหญ่ที่สุด 1 หน้า
        faceCandidates.maxByOrNull { (it.boundingBox?.width ?: 0f) * (it.boundingBox?.height ?: 0f) }?.let {
            filtered.add(it)
        }

        // มือ: เลือกมือที่ชัดเจนที่สุด 1 มือ
        handCandidates.maxByOrNull { it.confidence }?.let {
            filtered.add(it)
        }

        // วัตถุ: เลือกวัตถุที่ชัดเจนและมีขนาดเด่นที่สุด 1 ชิ้น (ไม่เกินโควต้า maxActiveTargets)
        objectCandidates.maxByOrNull { (it.boundingBox?.width ?: 0f) * (it.boundingBox?.height ?: 0f) }?.let {
            filtered.add(it)
        }

        return filtered.take(maxActiveTargets)
    }

    /**
     * ประมวลผลและ Smooth พิกัดวัตถุในแต่ละเฟรม
     */
    fun processFrame(
        rawObjects: List<DetectedObject>,
        faces: List<BoundingBox> = emptyList(),
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): List<DetectedObject> {
        val candidates = filterCandidates(rawObjects, faces)

        val matchedTrackIndices = mutableSetOf<Int>()

        // จับคู่ candidates กับ existing tracks ด้วย IOU / Center distance
        for (cand in candidates) {
            val candBox = cand.boundingBox ?: continue
            val candCategory = categorize(cand.label)

            var bestTrackIdx = -1
            var bestScore = 0f

            for ((idx, track) in activeTracks.withIndex()) {
                if (idx in matchedTrackIndices) continue
                if (track.category != candCategory) continue

                val iou = computeIou(track.box, candBox)
                val dist = computeCenterDist(track.box, candBox)
                val score = iou + (1f - dist.coerceAtMost(1f)) * 0.5f

                if (score > 0.40f && score > bestScore) {
                    bestScore = score
                    bestTrackIdx = idx
                }
            }

            if (bestTrackIdx != -1) {
                // อัปเดตและ Smooth Track เดิม
                matchedTrackIndices.add(bestTrackIdx)
                val track = activeTracks[bestTrackIdx]

                val smoothedX = track.box.x + (candBox.x - track.box.x) * smoothingFactor
                val smoothedY = track.box.y + (candBox.y - track.box.y) * smoothingFactor
                val smoothedW = track.box.width + (candBox.width - track.box.width) * smoothingFactor
                val smoothedH = track.box.height + (candBox.height - track.box.height) * smoothingFactor

                track.box = BoundingBox(
                    x = smoothedX.coerceIn(0f, 1f),
                    y = smoothedY.coerceIn(0f, 1f),
                    width = smoothedW.coerceIn(0.01f, 1f),
                    height = smoothedH.coerceIn(0.01f, 1f)
                )
                track.label = cand.label
                track.confidence = cand.confidence
                track.color = cand.color
                track.lastSeenMs = currentTimeMs
                track.consecutiveFrames++
                if (track.consecutiveFrames >= lockThresholdFrames) {
                    track.isLocked = true
                }
            } else {
                // เพิ่ม Track ใหม่หากยังไม่เต็มโควต้า
                if (activeTracks.size < maxActiveTargets) {
                    val newTrack = TrackedState(
                        category = candCategory,
                        label = cand.label,
                        confidence = cand.confidence,
                        color = cand.color,
                        box = candBox,
                        lastSeenMs = currentTimeMs,
                        consecutiveFrames = 1,
                        isLocked = false
                    )
                    activeTracks.add(newTrack)
                    matchedTrackIndices.add(activeTracks.lastIndex)
                }
            }
        }

        // Hysteresis: รักษา Track ที่หลุดไปชั่วคราวไว้ไม่เกิน persistenceWindowMs
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            if (currentTimeMs - track.lastSeenMs > persistenceWindowMs) {
                iterator.remove()
            }
        }

        // แปลงกลับเป็น List<DetectedObject> พร้อมส่งสถานะ isLocked
        return activeTracks.map { track ->
            DetectedObject(
                label = track.label,
                confidence = track.confidence,
                boundingBox = track.box,
                color = track.color,
                isLocked = track.isLocked
            )
        }
    }

    fun clear() {
        activeTracks.clear()
    }

    private fun categorize(label: String): TargetCategory {
        return when {
            label.contains("Face") || label.contains("Smile") || label.contains("Wink") -> TargetCategory.FACE
            label.contains("Hand") || label.contains("Finger") -> TargetCategory.HAND
            else -> TargetCategory.OBJECT
        }
    }

    private fun computeIou(b1: BoundingBox, b2: BoundingBox): Float {
        val interLeft = maxOf(b1.x, b2.x)
        val interTop = maxOf(b1.y, b2.y)
        val interRight = minOf(b1.x + b1.width, b2.x + b2.width)
        val interBottom = minOf(b1.y + b1.height, b2.y + b2.height)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val area1 = b1.width * b1.height
        val area2 = b2.width * b2.height
        val unionArea = area1 + area2 - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun computeCenterDist(b1: BoundingBox, b2: BoundingBox): Float {
        val c1x = b1.x + b1.width / 2f
        val c1y = b1.y + b1.height / 2f
        val c2x = b2.x + b2.width / 2f
        val c2y = b2.y + b2.height / 2f
        return hypot((c1x - c2x).toDouble(), (c1y - c2y).toDouble()).toFloat()
    }
}
