package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * EyeLayerPosition — พิกัดและขนาดของดวงตาแต่ละชั้น
 *
 * @param backCenter จุดศูนย์กลางของวงกลมหลัง (เบ้าตาบนหัวหุ่นยนต์ สีน้ำเงินคราม)
 * @param frontCenter จุดศูนย์กลางของวงกลมหน้า (ลูกตาสีนีออนไซแอน)
 * @param discSize ขนาดของวงกลมทั้ง 2 ชั้น (มีขนาดเท่ากัน 100% ตามภาพ LOOI Robot)
 */
data class EyeLayerPosition(
    val backCenter: Offset,
    val frontCenter: Offset,
    val discSize: Size
)

/**
 * DualEyeKinematicsResult — ผลลัพธ์พิกัดดวงตาทั้งสองข้างที่คำนวณจากระบบจำลองหัวหุ่นยนต์ 3 มิติ
 */
data class DualEyeKinematicsResult(
    val leftEye: EyeLayerPosition,
    val rightEye: EyeLayerPosition,
    val headCenter: Offset,
    val headRollDeg: Float
)

/**
 * VirtualHeadKinematics — ระบบจำลองจลนศาสตร์หัวหุ่นยนต์ 3 มิติ (Virtual 3D Robot Head Kinematics)
 *
 * จำลองการเคลื่อนไหวเหมือนมี "หัวหุ่นยนต์ 3 มิติ" จริง โดยไม่ต้องวาดหัวหุ่นยนต์ (Pure Living OLED Face):
 * 1. วงกลม 2 วง (Front Disc และ Back Disc) มีขนาดเท่ากันทุกประการ (Equal Diameter)
 * 2. วงด้านหน้า (Front Disc - ลูกตาสีนีออนไซแอน): เปรียบเหมือน "ลูกตา" ขยับกลอกสายตา (Gaze / Saccades) ได้กว้างและคล่องแคล่ว
 * 3. วงด้านหลัง (Back Disc - เบ้าตาสีน้ำเงินคราม): เปรียบเหมือน "เบ้าตาบนหัวหุ่นยนต์"
 *    จะขยับต่อเมื่อหันหน้า (Yaw), เงยหน้า/ก้มหน้า (Pitch), หรือเอียงคอ (Roll) เท่านั้น
 * 4. เมื่อหัวหุ่นยนต์หันข้าง (Spherical Face Perspective):
 *    - เบ้าตาเลื่อนตามการหันของหัว
 *    - ระยะห่างระหว่างดวงตาหดแคบลงตามทรงกระบอก/ทรงกลม 3 มิติของใบหน้า (cos compression)
 *    - มีมิติความลึกพารัลแลกซ์ (Depth Parallax) ระหว่างผิวด้านหน้ากับก้นเบ้าตา
 */
object VirtualHeadKinematics {

    /**
     * คำนวณพิกัดดวงตาสองชั้นตามหลักจลนศาสตร์หัวหุ่นยนต์ 3 มิติ
     */
    fun calculate(
        headCenterX: Float,
        headCenterY: Float,
        baseSpacing: Float,
        eyeDiameter: Float,
        // Head Pose (-1f..+1f) — หัวหุ่นยนต์
        headYaw: Float,          // หันซ้าย (-1) ถึง หันขวา (+1)
        headPitch: Float,        // ก้มหน้า (-1) ถึง เงยหน้า (+1)
        headRollDeg: Float = 0f, // เอียงคอซ้าย-ขวา
        // Pupil Gaze (-1f..+1f) — ลูกตา
        pupilGazeX: Float = 0f,  // ลูกตากลอกมองซ้าย (-1) ถึง ขวา (+1)
        pupilGazeY: Float = 0f,  // ลูกตากลอกมองบน (-1) ถึง ล่าง (+1)
        // Squashing (Blink / Emotion Squint)
        squashX: Float = 1f,
        squashY: Float = 1f
    ): DualEyeKinematicsResult {
        val clampedYaw = headYaw.coerceIn(-1f, 1f)
        val clampedPitch = headPitch.coerceIn(-1f, 1f)
        val clampedGazeX = pupilGazeX.coerceIn(-1f, 1f)
        val clampedGazeY = pupilGazeY.coerceIn(-1f, 1f)

        val effW = eyeDiameter * squashX
        val effH = eyeDiameter * squashY
        val discSize = Size(effW, effH)

        // 1. Head Shift (ระยะเลื่อนของหัวหุ่นยนต์ 3 มิติ: หันหน้า / เงยหน้า-ก้มหน้า)
        val maxHeadShiftX = eyeDiameter * 0.40f
        val maxHeadShiftY = eyeDiameter * 0.30f
        val headShiftX = clampedYaw * maxHeadShiftX
        val headShiftY = -clampedPitch * maxHeadShiftY // pitch > 0 (เงยหน้า) -> shift Y up (negative)

        // 2. Spherical Head Curvature (ระยะห่างเบ้าตาลดลงเมื่อหันหน้าตามทรงกลม 3 มิติ)
        val curvatureCompress = cos(clampedYaw * 0.35f)
        val dynamicSpacing = baseSpacing * curvatureCompress

        // 3. ตำแหน่งเบ้าตา (Back Disc Centers) บนระนาบหัวหุ่นยนต์ (ก่อนหมุน Roll)
        val unrotatedBackLeftX = headCenterX + headShiftX - dynamicSpacing
        val unrotatedBackLeftY = headCenterY + headShiftY

        val unrotatedBackRightX = headCenterX + headShiftX + dynamicSpacing
        val unrotatedBackRightY = headCenterY + headShiftY

        // 4. Resting Offset (ระยะเยื้องปกติขณะพัก ให้เห็นเสี้ยวสีน้ำเงินครามด้านล่าง-ขวาตามภาพจริง LOOI)
        val blinkCompress = squashY.coerceIn(0.12f, 1f)
        val restingOffsetX = effW * 0.025f
        val restingOffsetY = effH * 0.065f * blinkCompress

        // 5. Pupil Gaze Travel (ระยะการกลอกของลูกตาหน้า ขยับได้กว้าง คล่องแคล่ว และตอบสนองเร็วกว่า)
        val maxPupilShiftX = effW * 0.15f
        val maxPupilShiftY = effH * 0.11f
        val pupilShiftX = clampedGazeX * maxPupilShiftX
        val pupilShiftY = clampedGazeY * maxPupilShiftY

        // 6. 3D Depth Parallax (มิติความลึกระหว่างผิวดวงตาหน้ากับก้นเบ้าตาหลังเมื่อหัวหัน)
        val maxParallaxX = effW * 0.055f
        val maxParallaxY = effH * 0.038f
        val parallaxX = clampedYaw * maxParallaxX
        val parallaxY = -clampedPitch * maxParallaxY

        // 7. ตำแหน่งลูกตาหน้า (Front Disc Centers) สัมพัทธ์กับเบ้าตา
        // เมื่อพัก (pupil = 0, parallax = 0): frontCenter = backCenter - restingOffset
        // ทำให้ backCenter อยู่ไปทาง +X (ขวา) และ +Y (ล่าง) จึงเห็นเสี้ยวขอบสีน้ำเงินครามที่ด้านล่าง-ขวา
        val unrotatedFrontLeftX = unrotatedBackLeftX - restingOffsetX + pupilShiftX + parallaxX
        val unrotatedFrontLeftY = unrotatedBackLeftY - restingOffsetY + pupilShiftY + parallaxY

        val unrotatedFrontRightX = unrotatedBackRightX - restingOffsetX + pupilShiftX + parallaxX
        val unrotatedFrontRightY = unrotatedBackRightY - restingOffsetY + pupilShiftY + parallaxY

        // 8. การหมุนตาม Head Roll (เอียงคอ) รอบจุดกึ่งกลางหัวหุ่นยนต์
        val headPivot = Offset(headCenterX + headShiftX, headCenterY + headShiftY)
        val rad = headRollDeg * (PI.toFloat() / 180f)
        val cosR = cos(rad)
        val sinR = sin(rad)

        fun rotatePoint(pX: Float, pY: Float): Offset {
            if (headRollDeg == 0f) return Offset(pX, pY)
            val dx = pX - headPivot.x
            val dy = pY - headPivot.y
            val rx = dx * cosR - dy * sinR
            val ry = dx * sinR + dy * cosR
            return Offset(headPivot.x + rx, headPivot.y + ry)
        }

        return DualEyeKinematicsResult(
            leftEye = EyeLayerPosition(
                backCenter = rotatePoint(unrotatedBackLeftX, unrotatedBackLeftY),
                frontCenter = rotatePoint(unrotatedFrontLeftX, unrotatedFrontLeftY),
                discSize = discSize
            ),
            rightEye = EyeLayerPosition(
                backCenter = rotatePoint(unrotatedBackRightX, unrotatedBackRightY),
                frontCenter = rotatePoint(unrotatedFrontRightX, unrotatedFrontRightY),
                discSize = discSize
            ),
            headCenter = headPivot,
            headRollDeg = headRollDeg
        )
    }
}
