package com.amar.snapmapper

import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.widget.Toast

/**
 * Actions the Snap Key can run. All of these work in a **normal app** — no accessibility
 * service. Screenshots go through the system screen-capture API (MediaProjection); lock screen
 * uses a device-admin lock; everything else is plain framework calls or an app launch.
 */
enum class KeyAction(val label: String) {
    NONE("Do nothing"),
    FLASHLIGHT("Toggle flashlight"),
    SCREENSHOT("Take screenshot"),
    CAMERA("Open camera"),
    VIDEO("Record video"),
    LAUNCH_APP("Open an app…"),
    OPEN_URL("Open a link…"),
    PLAY_PAUSE("Play / pause media"),
    NEXT_TRACK("Next track"),
    PREV_TRACK("Previous track"),
    RING_VIBRATE("Switch ring / vibrate"),
    RING_MODE("Cycle ring / vibrate / silent"),
    ASSISTANT("Voice assistant"),
    LOCK_SCREEN("Lock screen"),
    HOME("Go home"),
    TIMER("Start a timer"),
    WEB_SEARCH("Web search"),
    CIRCLE_TO_SEARCH("Circle to Search"),
}

/**
 * Runs actions. Long-lived (held by [PressEngine]) so the torch state is tracked by a camera
 * callback. [run] takes the context to launch activities from — an Activity (long press) launches
 * reliably; a background service (single tap) may be refused by the OS, which is reported.
 */
class ActionRunner(private val appContext: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var torchOn = false
    private val torchCameraId: String? = runCatching {
        cameraManager.cameraIdList.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }
    }

    init {
        runCatching { cameraManager.registerTorchCallback(torchCallback, main) }
    }

    /** Runs [action] and returns a short confirmation for the on-screen pill. */
    fun run(action: KeyAction, gesture: Gesture, prefs: Prefs, scope: Scope, launch: Context): String {
        var result = action.label
        try {
            when (action) {
                KeyAction.NONE -> Unit
                KeyAction.FLASHLIGHT -> result = toggleTorch()
                KeyAction.SCREENSHOT -> { CaptureService.takeScreenshot(launch); result = "Screenshot" }
                KeyAction.CAMERA -> start(launch, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                KeyAction.VIDEO -> start(launch, Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
                KeyAction.LAUNCH_APP -> {
                    val pkg = prefs.appPackage(gesture, scope)
                    launchApp(launch, pkg)
                    result = "Opening ${ctxLabel(appContext, pkg) ?: "app"}"
                }
                KeyAction.OPEN_URL -> openUrl(launch, prefs.url(gesture, scope))
                KeyAction.PLAY_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                KeyAction.NEXT_TRACK -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
                KeyAction.PREV_TRACK -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                KeyAction.RING_VIBRATE -> result = toggleRingVibrate()
                KeyAction.RING_MODE -> result = cycleRingMode()
                KeyAction.ASSISTANT -> start(launch, Intent(Intent.ACTION_VOICE_COMMAND))
                KeyAction.LOCK_SCREEN -> result = lockScreen()
                KeyAction.HOME -> start(launch, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                KeyAction.TIMER -> start(launch, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_SKIP_UI, false))
                KeyAction.WEB_SEARCH -> start(launch, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, ""))
                KeyAction.CIRCLE_TO_SEARCH -> start(launch, Intent(appContext, CircleToSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        } catch (e: Exception) {
            toast("${action.label} failed: ${e.message}")
        }
        return result
    }

    private fun toggleTorch(): String {
        val id = torchCameraId ?: run { toast("No flashlight found"); return "No flashlight" }
        val on = !torchOn
        cameraManager.setTorchMode(id, on)
        torchOn = on
        return if (on) "Flashlight on" else "Flashlight off"
    }

    private fun lockScreen(): String {
        val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(appContext, LockAdminReceiver::class.java)
        return if (dpm.isAdminActive(admin)) {
            dpm.lockNow(); "Locked"
        } else {
            toast("Turn on \"Lock screen\" in SnapMapper first")
            "Lock needs setup"
        }
    }

    private fun start(launch: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            launch.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app can handle that")
        } catch (e: SecurityException) {
            toast("Android blocked opening from the background — try long press")
        }
    }

    private fun launchApp(launch: Context, pkg: String?) {
        if (pkg == null) return toast("Pick an app in SnapMapper first")
        val intent = appContext.packageManager.getLaunchIntentForPackage(pkg)
            ?: return toast("That app is no longer installed")
        start(launch, intent)
    }

    private fun openUrl(launch: Context, url: String?) {
        if (url.isNullOrBlank()) return toast("Set a link in SnapMapper first")
        start(launch, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun media(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun cycleRingMode() = setRinger(
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
    )

    private fun toggleRingVibrate() = setRinger(
        if (audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) AudioManager.RINGER_MODE_NORMAL
        else AudioManager.RINGER_MODE_VIBRATE
    )

    /** Changes the ringer mode and confirms it by feel (vibrate = long buzz, ring = two short). */
    private fun setRinger(mode: Int): String {
        try {
            audio.ringerMode = mode
        } catch (e: SecurityException) {
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        return when (audio.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> { buzz(longArrayOf(0, 380), intArrayOf(0, 255)); "Vibrate" }
            AudioManager.RINGER_MODE_NORMAL -> { buzz(longArrayOf(0, 70, 110, 70), intArrayOf(0, 255, 0, 255)); "Ring" }
            else -> { buzz(longArrayOf(0, 40), intArrayOf(0, 160)); "Silent" }
        }
    }

    private fun buzz(timings: LongArray, amplitudes: IntArray) {
        val vibrator = appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        val t = longArrayOf(150) + timings.drop(1)
        val effect = VibrationEffect.createWaveform(t, amplitudes, -1)
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        fun ctxLabel(context: Context, pkg: String?): String? = pkg?.let {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
            } catch (e: Exception) {
                null
            }
        }
    }
}
