package com.skyliner2008.jarvis.avatar

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.godotengine.godot.service.RemoteGodotFragment

/**
 * Shows the Godot robot over the app while Avatar mode is open. The engine runs in GodotService in its
 * own `:avatar` process (Godot 4.7's remote engine: it draws into a SurfaceControlViewHost that the
 * RemoteGodotFragment's SurfaceView shows, touches included). Stopping the engine ends that process, so
 * every start is a fresh engine and the app never has to restart one in its own process.
 *
 * - Avatar mode open ([AvatarStage.show]) → an engine runs; closed → it ends.
 * - The app in the background for [RELEASE_AFTER_MS] → the engine ends (it holds ~1 GB even paused)
 *   and starts again when the app comes back (≈ 3 s, the robot's face shows meanwhile).
 * - An engine that dies on its own (the system reclaimed it) is started again.
 *
 * Needs Android 11 (SurfaceControlViewHost), an arm64 phone (only arm64 engine libraries are packed) and
 * the robot's exported files in the app's assets (pet_godot/tools/export_to_app.ps1).
 */
class AvatarStageHost(private val activity: FragmentActivity) {
    private val container = FrameLayout(activity).apply {
        id = com.skyliner2008.jarvis.R.id.avatar_stage     // fixed: a fragment restored after recreation finds it
        visibility = View.GONE
    }
    private val handler = Handler(Looper.getMainLooper())
    /** Avatar mode wants the robot on screen */
    private var showing = false
    /** an engine is started (its fragment is attached) */
    private var engineUp = false
    /** when engines died on their own lately */
    private val crashTimes = ArrayList<Long>()
    /** engines kept dying: no more starts until the app's process starts again (kept in [AvatarStage], so a
     *  recreated activity's host agrees with the screen) */
    private val gaveUp: Boolean get() = AvatarStage.failed.value
    private val releaseInBackground = Runnable {
        if (showing && engineUp) {
            Log.i(TAG, "app in the background: engine released")
            stopEngine()
        }
    }

    /** Adds the (hidden) stage over the Compose content and connects [AvatarStage]; call after setContent. */
    fun install() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // an engine fragment restored with a recreated activity has no engine behind it: Avatar mode starts a new one
        val fm = activity.supportFragmentManager
        fm.findFragmentByTag(FRAGMENT_TAG)?.let { fm.beginTransaction().remove(it).commitNowAllowingStateLoss() }
        AvatarLink.install()
        AvatarLink.onEngineGone = { handler.post { onEngineGone() } }
        AvatarStage.platformAvailable = { isSupported() }
        AvatarStage.platformShow = { show -> activity.runOnUiThread { if (show) show() else hide() } }
    }

    /** MainActivity.onStart */
    fun onHostStart() {
        handler.removeCallbacks(releaseInBackground)
        if (showing && !engineUp && !gaveUp) startEngine()
    }

    /** MainActivity.onStop */
    fun onHostStop() {
        if (showing && engineUp) handler.postDelayed(releaseInBackground, RELEASE_AFTER_MS)
    }

    fun isSupported(): Boolean =
        !gaveUp &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a") &&
            hasRobotFiles()

    private fun hasRobotFiles(): Boolean = try {
        activity.assets.open(ROBOT_MARKER).close()
        true
    } catch (_: Exception) {
        false
    }

    private fun show() {
        if (showing || !isSupported() || activity.isFinishing) return
        Log.i(TAG, "show")
        showing = true
        // the engine's surface is sized once when it starts: keep the phone upright while Avatar mode is open
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // the engine draws from another process, so nothing here asks the display for a frame rate and
        // adaptive refresh (Samsung) drops to 30 Hz: ask for 60 while the robot is up
        setPreferredRefreshRate(60f)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startEngine()
    }

    private fun hide() {
        if (!showing) return
        Log.i(TAG, "hide")
        showing = false
        handler.removeCallbacks(releaseInBackground)
        stopEngine()
        AvatarLink.clearPending()
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setPreferredRefreshRate(0f)
    }

    private fun startEngine() {
        if (engineUp) return
        engineUp = true
        container.visibility = View.VISIBLE
        val fm = activity.supportFragmentManager
        val fragment = RemoteGodotFragment()
        fm.beginTransaction().replace(container.id, fragment, FRAGMENT_TAG).commitNowAllowingStateLoss()
        container.post {
            // stopped again before this ran (Avatar mode closed at once): binding now would start an
            // engine that no fragment shows or stops
            if (!engineUp || !fragment.isAdded || fragment.isRemoving) return@post
            try {
                fragment.startRemoteGame(emptyArray())
            } catch (e: Exception) {
                Log.e(TAG, "cannot start the robot engine", e)
            }
        }
    }

    private fun stopEngine() {
        if (!engineUp) return
        engineUp = false
        AvatarStage.onEngineStopped()
        val fm = activity.supportFragmentManager
        (fm.findFragmentByTag(FRAGMENT_TAG) as? RemoteGodotFragment)?.let { fragment ->
            try {
                fragment.stopRemoteGame(true)
            } catch (e: Exception) {
                Log.w(TAG, "stopRemoteGame: ${e.message}")
            }
            if (!fm.isStateSaved) fm.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
            else fm.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }
        container.visibility = View.GONE
    }

    /**
     * The engine's link dropped: expected after [stopEngine]; otherwise the engine died — start another,
     * unless it keeps dying ([MAX_CRASHES] within [CRASH_WINDOW_MS]): then Avatar mode shows the robot's
     * face instead of starting a crashing engine over and over.
     */
    private fun onEngineGone() {
        if (!showing || !engineUp) return
        stopEngine()
        val now = System.currentTimeMillis()
        crashTimes.removeAll { now - it > CRASH_WINDOW_MS }
        crashTimes.add(now)
        if (crashTimes.size >= MAX_CRASHES) {
            Log.e(TAG, "the robot engine keeps dying: Avatar mode falls back to the robot's face")
            AvatarStage.onEngineFailed()
            return
        }
        Log.w(TAG, "the robot engine is gone: starting a new one")
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startEngine()
    }

    /** 0 = no preference (the system decides again). */
    private fun setPreferredRefreshRate(hz: Float) {
        val window = activity.window
        window.attributes = window.attributes.apply { preferredRefreshRate = hz }
    }

    companion object {
        private const val TAG = "AvatarStageHost"
        private const val FRAGMENT_TAG = "avatar_stage"
        /** a file every Godot export has at its root (the project's settings) */
        private const val ROBOT_MARKER = "project.binary"
        private const val RELEASE_AFTER_MS = 30_000L
        private const val MAX_CRASHES = 3
        private const val CRASH_WINDOW_MS = 60_000L
    }
}
