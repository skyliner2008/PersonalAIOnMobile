package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.logDebug
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** An event from the Godot robot: hello, ready, touch {kind, zone}, scene {id, state}, back. */
data class AvatarEvent(val name: String, val data: JsonObject) {
    /** Text of a field (touch kind / zone, scene id …), "" when missing. */
    fun text(key: String): String = (data[key] as? JsonPrimitive)?.content ?: ""
}

/**
 * The Godot robot's stage — the app's only avatar engine (pet_godot/). Android runs the engine in its
 * own `:avatar` process and shows it over the app while Avatar mode is open; this object is the app's
 * side of that link: [show] / [hide], commands in the robot's words (pet_godot/scripts/app_bridge.gd)
 * and the robot's [events].
 */
object AvatarStage {
    private const val TAG = "AvatarStage"

    /** Platform: open (true) or close (false) the 3D stage over the app. */
    var platformShow: ((Boolean) -> Unit)? = null

    /** Platform: deliver one JSON command to the engine (the platform queues it until the engine is up). */
    var platformSend: ((String) -> Unit)? = null

    /** Platform: can this device run the stage (Android 11+ with the robot packed into the app). */
    var platformAvailable: () -> Boolean = { false }

    /** Platform: engine ready status changed (e.g. for crossfading stage container). */
    var platformReady: ((Boolean) -> Unit)? = null

    private val _events = MutableSharedFlow<AvatarEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AvatarEvent> = _events.asSharedFlow()

    private val _ready = MutableStateFlow(false)
    /** The engine is up and warmed up (its first frames are on screen). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var lastState: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    val isAvailable: Boolean get() = platformAvailable()

    fun show() {
        logDebug(TAG, "show")
        platformShow?.invoke(true)
    }

    fun hide() {
        logDebug(TAG, "hide")
        _ready.value = false
        platformReady?.invoke(false)
        lastState = null
        platformShow?.invoke(false)
    }

    /** Logged (but not gaze and voice, many a second; a tool's data only by name), so a test log shows what the robot was told. */
    private fun send(command: JsonObject) {
        when ((command["t"] as? JsonPrimitive)?.content) {
            "look", "voice", "hear", "tasks" -> Unit
            "tool" -> logDebug(TAG, "→ tool ${(command["tool"] as? JsonPrimitive)?.content}")
            else -> logDebug(TAG, "→ $command")
        }
        platformSend?.invoke(command.toString())
    }

    /** The whole look; kept, and sent again when a new engine attaches. */
    fun sendState(look: AvatarLook) {
        val command = buildJsonObject {
            put("t", "state")
            put("emotion", look.emotion)
            put("eyes", look.eyes)
            put("items", buildJsonArray { look.items.forEach { add(it) } })
            put("background", look.background)
            put("emote", look.emote)
        }.toString()
        lastState = command
        logDebug(TAG, "→ $command")
        platformSend?.invoke(command)
    }

    /** Gaze, -1..1 (x right, y up). */
    fun look(x: Float, y: Float) = send(buildJsonObject {
        put("t", "look"); put("x", x.coerceIn(-1f, 1f)); put("y", y.coerceIn(-1f, 1f))
    })

    fun voice(speaking: Boolean, level: Float) = send(buildJsonObject {
        put("t", "voice"); put("speaking", speaking); put("level", level.coerceIn(0f, 1f))
    })

    /** A body reaction: shake / heavy_shake / boat / thump / face_up / loud / poke. */
    fun react(name: String) = send(buildJsonObject { put("t", "react"); put("name", name) })

    /**
     * A tool's result on the scene made for that tool; [result] is the tool's data (a map, a list or text).
     * [hold]: the scene waits on its last picture until [releaseScene] (the AI is still talking about it).
     */
    fun showTool(tool: String, result: JsonElement, hold: Boolean = false) = send(buildJsonObject {
        put("t", "tool"); put("tool", tool); put("result", result)
        if (hold) put("hold", true)
    })

    /** The AI finished talking about the held tool scene: it plays to its end. */
    fun releaseScene() = send(buildJsonObject { put("t", "release") })

    /** Scene [id] with [data] (null: its own); the same id again with data while it plays gives it new data. */
    fun playScene(id: String, data: JsonObject? = null) = send(buildJsonObject {
        put("t", "scene"); put("id", id)
        if (data != null) put("data", data)
    })

    /** The robot's task list ([AvatarTasks]): the jobs the user still waits to hear about; empty hides it. */
    fun tasks(items: kotlinx.serialization.json.JsonArray) = send(buildJsonObject { put("t", "tasks"); put("items", items) })

    /** The user's voice level 0..1 while they talk to the AI (the listening scene's meter). */
    fun hear(level: Float) = send(buildJsonObject { put("t", "hear"); put("level", level.coerceIn(0f, 1f)) })

    /** Ends scene [id] if it is still the one playing (a tool's scene that took over keeps going); "" = any. */
    fun stopScene(id: String = "") = send(buildJsonObject { put("t", "stop"); if (id.isNotEmpty()) put("id", id) })

    fun say(text: String, secs: Float = 3f) = send(buildJsonObject {
        put("t", "say"); put("text", text); put("secs", secs)
    })

    fun effect(name: String) = send(buildJsonObject { put("t", "effect"); put("name", name) })

    fun fidget(name: String = "") = send(buildJsonObject { put("t", "fidget"); put("name", name) })

    /** How open the screen's eyelids were last asked to be (1 = none), for a new engine. */
    private var lidsOpen = 1f

    /**
     * The screen's eyelids (the robot's sleep, PetSelfCare): from top and bottom, [open] 1 = none … 0 = shut, over
     * [secs]. Kept, and sent again when a new engine attaches.
     */
    fun eyelids(open: Float, secs: Float) {
        lidsOpen = open.coerceIn(0f, 1f)
        send(buildJsonObject { put("t", "eyelids"); put("open", lidsOpen); put("secs", secs) })
    }

    /** Heavy eyelids for [secs]: they sag and blink, each time a little more shut (90 s of quiet until sleep). */
    fun eyelidsDrowsy(secs: Float) {
        lidsOpen = 0.2f
        send(buildJsonObject { put("t", "eyelids"); put("drowsy", secs) })
    }

    /** Platform: the engine stopped (the app went to the background, or it is being restarted). */
    fun onEngineStopped() {
        _ready.value = false
        platformReady?.invoke(false)
    }

    private val _failed = MutableStateFlow(false)
    /** The engine kept dying and the platform gave up: Avatar mode shows the robot's face instead. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Platform: see [failed]. */
    fun onEngineFailed() {
        logDebug(TAG, "engine failed for good")
        _ready.value = false
        _failed.value = true
        platformReady?.invoke(false)
    }

    /** Platform: a new engine attached to the link — it gets the current look first (and shut eyelids stay shut). */
    fun onEngineAttached() {
        lastState?.let { platformSend?.invoke(it) }
        if (lidsOpen < 1f) {
            platformSend?.invoke(buildJsonObject { put("t", "eyelids"); put("open", lidsOpen); put("secs", 0f) }.toString())
        }
    }

    /** Platform: an event from the engine. */
    fun dispatchEvent(name: String, payload: String?) {
        val data = try {
            payload?.takeIf { it.isNotBlank() }?.let { json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }
        if (name == "ready") {
            _ready.value = true
            platformReady?.invoke(true)
        }
        logDebug(TAG, "event $name $data")
        _events.tryEmit(AvatarEvent(name, data))
    }
}
