package com.skyliner2008.jarvis.avatar

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log

/**
 * The app's end of the robot link (main process). The engine's plugin ([AvatarGodotPlugin], `:avatar`
 * process) binds here, says MSG_ATTACH and gets the app's commands; its events go to [AvatarStage].
 */
class AvatarLinkService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            AvatarLinkProtocol.MSG_ATTACH -> msg.replyTo?.let { AvatarLink.attach(it) }
            AvatarLinkProtocol.MSG_EVENT -> AvatarStage.dispatchEvent(
                msg.data.getString(AvatarLinkProtocol.KEY_NAME) ?: "",
                msg.data.getString(AvatarLinkProtocol.KEY_PAYLOAD)
            )
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        AvatarLink.detach()
        return false
    }
}

/**
 * Commands on their way to the robot's engine (main thread). Until an engine attaches they wait here,
 * except the look ("state"): [AvatarStage] keeps the latest and hands it to every new engine first.
 */
internal object AvatarLink {
    private const val TAG = "AvatarLink"
    private const val MAX_PENDING = 64
    private val main = Handler(Looper.getMainLooper())
    private var engine: Messenger? = null
    private val pending = ArrayDeque<String>()
    /** Called when an engine's link drops (stopped on purpose, or it died). */
    var onEngineGone: (() -> Unit)? = null

    /** Connects [AvatarStage] to this link (once, at app start). */
    fun install() {
        AvatarStage.platformSend = { json -> main.post { send(json) } }
    }

    fun attach(messenger: Messenger) {
        Log.i(TAG, "engine attached")
        engine = messenger
        AvatarStage.onEngineAttached()        // the current look first (posted, so it goes before…)
        main.post {                           // …what waited meanwhile
            while (pending.isNotEmpty() && engine != null) send(pending.removeFirst())
        }
    }

    /**
     * Avatar mode closed: what waited for an engine belongs to that visit, not the next one. Posted
     * like [send], so commands already on their way are dropped too.
     */
    fun clearPending() {
        main.post { pending.clear() }
    }

    fun detach() {
        Log.i(TAG, "engine detached")
        engine = null
        pending.clear()
        onEngineGone?.invoke()
    }

    private fun send(json: String) {
        val target = engine
        if (target == null) {
            if (json.startsWith("{\"t\":\"state\"")) return          // the stage keeps the latest look
            if (pending.size >= MAX_PENDING) pending.removeFirst()
            pending.addLast(json)
            return
        }
        try {
            target.send(Message.obtain(null, AvatarLinkProtocol.MSG_COMMAND).apply {
                data = Bundle().apply { putString(AvatarLinkProtocol.KEY_JSON, json) }
            })
        } catch (e: RemoteException) {
            Log.w(TAG, "engine gone: ${e.message}")
            detach()
        }
    }
}
