package com.coloros.translate.ui.cube

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.amar.snapmapper.MainActivity
import com.amar.snapmapper.SnapKeyService

/**
 * Long-pressing the Snap Key makes ColorOS start action "coloros.intent.action.translate.CUBE"
 * in this package, with extras request_package = com.oplus.gesture and action_key_type = 2.
 * Any other launch (e.g. ColorOS's "Translate" settings shortcut) opens SnapMapper instead.
 */
class LongPressActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromSnapKey = intent?.getIntExtra("action_key_type", -1) == 2 &&
            intent?.getStringExtra("request_package") == KeyMessengerService.GESTURE_PKG
        if (fromSnapKey) {
            SnapKeyService.snapLongPress(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
