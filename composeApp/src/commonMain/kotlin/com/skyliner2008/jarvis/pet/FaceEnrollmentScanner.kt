package com.skyliner2008.jarvis.pet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * FaceEnrollmentScanner — ระบบลงทะเบียนสแกนใบหน้าอัจฉริยะ (High-End Biometric Scanner)
 *
 * 1. ผู้ใช้เห็นใบหน้าตัวเองจากกล้องจริงแบบ Real-time พร้อมกรอบวงรีเล็งใบหน้า
 * 2. ตรวจสอบจุดต่าง 4 มิติ: ดวงตา, สันจมูก, ริมฝีปาก และสัดส่วนโครงหน้า
 * 3. ตรวจจับตำแหน่งกึ่งกลางและมุมมองตรง (Center & Looking Straight)
 * 4. รวบรวมตัวอย่างหลายเฟรม (Multi-frame Landmark Averaging) แสดง Progress 0% -> 100% ชัดเจน
 * 5. สรุปผลสำเร็จ 100% พร้อมบันทึกลงสล็อต
 */
@Composable
fun FaceEnrollmentScanner(
    slotIndex: Int,
    slotName: String,
    biometrics: FaceBiometricDetails,
    /**
     * averagedLandmarks: mean landmark ratios; embedding: mean SFace embedding (empty without the model);
     * poseEmbeddings: the turned-head ones ([TurnedHeadSampler])
     */
    onCompleteEnrollment: (
        slotIndex: Int,
        slotName: String,
        averagedLandmarks: List<Float>,
        embedding: List<Float>,
        poseEmbeddings: List<List<Float>>
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onSwitchCamera: (() -> Unit)? = null,
    cameraContent: (@Composable () -> Unit)? = null
) {
    var isCompleted by remember { mutableStateOf(false) }
    var finalAveragedRatios by remember { mutableStateOf<List<Float>>(emptyList()) }
    // SFace on the phone ([FaceRecognition]): enroll by embedding; without it, by landmark ratios as before
    val useEmbedding = remember { PetVisionBridge.faceEmbeddingAvailable() }
    // one person's samples: a gap with no usable face or a different face in front starts over
    // (every frame used to be averaged, two people included)
    val sampler = remember {
        if (useEmbedding) {
            // embeddings come ~3 a second: 8 of them (~3 s)
            EnrollmentSampler(target = 8, minIntervalMs = 250L, sameFace = { mean, sample ->
                FaceRecognition.cosine(mean, sample) >= FaceRecognition.SAME_PERSON_COSINE
            })
        } else {
            EnrollmentSampler(target = 25, minIntervalMs = 80L, sameFace = { mean, sample ->
                EnrollmentSampler.rms(mean, sample) <= PetFaceProfile.MATCH_THRESHOLD * 2
            })
        }
    }
    var sampleCount by remember { mutableStateOf(0) }
    var lastEmbeddingAt by remember { mutableStateOf(0L) }
    var restarted by remember { mutableStateOf(false) }
    // step 2 (with the model): the head turned a little each way — null while the frontal step runs
    var turned by remember { mutableStateOf<TurnedHeadSampler?>(null) }
    var turnedCount by remember { mutableStateOf(0) }
    var frontResult by remember { mutableStateOf<Pair<List<Float>, List<Float>>?>(null) }

    val targetFrames = sampler.target

    fun finish(ratios: List<Float>, embedding: List<Float>, poses: List<List<Float>>) {
        if (isCompleted) return
        finalAveragedRatios = ratios
        isCompleted = true
        // how alike the scan's samples were — to check an enrollment from logcat
        val minCos = if (embedding.isNotEmpty()) sampler.samples().minOf { FaceRecognition.cosine(embedding, it) } else null
        val poseCos = poses.map { FaceRecognition.cosine(embedding, it) }
        com.skyliner2008.jarvis.logDebug(
            "FaceScan",
            "🪪 slot ${slotIndex + 1}: ${sampler.count} samples, restarts ${sampler.restarts}" +
                (minCos?.let { ", min cos to mean ${"%.2f".format(it)}" } ?: " (landmark ratios)") +
                if (embedding.isNotEmpty()) ", ${poses.size} turned-head (cos ${poseCos.joinToString { "%.2f".format(it) }})" else ""
        )
        onCompleteEnrollment(slotIndex, slotName, ratios, embedding, poses)
    }

    // the turned-head step ends by itself: a user who does not turn still gets the frontal enrollment
    LaunchedEffect(turned) {
        val step = turned ?: return@LaunchedEffect
        kotlinx.coroutines.delay(TURN_STEP_MS)
        frontResult?.let { (ratios, embedding) -> finish(ratios, embedding, step.samples()) }
    }

    LaunchedEffect(biometrics) {
        if (isCompleted) return@LaunchedEffect

        turned?.let { step ->
            val (ratios, embedding) = frontResult ?: return@LaunchedEffect
            if (!biometrics.isFaceDetected || !biometrics.isPlaced) return@LaunchedEffect
            if (biometrics.embedding.isEmpty() || biometrics.embeddingAtMs == lastEmbeddingAt) return@LaunchedEffect
            lastEmbeddingAt = biometrics.embeddingAtMs
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            if (step.offer(biometrics.embedding, biometrics.embeddingYaw, now)) turnedCount = step.count
            if (step.done) finish(ratios, embedding, step.samples())
            return@LaunchedEffect
        }

        val isReadyForSampling = biometrics.isFaceDetected &&
                biometrics.isCentered &&
                biometrics.hasEyesDetected &&
                biometrics.hasNoseDetected &&
                biometrics.landmarkRatios.size == 4
        if (!isReadyForSampling) return@LaunchedEffect

        val sample = if (useEmbedding) {
            // a new embedding only — the frames between two repeat the last one
            if (biometrics.embedding.isEmpty() || biometrics.embeddingAtMs == lastEmbeddingAt) return@LaunchedEffect
            lastEmbeddingAt = biometrics.embeddingAtMs
            biometrics.embedding
        } else biometrics.landmarkRatios

        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val result = sampler.offer(sample, now, extra = biometrics.landmarkRatios)
        sampleCount = sampler.count
        when (result) {
            EnrollmentSampler.Result.RESTARTED -> restarted = true
            EnrollmentSampler.Result.ADDED -> if (sampler.count > 2) restarted = false
            EnrollmentSampler.Result.DONE -> {
                val ratios = EnrollmentSampler.average(sampler.extras())
                if (useEmbedding) {
                    val embedding = FaceRecognition.meanEmbedding(sampler.samples())
                    frontResult = ratios to embedding
                    turned = TurnedHeadSampler(embedding)
                } else {
                    finish(ratios, emptyList(), emptyList())
                }
            }
            else -> Unit
        }
    }

    // Auto-dismiss 2.5s after successful completion
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            kotlinx.coroutines.delay(2500L)
            onCancel()
        }
    }

    val turnStep = turned
    // with the model the frontal step fills the bar to FRONT_SHARE, the turned head the rest
    val progress = when {
        isCompleted -> 1f
        turnStep != null -> FRONT_SHARE + (1f - FRONT_SHARE) * turnedCount / turnStep.target
        useEmbedding -> FRONT_SHARE * sampleCount / targetFrames
        else -> sampleCount.toFloat() / targetFrames
    }.coerceIn(0f, 1f)
    val progressPct = (progress * 100).toInt()
    // in place: centered and straight for the frontal step, centered at any angle for the turned head
    val inPlace = if (turnStep != null) biometrics.isPlaced else biometrics.isCentered

    val infiniteTransition = rememberInfiniteTransition(label = "biometric_laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF070B14).copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Header ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ระบบสแกนใบหน้าชีวมิติ (สล็อตที่ ${slotIndex + 1})",
                        color = Color(0xFF00F0FF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "โปรไฟล์: $slotName",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSwitchCamera != null) {
                        IconButton(
                            onClick = onSwitchCamera,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White.copy(alpha = 0.10f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.FlipCameraAndroid,
                                contentDescription = "Switch Camera",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.10f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── Scanner Viewport (With Reticle & Laser Overlay) ───
            Box(
                modifier = Modifier
                    .size(width = 280.dp, height = 350.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(
                        BorderStroke(
                            2.dp,
                            if (isCompleted) Color(0xFF00E676)
                            else if (inPlace) Color(0xFF00F0FF)
                            else Color(0xFFFFB300).copy(alpha = 0.7f)
                        ),
                        RoundedCornerShape(26.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Real-time camera feed underneath reticle
                if (cameraContent != null) {
                    cameraContent()
                }

                // Biometric Reticle & Guide Canvas (Draws oval, corner ticks, laser)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 1. Face Oval Guide
                    val ovalWidth = canvasWidth * 0.68f
                    val ovalHeight = canvasHeight * 0.72f
                    val ovalLeft = (canvasWidth - ovalWidth) / 2f
                    val ovalTop = (canvasHeight - ovalHeight) / 2f

                    val reticleColor = when {
                        isCompleted -> Color(0xFF00E676)
                        inPlace -> Color(0xFF00F0FF)
                        biometrics.isFaceDetected -> Color(0xFFFFB300)
                        else -> Color.White.copy(alpha = 0.35f)
                    }

                    drawOval(
                        color = reticleColor,
                        topLeft = Offset(ovalLeft, ovalTop),
                        size = Size(ovalWidth, ovalHeight),
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = if (!inPlace && !isCompleted) PathEffect.dashPathEffect(floatArrayOf(16f, 12f)) else null
                        )
                    )

                    // 2. Corner Target Brackets
                    val bracketLen = 22f
                    val strokeW = 3f
                    val bColor = reticleColor.copy(alpha = 0.9f)
                    // Top-Left
                    drawLine(bColor, Offset(16f, 16f), Offset(16f + bracketLen, 16f), strokeWidth = strokeW)
                    drawLine(bColor, Offset(16f, 16f), Offset(16f, 16f + bracketLen), strokeWidth = strokeW)
                    // Top-Right
                    drawLine(bColor, Offset(canvasWidth - 16f, 16f), Offset(canvasWidth - 16f - bracketLen, 16f), strokeWidth = strokeW)
                    drawLine(bColor, Offset(canvasWidth - 16f, 16f), Offset(canvasWidth - 16f, 16f + bracketLen), strokeWidth = strokeW)
                    // Bottom-Left
                    drawLine(bColor, Offset(16f, canvasHeight - 16f), Offset(16f + bracketLen, canvasHeight - 16f), strokeWidth = strokeW)
                    drawLine(bColor, Offset(16f, canvasHeight - 16f), Offset(16f, canvasHeight - 16f - bracketLen), strokeWidth = strokeW)
                    // Bottom-Right
                    drawLine(bColor, Offset(canvasWidth - 16f, canvasHeight - 16f), Offset(canvasWidth - 16f - bracketLen, canvasHeight - 16f), strokeWidth = strokeW)
                    drawLine(bColor, Offset(canvasWidth - 16f, canvasHeight - 16f), Offset(canvasWidth - 16f, canvasHeight - 16f - bracketLen), strokeWidth = strokeW)

                    // 3. Laser Sweep Line (Only while scanning)
                    if (!isCompleted && biometrics.isFaceDetected) {
                        val lineY = canvasHeight * laserY
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFF00F0FF), Color(0xFF7EF7FF), Color(0xFF00F0FF), Color.Transparent)
                            ),
                            start = Offset(ovalLeft, lineY),
                            end = Offset(ovalLeft + ovalWidth, lineY),
                            strokeWidth = 3f
                        )
                    }
                }

                // Completion Badge Overlay
                if (isCompleted) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.90f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF00E676)),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Done",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "100% สแกนเสร็จสิ้น!",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "บันทึกโปรไฟล์ ${slotName} เรียบร้อย",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── 4 Distinctive Features Checklist ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeatureBadge(label = "ดวงตา 👁️", isDetected = biometrics.hasEyesDetected)
                FeatureBadge(label = "สันจมูก 👃", isDetected = biometrics.hasNoseDetected)
                FeatureBadge(label = "ริมฝีปาก 👄", isDetected = biometrics.hasMouthDetected)
                FeatureBadge(label = "ตรงกลาง 📐", isDetected = inPlace)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Progress Bar & Percentage ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCompleted -> "✅ สแกนสำเร็จ 100% สมบูรณ์"
                        !biometrics.isFaceDetected -> "⚪ กรุณามองตรงมาที่กล้อง"
                        !inPlace -> "🟡 จัดใบหน้าให้อยู่ตรงกลางกรอบ"
                        turnStep != null -> "↔️ ค่อยๆ หันหน้าไปทางซ้ายและขวาเล็กน้อย ($turnedCount/${turnStep.target})"
                        restarted -> "🔄 ใบหน้าหลุดกรอบหรือเปลี่ยนคน — เริ่มเก็บใหม่"
                        else -> "🟢 กำลังวิเคราะห์จุดต่างและโครงหน้า..."
                    },
                    color = when {
                        isCompleted -> Color(0xFF00E676)
                        inPlace -> Color(0xFF00F0FF)
                        biometrics.isFaceDetected -> Color(0xFFFFB300)
                        else -> Color.White.copy(alpha = 0.6f)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "$progressPct%",
                    color = if (isCompleted) Color(0xFF00E676) else Color(0xFF00F0FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isCompleted) Color(0xFF00E676) else Color(0xFF00F0FF),
                trackColor = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Action Button ───
            if (isCompleted) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "เสร็จสิ้น (บันทึกเรียบร้อย)",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (turnStep != null) {
                Text(
                    text = "ขั้นที่ 2: หันหน้าช้าๆ ไปซ้ายและขวาเล็กน้อย (ประมาณ 15–30°) ช่วยให้น้องจำได้แม้ไม่ได้มองตรง",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { frontResult?.let { (ratios, embedding) -> finish(ratios, embedding, turnStep.samples()) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "ข้ามขั้นนี้ (บันทึกเฉพาะหน้าตรง)", color = Color.White, fontSize = 13.sp)
                }
            } else {
                Text(
                    text = "เคล็ดลับ: หันหน้าตรงเข้าหากล้องในที่มีแสงสว่างเพียงพอ และอยู่นิ่งๆ สักครู่",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
}

/** the turned-head step gives up after this long and saves what it has (the frontal template at least) */
private const val TURN_STEP_MS = 10_000L
/** the share of the progress bar the frontal step fills when a turned-head step follows */
private const val FRONT_SHARE = 0.7f

@Composable
private fun FeatureBadge(label: String, isDetected: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDetected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF1E293B).copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isDetected) Color(0xFF00E676).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDetected) "✓ " else "• ",
                color = if (isDetected) Color(0xFF00E676) else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = if (isDetected) Color.White else Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}
