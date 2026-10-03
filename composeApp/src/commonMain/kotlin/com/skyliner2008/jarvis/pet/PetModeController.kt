package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.sound.RobotSoundPlayer
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.AvatarState
import com.skyliner2008.jarvis.ui.component.avatar.RobotFaceState
import com.skyliner2008.jarvis.ui.component.avatar.BackgroundTheme
import com.skyliner2008.jarvis.ui.component.avatar.PropType
import com.skyliner2008.jarvis.ui.component.avatar.DynamicVectorProp
import com.skyliner2008.jarvis.ui.component.avatar.withFace
import com.skyliner2008.jarvis.avatar.AvatarSkit
import com.skyliner2008.jarvis.avatar.AvatarSkits
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import kotlinx.datetime.Clock

/**
 * PetFeatureTab — แท็บฟีเจอร์ย่อยในโหมดสัตว์เลี้ยง
 */
enum class PetFeatureTab {
    PET,        // หน้าสัตว์เลี้ยง เล่น ลูบ เกา
    SENTRY,     // โหมดสายตรวจเฝ้าโต๊ะ
    FOCUS,      // Focus Buddy (Pomodoro)
    GAMES       // มินิเกม / เซียมซี
}

/**
 * PetModeController — ศูนย์กลางจัดการฟังก์ชันและพฤติกรรมของโหมดสัตว์เลี้ยง
 */
class PetModeController(
    private val scope: CoroutineScope,
    private val avatarStateProvider: () -> AvatarState,
    private val onUpdateAvatarState: (AvatarState) -> Unit,
    private val onSentryIntruderAlert: (suspend () -> Unit)? = null
) {
    companion object {
        private const val TAG = "PetModeController"
        private const val PASSIVE_EMOTION_COOLDOWN_MS = 60_000L
        /** ค้างท้ายเรื่องสั้นๆ ก่อนคืนหน้าปกติ */
        const val SCENE_TAIL_MS = 300L
        /** what it picks for itself when it eats (pet_godot "snack" kinds; the voice can ask for a food prop) */
        private val SELF_FOODS = listOf("donut", "burger", "pizza", "cake", "ice_cream", "popcorn")
        /** its own scenes of the quiet: drowsiness may take over from them */
        private val OWN_SCENES = setOf(AvatarSkits.EAT, AvatarSkits.BATH, AvatarSkits.PLAY_BALL, AvatarSkits.IDLE_PLAY,
            AvatarSkits.WAKE).map { it.sceneName }.toSet()
        private const val LOUD_NOISE_COOLDOWN_MS = 8_000L
        var activeInstance: PetModeController? = null
            private set
    }

    // ─── State Machine & Memory ─────────────────────────────────────────────
    val stateMachine = PetStateMachine()
    private var autoSaveJob: Job? = null

    // ─── Tab State ─────────────────────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(PetFeatureTab.PET)
    val selectedTab: StateFlow<PetFeatureTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: PetFeatureTab) {
        logDebug(TAG, "📑 selectTab: $tab")
        _selectedTab.value = tab
        when (tab) {
            PetFeatureTab.SENTRY -> {
                RobotSoundPlayer.playSurprise()
                onUpdateAvatarState(avatarStateProvider().copy(
                    emotion = AvatarEmotion.ANGRY,
                    statusText = "🛡️ เปิดโหมดสายตรวจเฝ้าโต๊ะ..."
                ))
            }
            PetFeatureTab.FOCUS -> {
                RobotSoundPlayer.playHappy()
                if (_isFocusRunning.value) {
                    onUpdateAvatarState(avatarStateProvider().copy(
                        emotion = AvatarEmotion.THINKING,
                        statusText = "⏱️ Focus Buddy พร้อมช่วยตั้งใจทำงานแล้วค่ะ"
                    ))
                } else {
                    showTransientEmotion(AvatarEmotion.THINKING, "⏱️ Focus Buddy พร้อมช่วยตั้งใจทำงานแล้วค่ะ", 3000L)
                }
            }
            PetFeatureTab.GAMES -> {
                RobotSoundPlayer.playHappy()
                showTransientEmotion(AvatarEmotion.EXCITED, "🎲 มินิเกมแก้เบื่อ! เลือกเกมด้านล่างเลยค่ะ", 3000L)
            }
            PetFeatureTab.PET -> {
                RobotSoundPlayer.playHappy()
                onUpdateAvatarState(avatarStateProvider().copy(
                    emotion = AvatarEmotion.IDLE,
                    statusText = "🐾 ลูบหัวหรือจิ้มเล่นกับน้องได้เลยน้า"
                ))
            }
        }
    }

    // ─── Needs & Psychology Engine (Tamagotchi State) ──────────────────────────
    private val _needsState = MutableStateFlow(PetNeedsState(lastUpdateTimestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
    val needsState: StateFlow<PetNeedsState> = _needsState.asStateFlow()

    // ─── Idle life (PetSelfCare): Avatar mode has no care buttons — quiet for 30 s it plays (or eats, bathes),
    //     60 s drowsy, 90 s the screen's eyelids sag, 120 s asleep with the screen at 1 %; a voice or a touch wakes it ───
    private val selfCare = PetSelfCare()
    private val _idleStage = MutableStateFlow(PetSelfCare.Stage.AWAKE)
    /** where the quiet has got to — Avatar mode shows it on the screen (eyelids, brightness) */
    val idleStage: StateFlow<PetSelfCare.Stage> = _idleStage.asStateFlow()
    private var idleStageSinceMs = 0L
    /** last time someone talked (the user or the AI), a tool ran or a result waited ([onAvatarTick]) */
    private var lastConversationAtMs = 0L
    /** last touch, shake, gesture or care reaction (not the idle loop's own moods) */
    private var lastReactionAtMs = 0L
    /** this quiet stretch has had its own act (play / eat / bathe) */
    private var actedThisStretch = false
    /** the idle life's clock (tests run it fast) */
    internal var nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    /** asleep (the screen dark): the user's voice or a touch wakes it */
    val isAsleep: Boolean
        get() = _idleStage.value == PetSelfCare.Stage.ASLEEP

    // ─── Face Recognition (5 Slots) ────────────────────────────────────────────
    private val _faceProfiles = MutableStateFlow(PetFaceProfile.createDefaultSlots())
    val faceProfiles: StateFlow<List<PetFaceProfile>> = _faceProfiles.asStateFlow()

    private val _currentRecognizedPerson = MutableStateFlow<String?>("บอส")
    val currentRecognizedPerson: StateFlow<String?> = _currentRecognizedPerson.asStateFlow()

    var lastDetectedLandmarks: List<Float>? = null
    var onTriggerMissileBarrage: (() -> Unit)? = null

    // ─── Screensaver Idle Tricks Settings ──────────────────────────────────────
    private val _idleScreensaverDelaySeconds = MutableStateFlow(25)
    val idleScreensaverDelaySeconds: StateFlow<Int> = _idleScreensaverDelaySeconds.asStateFlow()

    fun setScreensaverDelay(seconds: Int) {
        _idleScreensaverDelaySeconds.value = seconds.coerceAtLeast(10)
    }

    // ─── 1. Idle Life & Gaze Wander System ─────────────────────────────────────
    private var idleJob: Job? = null
    private var lastInteractionTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    fun start() {
        logDebug(TAG, "🟢 start(): Starting PetModeController idle loop & vision hooks")
        activeInstance = this
        lastInteractionTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        // the user just opened it: the quiet starts now
        lastConversationAtMs = nowMs()
        lastReactionAtMs = lastConversationAtMs
        actedThisStretch = false
        setIdleStage(PetSelfCare.Stage.AWAKE)
        PetCustomPropStore.loadCustomProps()

        // Load PetMemory and restore NeedsState
        val mem = PetMemoryStore.load()
        if (mem.savedNeedsState.lastUpdateTimestamp > 0L) {
            val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - mem.savedNeedsState.lastUpdateTimestamp
            // app closed = pet asleep: energy recovers, hunger / dirt don't hit zero overnight
            _needsState.value = mem.savedNeedsState.decayOffline(elapsed)
            logDebug(TAG, "📦 Restored NeedsState (elapsed ${elapsed / 60000}min)")
        }

        // Hook up PetVisionBridge callbacks
        PetVisionBridge.registeredProfilesProvider = { _faceProfiles.value }
        PetVisionBridge.onCurrentFaceLandmarks = { landmarks -> lastDetectedLandmarks = landmarks }
        PetVisionBridge.onFaceProfileMatched = { name, _ -> _currentRecognizedPerson.value = name }
        PetVisionBridge.onHandGestureDetected = { gesture -> onHandGesture(gesture) }

        startIdleLoop()
        startAutoSave()
    }

    fun stop() {
        logDebug(TAG, "🔴 stop(): Stopping PetModeController")
        if (activeInstance === this) {
            activeInstance = null
        }
        idleJob?.cancel()
        idleJob = null
        autoSaveJob?.cancel()
        autoSaveJob = null
        stopFocusTimer()

        // Save memory before stopping
        PetMemoryStore.saveNeedsState(_needsState.value)
        PetMemoryStore.save()

        // Clear PetVisionBridge callbacks
        PetVisionBridge.registeredProfilesProvider = null
        PetVisionBridge.onCurrentFaceLandmarks = null
        PetVisionBridge.onFaceProfileMatched = null
        PetVisionBridge.onHandGestureDetected = null
    }

    fun notifyInteraction() {
        lastInteractionTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        PetVisionBridge.resetAbsenceTimer()
        val current = avatarStateProvider()
        // Cancel screensaver trick immediately on user touch/speech
        if (current.faceState.eyeTrick != com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.NONE) {
            onUpdateAvatarState(current.copy(
                faceState = current.faceState.copy(eyeTrickName = "none")
            ))
        }
    }

    // ─── Face Recognition Manager (5 Slots) ────────────────────────────────────
    fun enrollCurrentFace(slotIndex: Int, name: String): Boolean {
        val landmarks = lastDetectedLandmarks ?: return false
        val currentList = _faceProfiles.value.toMutableList()
        if (slotIndex !in currentList.indices) return false
        currentList[slotIndex] = PetFaceProfile(
            slotIndex = slotIndex,
            name = name.ifBlank { "สล็อต ${slotIndex + 1}" },
            isEnrolled = true,
            landmarkRatios = landmarks,
            enrolledAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
        _faceProfiles.value = currentList
        _currentRecognizedPerson.value = currentList[slotIndex].name
        RobotSoundPlayer.playSparkle()
        return true
    }

    fun deleteFaceProfile(slotIndex: Int) {
        val currentList = _faceProfiles.value.toMutableList()
        if (slotIndex in currentList.indices) {
            currentList[slotIndex] = PetFaceProfile(
                slotIndex = slotIndex,
                name = "สล็อต ${slotIndex + 1}",
                isEnrolled = false
            )
            _faceProfiles.value = currentList
        }
    }

    fun renameFaceProfile(slotIndex: Int, newName: String) {
        val currentList = _faceProfiles.value.toMutableList()
        if (slotIndex in currentList.indices) {
            currentList[slotIndex] = currentList[slotIndex].copy(name = newName.ifBlank { "สล็อต ${slotIndex + 1}" })
            _faceProfiles.value = currentList
        }
    }

    // ─── Hand Gesture Reactions ────────────────────────────────────────────────
    private var lastHandGestureTime: Long = 0L
    private var lastHandGestureType: HandGesture = HandGesture.NONE

    fun onHandGesture(gesture: HandGesture) {
        if (gesture == HandGesture.NONE) return
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        // Defensive cooldown: at least 3.0s between any gesture triggers
        if (now - lastHandGestureTime < 3000L) {
            return
        }
        // If same gesture is received repeatedly within 5.0s, ignore
        if (gesture == lastHandGestureType && (now - lastHandGestureTime < 5000L)) {
            return
        }

        lastHandGestureTime = now
        lastHandGestureType = gesture
        notifyInteraction()
        logDebug(TAG, "🖐️ [HandGesture] Received: ${gesture.name}")

        // gesture reactions are momentary: return to IDLE (only if nothing else took over),
        // otherwise the idle life (gaze wander, sleep, screensaver) stays frozen on this face.
        // showTransientEmotion bumps emotionGeneration so an older timer cannot end a newer reaction.
        val (emotion, status) = when (gesture) {
            HandGesture.HIGH_FIVE -> {
                RobotSoundPlayer.playHappy()
                _needsState.value = _needsState.value.interactLove()
                AvatarEmotion.HAPPY to "เย้! แปะมือกันฮับ High-Five! ✋✨"
            }
            HandGesture.OK -> {
                RobotSoundPlayer.playSparkle()
                _needsState.value = _needsState.value.interactLove()
                AvatarEmotion.EXCITED to "รับทราบฮับเจ้านาย! 👌✨"
            }
            HandGesture.BYE -> {
                RobotSoundPlayer.playPurr()
                AvatarEmotion.WINK to "บ๊ายบายฮับ แล้วรีบกลับมาหาน้องน้า 👋💕"
            }
            HandGesture.NO -> {
                RobotSoundPlayer.playConfused()
                AvatarEmotion.POUT to "ม่ายยย น้องไม่ได้ดื้อน้าา 🥺"
            }
            HandGesture.V_SIGN -> {
                RobotSoundPlayer.playHappy()
                _needsState.value = _needsState.value.interactLove()
                AvatarEmotion.EXCITED to "สู้ๆ ฮับ! ชูสองนิ้วเย้ๆ ✌️🎉"
            }
            HandGesture.THUMBS_UP -> {
                RobotSoundPlayer.playSparkle()
                _needsState.value = _needsState.value.interactLove()
                AvatarEmotion.HAPPY to "ขอบคุณฮับเจ้านาย ดีที่สุดเลย! 👍🥰"
            }
            HandGesture.THUMBS_DOWN -> {
                RobotSoundPlayer.playConfused()
                AvatarEmotion.SAD to "งื้ออ น้องขอโทษฮับ อย่าเพิ่งดุน้า 🥺👎"
            }
            HandGesture.NONE -> return
        }
        showTransientEmotion(emotion, status, 2800L)
    }

    /** Show an emotion for a moment, then return to IDLE if it is still the one on screen. */
    private fun showTransientEmotion(emotion: AvatarEmotion, statusText: String?, durationMs: Long) {
        lastReactionAtMs = nowMs()
        val generation = ++emotionGeneration
        onUpdateAvatarState(avatarStateProvider().copy(emotion = emotion, statusText = statusText))
        scope.launch {
            delay(durationMs)
            val cur = avatarStateProvider()
            if (generation == emotionGeneration && cur.emotion == emotion) {
                onUpdateAvatarState(cur.copy(emotion = AvatarEmotion.IDLE, statusText = null))
            }
        }
    }

    // ─── Pet Needs & Care Functions (StateMachine-Based) ─────────────────────
    // the robot's own scenes for them (eat / bath / play_ball in pet_godot), the same as when it sees to itself
    fun feedPet(specificFood: PropType? = null) {
        notifyInteraction()
        wakeUpFromIdle("บอสให้อาหาร", getUp = false)
        playScene(PetSceneArchetype.EATING, specificFood)
        val result = stateMachine.processTouch(InteractionType.FEED, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        // the scene plays its own timed sounds: drop the reaction's one-shot sound
        applyStateMachineResult(result.copy(soundAction = null), InteractionType.FEED)
        selfCare.done(PetSelfCare.Act.EAT, nowMs())
    }

    fun cleanPet() {
        notifyInteraction()
        wakeUpFromIdle("บอสพาอาบน้ำ", getUp = false)
        playScene(PetSceneArchetype.BATH_CLEAN)
        val result = stateMachine.processTouch(InteractionType.CLEAN, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        // the scene plays its own timed sounds: drop the reaction's one-shot sound
        applyStateMachineResult(result.copy(soundAction = null), InteractionType.CLEAN)
        selfCare.done(PetSelfCare.Act.BATHE, nowMs())
    }

    fun playWithPet() {
        notifyInteraction()
        wakeUpFromIdle("บอสชวนเล่น", getUp = false)
        playScene(PetSceneArchetype.PLAY_GAMING)
        val result = stateMachine.processTouch(InteractionType.PLAY, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        // the scene plays its own timed sounds: drop the reaction's one-shot sound
        applyStateMachineResult(result.copy(soundAction = null), InteractionType.PLAY)
        selfCare.done(PetSelfCare.Act.PLAY, nowMs())
    }

    /** Asked to sleep: off to sleep at once (eyes shut, the screen dark), unless it is not sleepy at all (it says so). */
    fun putToSleep() {
        val result = stateMachine.processTouch(InteractionType.SLEEP, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        if (result.emotion != AvatarEmotion.SLEEPING) {
            applyStateMachineResult(result, InteractionType.SLEEP, TouchZone.FACE_CENTER)
            return
        }
        result.needsUpdate?.let { _needsState.value = it(_needsState.value) }
        PetMemoryStore.logInteraction(InteractionType.SLEEP, TouchZone.FACE_CENTER, "sleeping")
        fallAsleep("บอสพาไปนอน")
    }

    // ─── Idle life (PetSelfCare) ───

    /**
     * Avatar mode, ten times a second. [active]: someone talks (the user or the AI), a tool runs, a result waits to
     * be told or a tool's scene is up — the quiet starts over, and a drowsy robot snaps awake. [userVoice]: the user
     * talks — that also wakes it from its sleep (the AI's own voice does not: its "ฝันดีฮับ" after "พาน้องนอน", an
     * alert in the night).
     */
    fun onAvatarTick(active: Boolean, userVoice: Boolean) {
        val now = nowMs()
        if (active) {
            lastConversationAtMs = now
            val stage = _idleStage.value
            if (stage == PetSelfCare.Stage.DROWSY || stage == PetSelfCare.Stage.EYELIDS || (stage == PetSelfCare.Stage.ASLEEP && userVoice)) {
                wakeUpFromIdle(if (userVoice) "มีเสียงพูด" else "AI พูด")
            }
        }
        idleTick(now)
    }

    /**
     * A finger on the screen (anywhere, any kind of touch): the quiet starts over. Asleep or drowsy, it wakes up
     * and the touch is spent on that — returns true (no tap reaction on top).
     */
    fun noteTouch(): Boolean {
        lastReactionAtMs = nowMs()
        if (_idleStage.value == PetSelfCare.Stage.AWAKE) return false
        wakeUpFromIdle("แตะหน้าจอ")
        return true
    }

    private fun setIdleStage(stage: PetSelfCare.Stage) {
        if (_idleStage.value == stage) return
        idleStageSinceMs = nowMs()
        _idleStage.value = stage
    }

    /** The quiet so far: 30 s its own act, then drowsy, eyelids and sleep ([PetSelfCare.nextStage]). */
    private fun idleTick(now: Long) {
        val stage = _idleStage.value
        if (stage == PetSelfCare.Stage.ASLEEP) return
        val current = avatarStateProvider()
        val quietFor = now - maxOf(lastConversationAtMs, lastReactionAtMs)
        if (quietFor < selfCare.playAfterMs) actedThisStretch = false
        val scene = current.faceState.sceneName
        // a demo on the screen (the moodset pages, the scene showcase) is not quiet time
        val demo = current.statusText?.startsWith("🎭") == true ||
            current.faceState.speechText?.let { it.startsWith("🎬") || it.startsWith("🎭") } == true
        // free: nothing of anyone else's on — its own scenes of the quiet may give way to the next one
        val free = !current.isSpeaking && !current.isDizzy && !demo && (scene.isBlank() || scene in OWN_SCENES)
        if (stage == PetSelfCare.Stage.AWAKE && !actedThisStretch && quietFor >= selfCare.playAfterMs && free &&
            scene.isBlank() && (current.emotion == AvatarEmotion.IDLE || current.emotion == AvatarEmotion.BORED)) {
            actedThisStretch = true
            selfCareAct(now)
            return
        }
        when (selfCare.nextStage(stage, quietFor, now - idleStageSinceMs)) {
            PetSelfCare.Stage.DROWSY -> if (stage == PetSelfCare.Stage.AWAKE && free) becomeDrowsy()
            PetSelfCare.Stage.EYELIDS -> if (stage == PetSelfCare.Stage.DROWSY) {
                logDebug(TAG, "😪 หนังตาหย่อน (เงียบ ${quietFor / 1000} วิ)")
                setIdleStage(PetSelfCare.Stage.EYELIDS)
            }
            PetSelfCare.Stage.ASLEEP -> if (stage == PetSelfCare.Stage.EYELIDS) fallAsleep("เงียบ ${quietFor / 1000} วิ")
            else -> {}
        }
    }

    /** 30 s into the quiet: it eats when hungry, bathes when dirty, otherwise plays by itself. */
    private fun selfCareAct(now: Long) {
        val n = _needsState.value
        val act = selfCare.act(n)
        logDebug(TAG, "🤖 ว่าง 30 วิ: ${act ?: "เหนื่อยเกินจะเล่น"} — อิ่ม ${n.satiety.toInt()}% พลังงาน ${n.energy.toInt()}% " +
            "สะอาด ${n.hygiene.toInt()}% สุข ${n.happiness.toInt()}%")
        when (act) {
            PetSelfCare.Act.EAT -> {
                _needsState.value = n.eatMeal()
                playSkit(AvatarSkits.EAT, item = SELF_FOODS.random())
            }
            PetSelfCare.Act.BATHE -> {
                _needsState.value = n.bathe()
                playSkit(AvatarSkits.BATH)
            }
            PetSelfCare.Act.PLAY -> {
                _needsState.value = n.playAlone()
                playSkit(listOf(AvatarSkits.PLAY_BALL, AvatarSkits.IDLE_PLAY).random())
            }
            null -> return
        }
        selfCare.done(act, now)
    }

    /** 60 s into the quiet: it nods off — the head drops, the eyes close (a looping scene until it sleeps or wakes). */
    private fun becomeDrowsy() {
        logDebug(TAG, "🥱 ง่วง สัปหงก — พลังงาน ${_needsState.value.energy.toInt()}%")
        val skit = AvatarSkits.DROWSY
        startScene(RobotFaceState(emotionName = skit.emotion.name.lowercase(), sceneName = skit.sceneName), skit.nameTh,
            skit.cues, durationMs = Long.MAX_VALUE, triggerMissileBarrage = false)
        setIdleStage(PetSelfCare.Stage.DROWSY)
    }

    /** Asleep: it lies down and sleeps (Avatar mode shuts the screen's eyelids and dims the screen) until woken. */
    fun fallAsleep(why: String) {
        if (isAsleep) return
        val n = _needsState.value
        logDebug(TAG, "😴 หลับ ($why) — พลังงาน ${n.energy.toInt()}% อิ่ม ${n.satiety.toInt()}%")
        val skit = AvatarSkits.SLEEP
        startScene(RobotFaceState(emotionName = skit.emotion.name.lowercase(), sceneName = skit.sceneName), skit.nameTh,
            skit.cues, durationMs = Long.MAX_VALUE, triggerMissileBarrage = false)
        selfCare.slept(nowMs())
        setIdleStage(PetSelfCare.Stage.ASLEEP)
    }

    /**
     * Awake again ([why] for the log): from sleep it gets up with its wake-up scene ([getUp]; Avatar mode brightens
     * the screen and opens the eyelids slowly), from drowsiness it just perks up. The quiet starts over.
     */
    fun wakeUpFromIdle(why: String, getUp: Boolean = true) {
        val stage = _idleStage.value
        if (stage == PetSelfCare.Stage.AWAKE) return
        activeSceneJob?.cancel()
        activeSceneEndJob?.cancel()
        lastReactionAtMs = nowMs()
        actedThisStretch = false
        logDebug(TAG, "⏰ ตื่น ($why) จาก ${stage.name} — พลังงาน ${_needsState.value.energy.toInt()}%")
        setIdleStage(PetSelfCare.Stage.AWAKE)
        if (stage == PetSelfCare.Stage.ASLEEP && getUp) {
            playSkit(AvatarSkits.WAKE)
        } else {
            onUpdateAvatarState(avatarStateProvider().copy(emotion = AvatarEmotion.IDLE, faceState = RobotFaceState(), statusText = null))
        }
    }

    /** What it did lately ("กิน 12 นาทีก่อน · นอน 40 นาทีก่อน"), for device_pet_care's status; "" = nothing lately. */
    fun selfCareNote(): String = selfCare.recent(nowMs())

    /**
     * นำผลลัพธ์จาก StateMachine มาแสดงผล (อัปเดต Needs, เสียง, Avatar State, และบันทึกลง PetMemory)
     */
    fun applyStateMachineResult(
        result: EmotionTransitionResult,
        type: InteractionType = InteractionType.FEED,
        zone: TouchZone = TouchZone.FACE_CENTER,
        logToMemory: Boolean = true
    ) {
        val current = avatarStateProvider()
        val isCatalogTest = current.statusText?.startsWith("🎭") == true ||
                current.faceState.speechText?.startsWith("🎭") == true
        if (isCatalogTest && type != InteractionType.FEED) {
            logDebug(TAG, "🛡️ applyStateMachineResult skipped emotion override — catalog test active (${current.statusText})")
            return
        }

        result.needsUpdate?.let { updateFn ->
            _needsState.value = updateFn(_needsState.value)
        }
        result.soundAction?.invoke()
        if (logToMemory) lastReactionAtMs = nowMs()

        // a shake or a call while it sleeps or dozes: it wakes into the reaction (the screen brightens, the eyelids
        // open) — out of its sleepy scene
        val wakes = logToMemory && _idleStage.value != PetSelfCare.Stage.AWAKE && result.emotion != AvatarEmotion.SLEEPING
        if (wakes) {
            activeSceneJob?.cancel()
            activeSceneEndJob?.cancel()
            actedThisStretch = false
            logDebug(TAG, "⏰ ตื่น (${type.name.lowercase()}) จาก ${_idleStage.value.name} — พลังงาน ${_needsState.value.energy.toInt()}%")
            setIdleStage(PetSelfCare.Stage.AWAKE)
        }
        val isDizzyNow = result.emotion == AvatarEmotion.DIZZY
        val generation = ++emotionGeneration
        onUpdateAvatarState(current.copy(
            emotion = result.emotion,
            isDizzy = isDizzyNow,
            statusText = result.statusText,
            faceState = if (wakes) RobotFaceState() else current.faceState
        ))

        if (logToMemory) {
            PetMemoryStore.logInteraction(type, zone, result.emotion.name.lowercase())
        }

        if (result.triggerMissileBarrage) {
            onTriggerMissileBarrage?.invoke()
        }

        if (result.emotion != AvatarEmotion.SLEEPING && result.durationMs < Long.MAX_VALUE) {
            scope.launch {
                delay(result.durationMs)
                // only the latest reaction may end itself: an earlier poke's timer must not
                // cut short a newer reaction that happens to show the same emotion
                if (generation == emotionGeneration && avatarStateProvider().emotion == result.emotion) {
                    onUpdateAvatarState(avatarStateProvider().copy(
                        emotion = AvatarEmotion.IDLE,
                        isDizzy = false,
                        statusText = null
                    ))
                }
            }
        }
    }

    /** Bumped on every state-machine reaction; revert timers check they are still the latest. */
    private var emotionGeneration = 0L

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(60_000L)
                PetMemoryStore.saveNeedsState(_needsState.value)
                PetMemoryStore.save()
            }
        }
    }

    /**
     * อัปเดตสถานะใบหน้าหุ่นยนต์แบบไดนามิก (Emotion, EyeStyle, BackgroundTheme, Props, Gesture)
     */
    fun updateRobotFace(state: RobotFaceState) {
        logDebug(TAG, "🎭 updateRobotFace: emotion=${state.emotion}, bg=${state.backgroundTheme}, props=${state.props}, gesture=${state.gesture}")
        notifyInteraction()
        val current = avatarStateProvider()
        onUpdateAvatarState(current.withFace(state))

        // Trigger reactive robot SFX matching props and emotions
        when {
            state.props.contains(com.skyliner2008.jarvis.ui.component.avatar.PropType.SPARKLES) -> RobotSoundPlayer.playSparkle()
            state.props.contains(com.skyliner2008.jarvis.ui.component.avatar.PropType.HEARTS) || state.emotion == AvatarEmotion.LOVE -> RobotSoundPlayer.playPurr()
            state.props.contains(com.skyliner2008.jarvis.ui.component.avatar.PropType.QUESTION_MARK) || state.emotion == AvatarEmotion.CONFUSED -> RobotSoundPlayer.playConfused()
            state.emotion == AvatarEmotion.EXCITED -> RobotSoundPlayer.playHappy()
            state.emotion == AvatarEmotion.ANGRY -> RobotSoundPlayer.playAlarm()
            else -> {}
        }
    }

    private var activeSceneJob: kotlinx.coroutines.Job? = null
    private var activeSceneEndJob: kotlinx.coroutines.Job? = null
    private var sceneCounter = 0

    /** มีฉาก (Pet Scene หรือเรื่องอารมณ์) กำลังเล่นอยู่ */
    val isScenePlaying: Boolean
        get() = avatarStateProvider().faceState.sceneName.isNotBlank()

    /**
     * สั่งเล่นฉากสำเร็จรูป (Smart Scene Archetype) พร้อมสุ่มหรือสลับไอเทมตามที่ระบุ
     * เล่นครบตาม durationMs แล้วคืนสู่โหมดปกติอย่างนุ่มนวล
     * @param label ข้อความบรรยายบนจอ (เช่นตอนเดโม่)
     * @return ความยาวของฉาก (ms)
     */
    fun playScene(
        archetype: PetSceneArchetype,
        specificProp: PropType? = null,
        durationMs: Long? = null,
        label: String? = null
    ): Long {
        val (faceState, spec) = PetSceneEngine.resolveScene(archetype, specificProp)
        return startArchetypeScene(faceState.copy(speechText = label), spec, durationMs)
    }

    /**
     * An archetype scene: when the robot has a skit for it (angry missile, super love, dramatic cry,
     * celebration …) the skit plays with its own sound timeline and length (its rockets replace the
     * missile barrage); otherwise the robot shows the scene's emotion and props with the scene's sounds.
     */
    private fun startArchetypeScene(faceState: RobotFaceState, spec: PetSceneSpec, durationMs: Long? = null): Long {
        val skit = AvatarSkits.forSceneName(faceState.sceneName)
        if (skit != null) {
            val duration = skit.durationMs + SCENE_TAIL_MS
            startScene(faceState, spec.nameTh, skit.cues, duration, triggerMissileBarrage = false)
            return duration
        }
        val duration = durationMs ?: spec.durationMs
        startScene(faceState, spec.nameTh, spec.cues.ifEmpty { listOf(SceneCue(0L, spec.sound)) },
            duration, spec.triggerMissileBarrage)
        return duration
    }

    /**
     * เล่นละครสั้นของหุ่น (pet_godot/scenes, ~10 วินาที) จนจบ พร้อมเสียงตามไทม์ไลน์ของฉาก
     * — ใช้จากปุ่มเดโม่และคำสั่งเสียง
     * @return ความยาวของฉาก (ms)
     */
    fun playSkit(skit: AvatarSkit, label: String? = null, item: String = ""): Long {
        val face = RobotFaceState(
            emotionName = skit.emotion.name.lowercase(),
            sceneName = skit.sceneName,
            speechText = label,
            sceneItem = item
        )
        val duration = skit.durationMs + SCENE_TAIL_MS
        startScene(face, skit.nameTh, skit.cues, duration, triggerMissileBarrage = false)
        return duration
    }

    /**
     * สั่งเล่นฉากจากชื่อหรือคีย์เวิร์ด (ไทยหรืออังกฤษ): ละครของหุ่น (ชื่อ/คำเฉพาะ) ก่อน แล้วค่อย Pet Scene
     * @return ชื่อฉากภาษาไทยที่เล่น หรือ null ถ้าไม่รู้จัก
     */
    fun playSceneByNameOrKeyword(keyword: String, specificProp: PropType? = null): String? {
        val skit = AvatarSkits.resolveKeyword(keyword)
        if (skit != null) {
            logDebug(TAG, "🎬 playSceneByNameOrKeyword: '$keyword' -> skit ${skit.id}")
            playSkit(skit)
            return skit.nameTh
        }
        val resolved = PetSceneEngine.resolveFromKeyword(keyword, specificProp)
        if (resolved != null) {
            val (faceState, spec) = resolved
            logDebug(TAG, "🎬 playSceneByNameOrKeyword: '$keyword' -> ${spec.archetype.name}")
            startArchetypeScene(faceState, spec)
            return spec.nameTh
        }
        logDebug(TAG, "⚠️ playSceneByNameOrKeyword: No match for '$keyword'")
        return null
    }

    /** หยุดฉากที่กำลังเล่น (เสียง + ตัวจับเวลา) และคืนหน้าปกติ */
    fun stopScene() {
        activeSceneJob?.cancel()
        activeSceneEndJob?.cancel()
        activeSceneJob = null
        activeSceneEndJob = null
        val current = avatarStateProvider()
        if (current.faceState.sceneName.isNotBlank()) {
            onUpdateAvatarState(current.copy(emotion = AvatarEmotion.IDLE, faceState = RobotFaceState()))
        }
    }

    /**
     * เริ่มฉาก: ใส่ sceneId ใหม่ (Rive เล่น story ตั้งแต่ต้นแม้เป็นฉากเดิม), เล่นเสียงตามไทม์ไลน์ของบท,
     * แล้วคืนหน้าปกติเมื่อจบ — ฉากใหม่ยกเลิกเสียง/ตัวจับเวลาของฉากเก่าทั้งหมด
     */
    private fun startScene(
        faceState: RobotFaceState,
        nameTh: String,
        cues: List<SceneCue>,
        durationMs: Long,
        triggerMissileBarrage: Boolean
    ) {
        val face = faceState.copy(sceneId = ++sceneCounter)
        logDebug(TAG, "🎬 startScene: ${face.sceneName} ($nameTh) item=${face.sceneItem} duration=${durationMs}ms cues=${cues.size}")

        notifyInteraction()
        activeSceneJob?.cancel()
        activeSceneEndJob?.cancel()
        onUpdateAvatarState(avatarStateProvider().withFace(face))

        if (triggerMissileBarrage) {
            onTriggerMissileBarrage?.invoke()
        }

        val sceneJob = scope.launch {
            var elapsed = 0L
            for (cue in cues.sortedBy { it.atMs }) {
                if (cue.atMs >= durationMs) break
                delay(cue.atMs - elapsed)
                elapsed = cue.atMs
                RobotSoundPlayer.play(cue.sound)
            }
        }
        activeSceneJob = sceneJob
        activeSceneEndJob = scope.launch {
            delay(durationMs)
            sceneJob.cancel()
            val current = avatarStateProvider()
            // คืนหน้าปกติเฉพาะเมื่อยังเป็นฉากนี้อยู่ (ไม่ทับหน้าที่ถูกตั้งใหม่ระหว่างฉาก)
            if (current.faceState.sceneId != face.sceneId) return@launch
            logDebug(TAG, "🎬 scene finished: ${face.sceneName} ($nameTh) after ${durationMs}ms")
            onUpdateAvatarState(current.copy(
                emotion = AvatarEmotion.IDLE,
                faceState = RobotFaceState()
            ))
        }
    }

    /**
     * เพิ่ม Dynamic Vector Prop ใหม่เข้าไปใน Face State และบันทึกถาวรลงฐานข้อมูล
     */
    fun addCustomProp(prop: DynamicVectorProp) {
        logDebug(TAG, "✨ addCustomProp: ${prop.name} (${prop.position})")
        notifyInteraction()
        PetCustomPropStore.saveCustomProp(prop)
        val current = avatarStateProvider()
        val currentFace = current.faceState
        val updatedProps = currentFace.customProps.filterNot { it.id == prop.id || it.name == prop.name } + prop
        val newFace = currentFace.copy(customProps = updatedProps)
        onUpdateAvatarState(current.copy(faceState = newFace))
        RobotSoundPlayer.playSparkle()
    }

    /**
     * ลบ Dynamic Vector Prop ตามชื่อหรือ ID
     */
    fun removeCustomProp(nameOrId: String) {
        logDebug(TAG, "🗑️ removeCustomProp: $nameOrId")
        notifyInteraction()
        val current = avatarStateProvider()
        val currentFace = current.faceState
        val updatedProps = currentFace.customProps.filterNot {
            it.id.equals(nameOrId, ignoreCase = true) || it.name.equals(nameOrId, ignoreCase = true)
        }
        val newFace = currentFace.copy(customProps = updatedProps)
        onUpdateAvatarState(current.copy(faceState = newFace))
    }

    /**
     * ล้าง Dynamic Vector Props ทั้งหมด
     */
    fun clearCustomProps() {
        logDebug(TAG, "🧹 clearCustomProps")
        notifyInteraction()
        val current = avatarStateProvider()
        val currentFace = current.faceState
        val newFace = currentFace.copy(customProps = emptyList())
        onUpdateAvatarState(current.copy(faceState = newFace))
    }

    /**
     * เปลี่ยนธีมฉากหลัง (BackgroundTheme)
     */
    fun setBackgroundTheme(theme: BackgroundTheme) {
        logDebug(TAG, "🌌 setBackgroundTheme: $theme")
        notifyInteraction()
        val current = avatarStateProvider()
        val currentFace = current.faceState
        val newFace = currentFace.copy(backgroundName = theme.name.lowercase())
        onUpdateAvatarState(current.copy(faceState = newFace))
    }

    /**
     * สลับการสวมใส่/ถอดอุปกรณ์เสริม (PropType)
     */
    fun toggleProp(prop: PropType) {
        logDebug(TAG, "✨ toggleProp: $prop")
        notifyInteraction()
        val current = avatarStateProvider()
        val currentFace = current.faceState
        val currentProps = currentFace.props.toMutableList()
        if (currentProps.contains(prop)) {
            currentProps.remove(prop)
        } else {
            currentProps.add(prop)
        }
        val propsString = currentProps.joinToString(",") { it.name.lowercase() }
        val newFace = currentFace.copy(propsRaw = propsString)
        onUpdateAvatarState(current.copy(faceState = newFace))
    }

    /**
     * ล้างอุปกรณ์เสริมมาตรฐานทั้งหมด
     */
    fun clearProps() {
        logDebug(TAG, "🧹 clearProps")
        notifyInteraction()
        val current = avatarStateProvider()
        val currentFace = current.faceState
        val newFace = currentFace.copy(propsRaw = "", customProps = emptyList())
        onUpdateAvatarState(current.copy(faceState = newFace))
    }

    /** สวมอุปกรณ์เสริมจากคลัง (ไม่ถอดถ้าใส่อยู่แล้ว ต่างจาก toggleProp) */
    fun wearStockProp(prop: PropType) {
        val current = avatarStateProvider()
        if (prop in current.faceState.props) return
        toggleProp(prop)
    }

    /** ถอดอุปกรณ์เสริมจากคลังเฉพาะชิ้น (ไม่ทำอะไรถ้าไม่ได้ใส่) */
    fun removeStockProp(prop: PropType) {
        val current = avatarStateProvider()
        if (prop !in current.faceState.props) return
        toggleProp(prop)
    }

    /** ปลุกจาก tool (ใช้กฎเดียวกับหงายจอขึ้น: หลับพอแล้วดีใจ ยังง่วงอยู่ก็หงุดหงิด) */
    fun wakeUpFromTool() {
        notifyInteraction()
        wakeUp()
    }

    /**
     * อัปเดตสถานะใบหน้าจาก JSON string หรือ formatted command "EMOTION|key=val|..." หรือ "CUSTOM_PROP|action=..."
     */
    fun updateRobotFace(commandOrJson: String) {
        if (commandOrJson.startsWith("CUSTOM_PROP|")) {
            val parts = commandOrJson.split("|")
            val args = mutableMapOf<String, String>()
            for (i in 1 until parts.size) {
                val kv = parts[i].split("=", limit = 2)
                if (kv.size == 2) {
                    args[kv[0].trim()] = kv[1].trim()
                }
            }
            val action = args["action"]?.lowercase() ?: "add"
            val name = args["name"] ?: "custom_prop"
            when (action) {
                "clear" -> clearCustomProps()
                "remove" -> removeCustomProp(name)
                "add" -> {
                    val svg = args["svg_path"]
                    if (!svg.isNullOrBlank()) {
                        val pos = try {
                            com.skyliner2008.jarvis.ui.component.avatar.PropPosition.valueOf(args["position"]?.uppercase() ?: "FOREHEAD")
                        } catch (_: Exception) { com.skyliner2008.jarvis.ui.component.avatar.PropPosition.FOREHEAD }
                        val anim = try {
                            com.skyliner2008.jarvis.ui.component.avatar.DynamicPropAnimation.valueOf(args["animation"]?.uppercase() ?: "FLOAT_BOB")
                        } catch (_: Exception) { com.skyliner2008.jarvis.ui.component.avatar.DynamicPropAnimation.FLOAT_BOB }
                        val prop = com.skyliner2008.jarvis.ui.component.avatar.DynamicVectorProp(
                            id = name,
                            name = name,
                            svgPath = svg,
                            fillColor = args["color"] ?: "#FFD700",
                            strokeColor = args["stroke_color"],
                            strokeWidth = args["stroke_width"]?.toFloatOrNull() ?: 0f,
                            position = pos,
                            sizeDp = args["size"]?.toFloatOrNull() ?: 0f,
                            animation = anim
                        )
                        addCustomProp(prop)
                    } else {
                        // Reuse from persistent store by name
                        val existing = PetCustomPropStore.findPropByNameOrId(name)
                        if (existing != null) {
                            val pos = args["position"]?.let {
                                try { com.skyliner2008.jarvis.ui.component.avatar.PropPosition.valueOf(it.uppercase()) } catch (_: Exception) { null }
                            } ?: existing.position
                            val anim = args["animation"]?.let {
                                try { com.skyliner2008.jarvis.ui.component.avatar.DynamicPropAnimation.valueOf(it.uppercase()) } catch (_: Exception) { null }
                            } ?: existing.animation
                            val size = args["size"]?.toFloatOrNull() ?: existing.sizeDp
                            val color = args["color"] ?: existing.fillColor
                            val prop = existing.copy(
                                position = pos,
                                animation = anim,
                                sizeDp = size,
                                fillColor = color
                            )
                            addCustomProp(prop)
                        }
                    }
                }
            }
            return
        }

        if (commandOrJson.startsWith("{")) {
            updateRobotFace(RobotFaceState.fromJson(commandOrJson))
        } else {
            val parts = commandOrJson.split("|")
            val emotionName = parts.firstOrNull() ?: "idle"
            val args = mutableMapOf("emotion" to emotionName)
            for (i in 1 until parts.size) {
                val kv = parts[i].split("=", limit = 2)
                if (kv.size == 2) {
                    args[kv[0].trim()] = kv[1].trim()
                }
            }
            updateRobotFace(RobotFaceState.fromArgs(args))
        }
    }

    private fun startIdleLoop() {
        idleJob?.cancel()
        idleJob = scope.launch {
            var lastDecayTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            var lastPassiveTime = 0L
            while (isActive) {
                delay(Random.nextLong(2800, 5200))

                val current = avatarStateProvider()
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val inactiveDuration = now - lastInteractionTime

                // ─── Tamagotchi Needs Decay & Passive Emotion Loop (Every ~20s) ───
                if (now - lastDecayTime >= 20_000L) {
                    val elapsed = now - lastDecayTime
                    // a long gap here means the process was frozen (Doze / clock change):
                    // decayLive treats the excess like offline time instead of hours of awake hunger
                    _needsState.value = _needsState.value.decayLive(
                        elapsed,
                        isSleeping = current.emotion == AvatarEmotion.SLEEPING
                    )
                    lastDecayTime = now

                    // State machine passive emotion check (e.g. hungry -> angry, bored, tired).
                    // At most once a minute: a hungry pet used to turn ANGRY again every 20 s.
                    // Not logged as an interaction: it used to be recorded as a "pat",
                    // inflating pats and timesGotAngry in PetMemory.
                    if (now - lastPassiveTime >= PASSIVE_EMOTION_COOLDOWN_MS && current.faceState.sceneName.isBlank()) {
                        val passiveResult = stateMachine.resolvePassiveEmotion(_needsState.value, current.emotion)
                        if (passiveResult != null) {
                            lastPassiveTime = now
                            applyStateMachineResult(passiveResult, InteractionType.PAT, TouchZone.FACE_CENTER, logToMemory = false)
                        }
                    }
                }


                // If busy, speaking or playing a scene, don't wander gaze abruptly
                if (current.isSpeaking || current.emotion == AvatarEmotion.LISTENING || current.isDizzy ||
                    current.faceState.sceneName.isNotBlank()
                ) {
                    continue
                }

                // naps (low energy, nobody around for 2.5 / 10 minutes) are PetSelfCare's: the robot lies down in its
                // sleep scene instead of only closing its eyes where it stands
                when {
                    // (yawning and nodding off are the drowsy scene's now: PetSelfCare, 60 s of quiet)
                    // Screensaver Eye Tricks: เมื่อปล่อยจอทิ้งไว้เกินกำหนด และอยู่ในสถานะ IDLE
                    inactiveDuration > (_idleScreensaverDelaySeconds.value * 1000L) &&
                    current.emotion == AvatarEmotion.IDLE &&
                    current.faceState.eyeTrick == com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.NONE -> {
                        val trick = listOf(
                            com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.PING_PONG_BOUNCE,
                            com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.TIRED_BOUNCE,
                            com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.SNOOKER_SHOT
                        ).random()

                        when (trick) {
                            com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.PING_PONG_BOUNCE -> RobotSoundPlayer.playSparkle()
                            com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.TIRED_BOUNCE -> RobotSoundPlayer.playConfused()
                            com.skyliner2008.jarvis.ui.component.avatar.EyeTrickState.SNOOKER_SHOT -> RobotSoundPlayer.playHappy()
                            else -> {}
                        }

                        onUpdateAvatarState(current.copy(
                            faceState = current.faceState.copy(eyeTrickName = trick.name.lowercase())
                        ))

                        delay(4500)
                        // รีเซ็ตลูกเล่นตากลับสู่ NONE ถ้ายังเล่นท่าเดิมอยู่
                        val afterTrick = avatarStateProvider()
                        if (afterTrick.faceState.eyeTrick == trick) {
                            onUpdateAvatarState(afterTrick.copy(
                                faceState = afterTrick.faceState.copy(eyeTrickName = "none")
                            ))
                        }
                    }
                    // สอดส่องสายตาแบบมีชีวิตชีวา (Idle Gaze Wander)
                    current.emotion == AvatarEmotion.IDLE -> {
                        val wanderX = Random.nextFloat() * 1.6f - 0.8f // -0.8..0.8
                        val wanderY = Random.nextFloat() * 0.8f - 0.4f // -0.4..0.4
                        onUpdateAvatarState(current.copy(
                            gazeOffsetX = wanderX,
                            gazeOffsetY = wanderY
                        ))
                        delay(Random.nextLong(1200, 2400))
                        // กลับมามองตรง
                        onUpdateAvatarState(avatarStateProvider().copy(
                            gazeOffsetX = 0f,
                            gazeOffsetY = 0f
                        ))
                    }
                }
            }
        }
    }

    private fun wakeUp() {
        val current = avatarStateProvider()
        val isCatalogTest = current.statusText?.startsWith("🎭") == true ||
                current.faceState.speechText?.startsWith("🎭") == true
        if (isCatalogTest) {
            logDebug(TAG, "🛡️ wakeUp() skipped — catalog test active (${current.statusText})")
            return
        }
        val result = stateMachine.processTouch(InteractionType.WAKE_UP, TouchZone.FACE_CENTER, _needsState.value, current.emotion)
        applyStateMachineResult(result, InteractionType.WAKE_UP, TouchZone.FACE_CENTER)
    }

    // ─── 2. Touch & Gesture Reactions ──────────────────────────────────────────

    /** ลูบหัว (Petting Forehead) */
    fun onPetHead(zone: TouchZone = TouchZone.FOREHEAD) {
        logDebug(TAG, "🐾 [Touch] Pet Head -> StateMachine")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.PET_HEAD, zone, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.PET_HEAD, zone)
    }

    /** เกาคาง (Chin Scratch) */
    fun onChinScratch() {
        logDebug(TAG, "🐾 [Touch] Chin Scratch -> StateMachine")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.CHIN_SCRATCH, TouchZone.CHIN, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.CHIN_SCRATCH, TouchZone.CHIN)
    }

    /** จิ้มแก้ม / แตะตัว (Poke / Tap) */
    fun onPoke(zone: TouchZone = TouchZone.LEFT_CHEEK) {
        logDebug(TAG, "🐾 [Touch] Cheek Poke -> StateMachine (zone=$zone)")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.POKE, zone, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.POKE, zone)
    }

    /** จั๊กจี้ (Double Tap / Tickle) */
    fun onTickle(zone: TouchZone = TouchZone.LEFT_CHEEK) {
        logDebug(TAG, "🐾 [Touch] Tickle -> StateMachine (zone=$zone)")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.TICKLE, zone, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.TICKLE, zone)
    }

    /** ตบเบาๆ กลางหน้า (Pat) */
    fun onPat() {
        logDebug(TAG, "🐾 [Touch] Pat -> StateMachine")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.PAT, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.PAT, TouchZone.FACE_CENTER)
    }

    /**
     * สไลด์นิ้วตามหน้าจอ / สายตาจากกล้อง (Gaze Following)
     * @param faceScaleFactor null = คงค่าเดิม — กล้องส่งระยะใบหน้าแยกผ่าน [onFaceDistance]
     *        (เดิมค่าเริ่มต้น 1f รีเซ็ตขนาดตาทุกเฟรมกล้อง ทำให้ตากระพริบขนาด)
     */
    fun onGazeTouch(normX: Float, normY: Float, faceScaleFactor: Float? = null) {
        notifyInteraction()
        val current = avatarStateProvider()
        if (current.emotion != AvatarEmotion.SLEEPING && !current.isDizzy) {
            onUpdateAvatarState(current.copy(
                gazeOffsetX = normX.coerceIn(-1f, 1f),
                gazeOffsetY = normY.coerceIn(-1f, 1f),
                faceScaleFactor = faceScaleFactor ?: current.faceScaleFactor
            ))
        }
    }

    /** ปรับขนาดดวงตาตามระยะห่างของใบหน้า (Face Distance Scale Factor) */
    fun onFaceDistance(scale: Float) {
        val current = avatarStateProvider()
        if (kotlin.math.abs(current.faceScaleFactor - scale) > 0.02f) {
            onUpdateAvatarState(current.copy(faceScaleFactor = scale))
        }
    }

    /** จัดการเมื่อใบหน้าหายไปจากระยะสายตาเกินเวลา (>45s -> BORED, >180s -> SLEEPING) */
    fun onFaceAbsenceTimeout(emotion: AvatarEmotion) {
        val current = avatarStateProvider()
        // ไม่ขัดจังหวะขณะกำลังคุย กำลังฟัง หรือกำลังรันฉาก/แคตตาล็อกทดสอบ
        val isCatalogTest = current.statusText?.startsWith("🎭") == true ||
                current.faceState.speechText?.startsWith("🎭") == true
        if (current.isSpeaking ||
            current.emotion == AvatarEmotion.LISTENING ||
            current.emotion == AvatarEmotion.SPEAKING ||
            isCatalogTest) {
            return
        }

        if (emotion == AvatarEmotion.SLEEPING) {
            // หากพลังงานยังสูง (> 30f) จะไม่ถูกบังคับหลับลึกเพียงเพราะมองไม่เห็นหน้า
            // แต่จะเปลี่ยนเป็น BORED (เหงา/รอบอส)
            if (_needsState.value.energy > 30f) {
                if (current.emotion == AvatarEmotion.IDLE) {
                    logDebug(TAG, "😴 Face absence reached sleep timeout, but energy is high (${_needsState.value.energy}%) -> transitioning to BORED")
                    onUpdateAvatarState(current.copy(
                        emotion = AvatarEmotion.BORED,
                        statusText = "บอสหายไปไหนน้า... น้องเหงาแล้ว 🥺"
                    ))
                }
                return
            }
            // พลังงานต่ำ: เหงาเหมือนกัน — sleep comes from the quiet (PetSelfCare: 2 minutes without a voice or a
            // touch), not from an empty camera: the user may be talking from out of its view
            if (current.emotion == AvatarEmotion.IDLE && current.faceState.sceneName.isBlank()) {
                logDebug(TAG, "😴 Face absent, low energy (${_needsState.value.energy}%) -> BORED (sleep follows the quiet)")
                onUpdateAvatarState(current.copy(
                    emotion = AvatarEmotion.BORED,
                    statusText = "บอสหายไปไหนน้า... น้องง่วงแล้ว 🥱"
                ))
            }
        } else if (emotion == AvatarEmotion.BORED) {
            if (current.emotion == AvatarEmotion.IDLE) {
                onUpdateAvatarState(current.copy(
                    emotion = AvatarEmotion.BORED,
                    statusText = "บอสอยู่ไหนน้าา... 👀"
                ))
            }
        }
    }


    /** แสดงอาการวิงเวียนเมื่อถูกเขย่า (Dizzy Spiral Eyes) */
    fun onDizzy() {
        logDebug(TAG, "🌀 [Motion] onDizzy() -> StateMachine SHAKE")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.SHAKE, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.SHAKE, TouchZone.FACE_CENTER)
    }

    /** เขย่าแรง / เขย่ารัวๆ (Heavy Shake -> Angry / Enraged) */
    fun onHeavyShake() {
        logDebug(TAG, "⚡ [Motion] onHeavyShake() -> StateMachine HEAVY_SHAKE")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.HEAVY_SHAKE, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.HEAVY_SHAKE, TouchZone.FACE_CENTER)
    }

    /** เอียงมือถือไปมา เหมือนนั่งเรือ (Boat Rocking -> Seasick Dizzy) */
    fun onBoatRocking() {
        logDebug(TAG, "⛵ [Motion] onBoatRocking() -> StateMachine BOAT_ROCKING")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.BOAT_ROCKING, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.BOAT_ROCKING, TouchZone.FACE_CENTER)
    }

    /** ทุบโต๊ะ / แรงสะเทือนเฉียบพลัน (Table Thump -> Surprised) */
    fun onTableThump() {
        logDebug(TAG, "💥 [Motion] onTableThump() -> StateMachine TABLE_THUMP")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.TABLE_THUMP, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.TABLE_THUMP, TouchZone.FACE_CENTER)
    }

    /** เสียงดัง / ตะโกน / ตะคอก (Loud Noise -> Surprised or Sad) */
    private var lastLoudNoiseTime = 0L

    fun onLoudNoise(audioLevel: Float = 0.8f) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (now - lastLoudNoiseTime < LOUD_NOISE_COOLDOWN_MS) return
        lastLoudNoiseTime = now
        logDebug(TAG, "📢 [Audio] onLoudNoise(level=$audioLevel) -> StateMachine LOUD_NOISE")
        notifyInteraction()
        val result = stateMachine.processTouch(InteractionType.LOUD_NOISE, TouchZone.FACE_CENTER, _needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.LOUD_NOISE, TouchZone.FACE_CENTER)
    }

    /** โกรธจัด สู้กลับ ยิงจรวดมิสซาย (Fight Back) */
    fun triggerFightBack() {
        logDebug(TAG, "🚀 [Combat] triggerFightBack() -> StateMachine FIGHT_BACK")
        notifyInteraction()
        val result = stateMachine.resolveFightBack(_needsState.value, avatarStateProvider().emotion)
        applyStateMachineResult(result, InteractionType.FIGHT_BACK, TouchZone.FACE_CENTER)
    }

    /** พลิกคว่ำหน้าจอ -> หลับพักผ่อน */
    fun onFaceDown() {
        logDebug(TAG, "😴 [Motion] onFaceDown() -> Emotion: SLEEPING, Sound: Yawn/Snore")
        onUpdateAvatarState(avatarStateProvider().copy(
            emotion = AvatarEmotion.SLEEPING,
            statusText = "คว่ำหน้าจอ -> หลับพักผ่อน Zzz..."
        ))
        RobotSoundPlayer.playYawn()
        scope.launch {
            delay(1200)
            if (avatarStateProvider().emotion == AvatarEmotion.SLEEPING) {
                RobotSoundPlayer.playSnore()
            }
        }
    }

    /** พลิกหน้าจอขึ้น -> ตื่น */
    fun onFaceUp() {
        logDebug(TAG, "☀️ [Motion] onFaceUp() -> WakeUp")
        wakeUp()
    }

    /** หน้าบูด แก้มป่อง */
    fun onPout() {
        notifyInteraction()
        RobotSoundPlayer.playConfused()
        showTransientEmotion(AvatarEmotion.POUT, "งอนแล้วน้าา... Hmph! 😤", 2500L)
    }

    /** แอบขยิบตา */
    fun onWink() {
        notifyInteraction()
        RobotSoundPlayer.playHappy()
        showTransientEmotion(AvatarEmotion.WINK, "แอบขยิบตาให้คุณน้าา 😉✨", 2200L)
    }

    // ─── Mini-Game: Copycat Face Mimic ─────────────────────────────────────────
    private val _copycatTarget = MutableStateFlow<AvatarEmotion?>(null)
    val copycatTarget: StateFlow<AvatarEmotion?> = _copycatTarget.asStateFlow()

    fun startCopycatGame() {
        notifyInteraction()
        val challenges = listOf(AvatarEmotion.HAPPY, AvatarEmotion.WINK)
        val picked = challenges.random()
        _copycatTarget.value = picked
        RobotSoundPlayer.playSurprise()
        val text = when (picked) {
            AvatarEmotion.HAPPY -> "เกมเลียนแบบหน้า: ยิ้มกว้างๆ ให้น้องดูหน่อยค่ะ! 😊"
            AvatarEmotion.WINK -> "เกมเลียนแบบหน้า: ขยิบตาข้างเดียวแข่งกันหน่อยค่ะ! 😉"
            else -> "เกมเลียนแบบหน้า: ทำหน้าตามน้องน้า!"
        }
        onUpdateAvatarState(avatarStateProvider().copy(
            emotion = picked,
            statusText = text
        ))
    }

    fun onUserMimicSuccess() {
        if (_copycatTarget.value == null) return
        logDebug(TAG, "🎉 [Copycat] Mimic success! Challenge: ${_copycatTarget.value}")
        _copycatTarget.value = null
        RobotSoundPlayer.playWakeUp()
        showTransientEmotion(AvatarEmotion.EXCITED, "🎉 ถูกต้องค่าา! เก่งมากกก ชนะเกมเลียนแบบแล้ว! ✨", 3000L)
    }

    // ─── 3. Desk Sentry Mode ───────────────────────────────────────────────────
    private val _isSentryActive = MutableStateFlow(false)
    val isSentryActive: StateFlow<Boolean> = _isSentryActive.asStateFlow()

    fun toggleSentry(active: Boolean? = null) {
        val target = active ?: !_isSentryActive.value
        _isSentryActive.value = target
        logDebug(TAG, "🛡️ [Sentry] toggleSentry -> active=$target")
        if (target) {
            RobotSoundPlayer.playAlarm()
            onUpdateAvatarState(avatarStateProvider().copy(
                emotion = AvatarEmotion.ANGRY,
                statusText = "🚨 โหมดสายตรวจเฝ้าโต๊ะกำลังทำงาน (ตรวจจับผู้บุกรุก)..."
            ))
        } else {
            RobotSoundPlayer.playHappy()
            val offText = "ปิดโหมดสายตรวจแล้วค่ะ"
            onUpdateAvatarState(avatarStateProvider().copy(emotion = AvatarEmotion.IDLE, statusText = offText))
            scope.launch {
                delay(3000L)
                val cur = avatarStateProvider()
                if (cur.statusText == offText) onUpdateAvatarState(cur.copy(statusText = null))
            }
        }
    }

    fun triggerIntruderAlert() {
        if (!_isSentryActive.value) return
        logDebug(TAG, "🚨 [Sentry] Intruder alert triggered! Sound: Alarm, Emotion: ANGRY")
        RobotSoundPlayer.playAlarm()
        onUpdateAvatarState(avatarStateProvider().copy(
            emotion = AvatarEmotion.ANGRY,
            statusText = "🚨 ตรวจพบสิ่งมีชีวิตหน้าโต๊ะทำงาน! ผู้บุกรุกยืนยันตัวตนด่วน!"
        ))
        scope.launch {
            onSentryIntruderAlert?.invoke()
        }
    }

    // ─── 4. Focus Buddy (Pomodoro Timer) ───────────────────────────────────────
    private val _focusTotalSeconds = MutableStateFlow(25 * 60)
    val focusTotalSeconds: StateFlow<Int> = _focusTotalSeconds.asStateFlow()

    private val _focusRemainingSeconds = MutableStateFlow(25 * 60)
    val focusRemainingSeconds: StateFlow<Int> = _focusRemainingSeconds.asStateFlow()

    private val _isFocusRunning = MutableStateFlow(false)
    val isFocusRunning: StateFlow<Boolean> = _isFocusRunning.asStateFlow()

    private var focusJob: Job? = null

    fun setFocusDuration(minutes: Int) {
        stopFocusTimer()
        _focusTotalSeconds.value = minutes * 60
        _focusRemainingSeconds.value = minutes * 60
    }

    fun toggleFocusTimer() {
        if (_isFocusRunning.value) {
            pauseFocusTimer()
        } else {
            startFocusTimer()
        }
    }

    private fun startFocusTimer() {
        _isFocusRunning.value = true
        RobotSoundPlayer.playHappy()
        onUpdateAvatarState(avatarStateProvider().copy(
            emotion = AvatarEmotion.THINKING,
            statusText = "🧠 โฟกัสเวลากันค่ะ สู้ๆ นะคะ!"
        ))
        focusJob = scope.launch {
            while (isActive && _focusRemainingSeconds.value > 0) {
                delay(1000)
                _focusRemainingSeconds.value -= 1
            }
            if (_focusRemainingSeconds.value <= 0) {
                onFocusComplete()
            }
        }
    }

    fun pauseFocusTimer() {
        _isFocusRunning.value = false
        focusJob?.cancel()
        focusJob = null
        RobotSoundPlayer.playConfused()
        val cur = avatarStateProvider()
        onUpdateAvatarState(cur.copy(
            emotion = if (cur.emotion == AvatarEmotion.THINKING) AvatarEmotion.IDLE else cur.emotion,
            statusText = "⏸️ พักการจับเวลาชั่วคราวค่ะ"
        ))
    }

    fun resetFocusTimer() {
        stopFocusTimer()
        _focusRemainingSeconds.value = _focusTotalSeconds.value
        onUpdateAvatarState(avatarStateProvider().copy(
            statusText = "รีเซ็ตเวลาโฟกัสเรียบร้อยแล้วค่ะ"
        ))
    }

    private fun stopFocusTimer() {
        _isFocusRunning.value = false
        focusJob?.cancel()
        focusJob = null
    }

    private fun onFocusComplete() {
        _isFocusRunning.value = false
        RobotSoundPlayer.playWakeUp()
        showTransientEmotion(AvatarEmotion.EXCITED, "🎉 ยอดเยี่ยมมากค่ะ! ครบเวลาโฟกัสแล้ว ได้เวลาพักผ่อนแล้วนะคะ!", 5000L)
    }

    // ─── 5. Mini-Games: Fortune Oracle ─────────────────────────────────────────
    private val oraclePredictions = listOf(
        "🔮 เซียมซีบอกว่า: วันนี้กราฟเป็นใจ ถือออเดอร์แล้วใจนิ่ง กำไรจะมาหาคุณเองค่ะ!",
        "🔮 เซียมซีบอกว่า: อย่าลืมจิบน้ำและยืดเส้นยืดสายบ้างน้า จาร์วิสเป็นห่วงค่ะ",
        "🔮 เซียมซีบอกว่า: วันนี้สมองของคุณเฉียบคมเป็นพิเศษ ลุยโปรเจกต์ต่อได้เลย!",
        "🔮 เซียมซีบอกว่า: ระวัง FOMO นะคะ รอสัญญาณคอนเฟิร์มสวยๆ ก่อนค่อยเข้าทำกำไร",
        "🔮 เซียมซีบอกว่า: สัตว์เลี้ยงตัวนี้ส่งพลังใจให้คุณ 100% วันนี้จะราบรื่นแน่นอนค่ะ ✨",
        "🔮 เซียมซีบอกว่า: ถ้าเหนื่อยนัก ลองพักสายตา 5 นาที แล้วค่อยกลับมาสู้นะคะ"
    )

    private val _fortuneText = MutableStateFlow<String?>(null)
    val fortuneText: StateFlow<String?> = _fortuneText.asStateFlow()

    fun drawFortune() {
        notifyInteraction()
        RobotSoundPlayer.playSurprise()
        val picked = oraclePredictions.random()
        _fortuneText.value = picked
        showTransientEmotion(AvatarEmotion.EXCITED, picked, 6000L)
    }
}
