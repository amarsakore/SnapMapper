package com.coloros.translate.ui.cube

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.amar.snapmapper.SnapKeyService

/**
 * Snap Key presses straight from ColorOS, with no ADB, root or Developer options.
 *
 * When Settings → Additional settings → Snap Key is set to "Translate", ColorOS's gesture app
 * (com.oplus.gesture) binds to com.coloros.translate/.ui.cube.KeyMessengerService, as listed in
 * its action_button_scene_config.xml, and sends a Messenger message on every tap:
 * what = 1, data["key"] = "SINGLE_TAP" (what = 2 / "LONG_PRESS" for the long-press path).
 * The real ColorOS Translate app isn't installed on this phone, so SnapMapper uses that package
 * name and receives those messages.
 */
open class KeyMessengerService : Service() {

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!fromColorOs(msg)) {
                Log.w(TAG, "ignored message from uid ${msg.sendingUid}")
                return
            }
            val key = msg.data?.getString("key")
            Log.i(TAG, "Snap Key message what=${msg.what} key=$key")
            when {
                key == "LONG_PRESS" || (key == null && msg.what == 2) -> SnapKeyService.snapLongPress(this@KeyMessengerService)
                key == "SINGLE_TAP" || (key == null && msg.what == 1) -> SnapKeyService.snapTap(this@KeyMessengerService)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    /** Only ColorOS's gesture app may send presses. */
    private fun fromColorOs(msg: Message): Boolean {
        val uid = msg.sendingUid
        if (uid < 0) return false
        val pkgs = packageManager.getPackagesForUid(uid) ?: return false
        return GESTURE_PKG in pkgs
    }

    companion object {
        private const val TAG = "SnapKeyMessenger"
        const val GESTURE_PKG = "com.oplus.gesture"
    }
}
