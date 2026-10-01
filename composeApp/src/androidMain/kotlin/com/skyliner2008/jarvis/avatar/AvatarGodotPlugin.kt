package com.skyliner2008.jarvis.avatar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

/** The Messenger link between the app (main process) and the robot's engine (`:avatar` process). */
internal object AvatarLinkProtocol {
    /** plugin -> app: I am here, reply to [Message.replyTo] */
    const val MSG_ATTACH = 1
    /** app -> plugin: one JSON command ([KEY_JSON]) */
    const val MSG_COMMAND = 2
    /** plugin -> app: an engine event ([KEY_NAME], [KEY_PAYLOAD] JSON) */
    const val MSG_EVENT = 3

    const val KEY_JSON = "json"
    const val KEY_NAME = "name"
    const val KEY_PAYLOAD = "payload"
}

/**
 * The app's Godot plugin "JarvisAvatar" (declared in the manifest, loaded by the engine inside
 * GodotService in the `:avatar` process). GDScript (pet_godot/scripts/app_bridge.gd) gets the app's
 * commands on the `command` signal and sends events with sendEvent(name, json); this class carries
 * both over to the app through [AvatarLinkService].
 */
class AvatarGodotPlugin(godot: Godot) : GodotPlugin(godot) {
    private val main = Handler(Looper.getMainLooper())
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == AvatarLinkProtocol.MSG_COMMAND) {
            msg.data.getString(AvatarLinkProtocol.KEY_JSON)?.let { deliver(it) }
        }
        true
    })
    /** set on the main thread, read on Godot's render thread (sendEvent) */
    @Volatile private var link: Messenger? = null
    private var bound = false
    /** GDScript said hello: commands can go out as signals (before that they wait here) */
    private var scriptReady = false
    private val waiting = ArrayDeque<String>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            link = Messenger(service)
            sendToApp(AvatarLinkProtocol.MSG_ATTACH, Bundle())
            Log.i(TAG, "linked to the app")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            link = null
        }
    }

    init {
        val ctx = godot.context
        bound = try {
            ctx.bindService(Intent(ctx, AvatarLinkService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.w(TAG, "cannot bind the app link: ${e.message}")
            false
        }
    }

    override fun getPluginName(): String = NAME

    override fun getPluginSignals(): Set<SignalInfo> = setOf(SignalInfo("command", String::class.java))

    /** GDScript -> app. "hello" also means the script is listening now. */
    @UsedByGodot
    fun sendEvent(name: String, payload: String) {
        if (name == "hello") main.post {
            scriptReady = true
            while (waiting.isNotEmpty()) emitSignal("command", waiting.removeFirst())
        }
        sendToApp(AvatarLinkProtocol.MSG_EVENT, Bundle().apply {
            putString(AvatarLinkProtocol.KEY_NAME, name)
            putString(AvatarLinkProtocol.KEY_PAYLOAD, payload)
        })
    }

    /** Android back inside the robot's surface: the app closes Avatar mode (the engine must not quit). */
    override fun onMainBackPressed(): Boolean {
        sendToApp(AvatarLinkProtocol.MSG_EVENT, Bundle().apply {
            putString(AvatarLinkProtocol.KEY_NAME, "back")
            putString(AvatarLinkProtocol.KEY_PAYLOAD, "{}")
        })
        return true
    }

    override fun onMainDestroy() = unbind()

    override fun onGodotTerminating() = unbind()

    private fun deliver(json: String) {
        if (scriptReady) {
            emitSignal("command", json)
        } else {
            if (waiting.size >= MAX_WAITING) waiting.removeFirst()
            waiting.addLast(json)
        }
    }

    private fun sendToApp(what: Int, data: Bundle) {
        val target = link ?: return
        try {
            target.send(Message.obtain(null, what).apply {
                this.data = data
                replyTo = inbox
            })
        } catch (e: RemoteException) {
            Log.w(TAG, "the app is gone: ${e.message}")
            link = null
        }
    }

    private fun unbind() {
        if (!bound) return
        bound = false
        try {
            godot.context.unbindService(connection)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val NAME = "JarvisAvatar"
        private const val TAG = "AvatarGodotPlugin"
        private const val MAX_WAITING = 64
    }
}
