package com.amar.snapmapper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/**
 * Transparent, one-shot activity that asks the system for screen-capture consent and hands the
 * result to [CaptureService]. Used to arm screenshots (and optionally capture straight away).
 */
class ProjectionActivity : Activity() {

    private var thenCapture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thenCapture = intent.getBooleanExtra(EXTRA_CAPTURE, false)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        try {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
        } catch (e: Exception) {
            Toast.makeText(this, "Screen capture not available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, CaptureService::class.java)
                .setAction(CaptureService.ACTION_ARM)
                .putExtra(CaptureService.EXTRA_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data)
                .putExtra(CaptureService.EXTRA_THEN_CAPTURE, thenCapture)
            startForegroundService(svc)
        } else {
            Toast.makeText(this, "Screenshot needs screen-capture permission", Toast.LENGTH_SHORT).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQ = 1
        const val EXTRA_CAPTURE = "capture"

        fun request(context: Context, capture: Boolean) {
            val i = Intent(context, ProjectionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_CAPTURE, capture)
            context.startActivity(i)
        }
    }
}
