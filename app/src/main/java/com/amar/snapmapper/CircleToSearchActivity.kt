package com.amar.snapmapper

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.lang.reflect.InvocationTargetException

/**
 * Opens Circle to Search with Android's own call, ContextualSearchManager.startContextualSearch(
 * Activity, ContextualSearchConfig). The system only answers the app in front, so this see-through
 * activity comes to the front, asks, and closes (about 0.4 s on the Find X9).
 * The class isn't in the public SDK, hence the reflection.
 *
 * It asks only once it has window focus, and retries a few times: right after a previous Circle to
 * Search closes, the system can still see another app in front for a moment and refuses the call.
 */
class CircleToSearchActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var tries = 0
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never stay on screen if focus doesn't arrive.
        handler.postDelayed({ close() }, 1500)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done && tries == 0) attempt()
    }

    private fun attempt() {
        tries++
        try {
            val manager = getSystemService("contextual_search")!!
            val configClass = Class.forName("android.app.contextualsearch.ContextualSearchConfig")
            val builder = Class.forName("android.app.contextualsearch.ContextualSearchConfig\$Builder")
            val config = builder.getMethod("build").invoke(builder.getConstructor().newInstance())
            manager.javaClass.getMethod("startContextualSearch", Activity::class.java, configClass)
                .invoke(manager, this, config)
            close()
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "Circle to Search call failed (try $tries)", cause)
            if (tries < MAX_TRIES) {
                handler.postDelayed({ if (!done) attempt() }, RETRY_MS)
            } else {
                Toast.makeText(this, "Circle to Search failed: ${cause.message ?: cause.javaClass.simpleName}",
                    Toast.LENGTH_LONG).show()
                close()
            }
        }
    }

    private fun close() {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "SnapKeyCircle"
        private const val MAX_TRIES = 4
        private const val RETRY_MS = 150L
    }
}
