package com.skyliner2008.jarvis.pet

import kotlin.math.hypot

/**
 * One hand the hand model found (HandTracker on Android, MediaPipe Gesture Recognizer): its 21 points in frame
 * pixels, MediaPipe order — 0 wrist, 4 thumb tip, 8 index tip, 9 middle knuckle, 12 middle tip, 16 ring tip, 20 pinky tip.
 */
class HandSighting(
    /** x0, y0, x1, y1 … — 21 points in frame pixels */
    val points: FloatArray,
    /** the model's own gesture: None, Closed_Fist, Open_Palm, Pointing_Up, Thumb_Down, Thumb_Up, Victory, ILoveYou */
    val modelGesture: String = "None",
    val gestureScore: Float = 0f,
    /** how sure the model is that this is a hand */
    val handScore: Float = 1f,
    /** "Left" / "Right" as the model sees it (it assumes a mirrored frame) — for the log */
    val side: String = "?"
) {
    fun x(i: Int) = points[2 * i]
    fun y(i: Int) = points[2 * i + 1]
    fun distance(a: Int, b: Int) = hypot(x(a) - x(b), y(a) - y(b))

    /** wrist to middle knuckle: the hand's size in this frame, the yardstick for every distance and motion */
    val palm: Float get() = distance(HandPose.WRIST, HandPose.MIDDLE_KNUCKLE)

    /** [left, top, right, bottom] around the 21 points, frame pixels */
    fun bounds(): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until points.size / 2) {
            l = minOf(l, x(i)); r = maxOf(r, x(i)); t = minOf(t, y(i)); b = maxOf(b, y(i))
        }
        return floatArrayOf(l, t, r, b)
    }
}

/**
 * What a hand is doing, from the hand model — it replaced counting skin-coloured pixels outside the face box,
 * which took arms, the desk and a hand in front of the face for hands and guessed the gesture from the blob's
 * width and height (phone 2026-10-09 00:33: one hand read 👍 and then ✌️ within a second).
 */
object HandPose {
    const val WRIST = 0
    const val THUMB_TIP = 4
    const val INDEX_TIP = 8
    const val MIDDLE_KNUCKLE = 9
    const val MIDDLE_TIP = 12

    /** the model's gesture counts from this score */
    const val MIN_GESTURE_SCORE = 0.55f
    /** 👌 is read from the points only on a hand the model is this sure of (edge-on, thumb and index tips overlap) */
    const val MIN_HAND_SCORE = 0.6f
    /** points of a hand inside the face box (shrunk by [COVER_INSET]) from which the hand covers the face */
    const val COVER_POINTS = 4
    const val COVER_INSET = 0.10f

    /**
     * @param swinging the fingers swing side to side ([SideToSide.reversals]) — Open_Palm is then a wave, Pointing_Up a wag
     * @param still the hand has held its place — an open palm is a high five only when held, not on its way to a wave
     * @param holding the hand holds a thing ([holds]) — a hand round a cup has its thumb and index tips together, as 👌
     */
    fun gesture(hand: HandSighting, swinging: Boolean, still: Boolean, holding: Boolean = false): HandGesture {
        if (!holding && isOk(hand)) return HandGesture.OK
        if (hand.gestureScore < MIN_GESTURE_SCORE) return HandGesture.NONE
        return when (hand.modelGesture) {
            "Victory" -> HandGesture.V_SIGN
            "Thumb_Up" -> HandGesture.THUMBS_UP
            "Thumb_Down" -> HandGesture.THUMBS_DOWN
            "Open_Palm" -> when {
                swinging -> HandGesture.BYE
                still -> HandGesture.HIGH_FIVE
                else -> HandGesture.NONE
            }
            "Pointing_Up" -> if (swinging) HandGesture.NO else HandGesture.NONE
            else -> HandGesture.NONE
        }
    }

    /** 👌: thumb and index tips touch while the other three fingers stand out (the model has no OK of its own) */
    fun isOk(hand: HandSighting): Boolean {
        if (hand.handScore < MIN_HAND_SCORE) return false
        val palm = hand.palm
        if (palm < 1f) return false
        if (hand.distance(THUMB_TIP, INDEX_TIP) > palm * 0.35f) return false
        // tip farther from the wrist than its middle joint: middle 12/10, ring 16/14, pinky 20/18
        return listOf(12 to 10, 16 to 14, 20 to 18).all { (tip, joint) ->
            hand.distance(WRIST, tip) > hand.distance(WRIST, joint) * 1.1f
        }
    }

    /** a hand with no gesture, in words: what the model saw it doing */
    fun poseLabel(hand: HandSighting): String = when (hand.modelGesture.takeIf { hand.gestureScore >= MIN_GESTURE_SCORE }) {
        "Closed_Fist" -> "✊ กำมือ"
        "Pointing_Up" -> "☝️ ชี้นิ้ว"
        "ILoveYou" -> "🤟 ไอเลิฟยู"
        "Open_Palm" -> "✋ แบมือ"
        else -> "✋ ยกมือ"
    }

    /**
     * The hand holds a thing: its thumb or index tip is on the thing's box [left, top, right, bottom] (frame pixels),
     * grown by [HOLD_MARGIN] of its size (phone 2026-10-10 00:57: a hand round a cup read 👌 and the pet answered it)
     */
    fun holds(hand: HandSighting, box: FloatArray): Boolean {
        val mx = (box[2] - box[0]) * HOLD_MARGIN
        val my = (box[3] - box[1]) * HOLD_MARGIN
        return listOf(THUMB_TIP, INDEX_TIP).any { i ->
            hand.x(i) in (box[0] - mx)..(box[2] + mx) && hand.y(i) in (box[1] - my)..(box[3] + my)
        }
    }

    /** how far around a thing's box a fingertip still holds it */
    const val HOLD_MARGIN = 0.15f

    /**
     * The hand is over the face [left, top, right, bottom] (frame pixels): ML Kit then sees a covered eye as a
     * closed one (a wink), and the face's embedding is of the hand as much as the face.
     */
    fun covers(hand: HandSighting, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        val insetX = (right - left) * COVER_INSET
        val insetY = (bottom - top) * COVER_INSET
        var inside = 0
        for (i in 0 until hand.points.size / 2) {
            if (hand.x(i) in (left + insetX)..(right - insetX) && hand.y(i) in (top + insetY)..(bottom - insetY)) inside++
        }
        return inside >= COVER_POINTS
    }
}

/**
 * A point's side-to-side motion over the last [windowMs] (frame pixels): a wave is the middle fingertip swinging,
 * a wag the index tip. A gap of [gapMs] without the hand starts over.
 */
class SideToSide(private val windowMs: Long = 1_500L, private val gapMs: Long = 500L) {
    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun offer(x: Float, nowMs: Long) {
        if (samples.isNotEmpty() && nowMs - samples.last().first > gapMs) samples.clear()
        samples.addLast(nowMs to x)
        while (nowMs - samples.first().first > windowMs) samples.removeFirst()
    }

    fun clear() = samples.clear()

    /** turns of direction, each after moving back at least [minSwing] from the last turning point */
    fun reversals(minSwing: Float): Int {
        val first = samples.firstOrNull() ?: return 0
        var extreme = first.second
        var direction = 0
        var turns = 0
        for ((_, x) in samples) {
            when (direction) {
                0 -> if (x - extreme >= minSwing) { direction = 1; extreme = x } else if (extreme - x >= minSwing) { direction = -1; extreme = x }
                1 -> if (x > extreme) extreme = x else if (extreme - x >= minSwing) { direction = -1; extreme = x; turns++ }
                else -> if (x < extreme) extreme = x else if (x - extreme >= minSwing) { direction = 1; extreme = x; turns++ }
            }
        }
        return turns
    }

    /** how far the point travelled across the last [sinceMs]; Float.MAX_VALUE until that long has been seen */
    fun span(nowMs: Long, sinceMs: Long): Float {
        val recent = samples.filter { nowMs - it.first <= sinceMs }
        if (recent.isEmpty() || nowMs - samples.first().first < sinceMs) return Float.MAX_VALUE
        return recent.maxOf { it.second } - recent.minOf { it.second }
    }
}

/**
 * Each frame's hands → the main hand and its gesture. Keeps each hand's motion ([SideToSide]) apart, so two hands do
 * not mix into one swing: a hand is the one whose wrist was nearest last frame (within [MATCH_PALMS] palms). It used to
 * be the side the model names, which with two hands in view sometimes named both "Right" — their x mixed into
 * one track and made swings (a wave, a no) nobody made; the side also flips with the back camera.
 */
class HandGestureReader {
    companion object {
        /** a wave: the middle fingertip turns twice, each swing at least this many palms */
        const val WAVE_SWING_PALMS = 0.6f
        /** a wag: the index tip turns twice, each swing at least this many palms */
        const val WAG_SWING_PALMS = 0.3f
        const val TURNS = 2
        /** held still: the middle fingertip moved under this many palms over [STILL_MS] */
        const val STILL_PALMS = 0.35f
        const val STILL_MS = 400L
        /** the same hand as last frame: its wrist moved at most this many palms */
        const val MATCH_PALMS = 2.5f
    }

    data class Reading(val hand: HandSighting, val gesture: HandGesture)

    private class Track(var wristX: Float, var wristY: Float) {
        val tip = SideToSide()
        val index = SideToSide()
    }

    private var tracks: List<Track> = emptyList()

    /**
     * null when no hand is in view; otherwise the hand with a gesture (the surest), else the biggest.
     * [thingBoxes]: the things in view [left, top, right, bottom] (frame pixels) — a hand holding one makes no 👌
     */
    fun read(hands: List<HandSighting>, nowMs: Long, thingBoxes: List<FloatArray> = emptyList()): Reading? {
        if (hands.isEmpty()) {
            tracks = emptyList()
            return null
        }
        val free = tracks.toMutableList()
        val next = mutableListOf<Track>()
        val readings = hands.map { hand ->
            val wx = hand.x(HandPose.WRIST)
            val wy = hand.y(HandPose.WRIST)
            val nearest = free.minByOrNull { hypot(it.wristX - wx, it.wristY - wy) }
                ?.takeIf { hypot(it.wristX - wx, it.wristY - wy) <= hand.palm * MATCH_PALMS }
            val track = nearest?.also { free.remove(it) } ?: Track(wx, wy)
            track.wristX = wx
            track.wristY = wy
            next += track
            val tip = track.tip.apply { offer(hand.x(HandPose.MIDDLE_TIP), nowMs) }
            val index = track.index.apply { offer(hand.x(HandPose.INDEX_TIP), nowMs) }
            val palm = hand.palm
            val swinging = when (hand.modelGesture) {
                "Pointing_Up" -> index.reversals(palm * WAG_SWING_PALMS) >= TURNS
                else -> tip.reversals(palm * WAVE_SWING_PALMS) >= TURNS
            }
            val still = tip.span(nowMs, STILL_MS) < palm * STILL_PALMS
            val holding = thingBoxes.any { HandPose.holds(hand, it) }
            Reading(hand, HandPose.gesture(hand, swinging, still, holding))
        }
        tracks = next
        return readings.filter { it.gesture != HandGesture.NONE }.maxByOrNull { it.hand.gestureScore }
            ?: readings.maxByOrNull { it.hand.palm }
    }

    fun clear() {
        tracks = emptyList()
    }
}
