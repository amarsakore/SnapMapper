package com.amar.snapmapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Takes screenshots through the system screen-capture API (MediaProjection) — **no accessibility
 * service**, so it doesn't trip Google Pay. Armed once (the system shows a one-time consent),
 * then kept alive as a foreground service so later screenshots need no pop-up. A quiet, ongoing
 * notification is the price Android charges for holding screen capture.
 */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var width = 0
    private var height = 0
    private var density = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> arm(intent)
            ACTION_CAPTURE -> capture()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> if (projection == null) { stopSelf(); return START_NOT_STICKY }
        }
        return START_STICKY
    }

    private fun arm(intent: Intent) {
        startForegroundNotice()
        if (projection != null) { // already armed, just capture if asked
            if (intent.getBooleanExtra(EXTRA_THEN_CAPTURE, false)) handler.postDelayed({ capture() }, 250)
            return
        }
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) ?: return stopWith("No capture permission")
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data) ?: return stopWith("Screen capture unavailable")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { teardown() }
        }, handler)
        val vd = proj.createVirtualDisplay(
            "SnapMapperCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
        )
        projection = proj
        imageReader = reader
        virtualDisplay = vd
        armed = true
        PressEngine // touch to ensure class loaded
        if (intent.getBooleanExtra(EXTRA_THEN_CAPTURE, false)) handler.postDelayed({ capture() }, 450)
    }

    private fun capture() {
        val reader = imageReader ?: run {
            // Not armed: ask for consent, then capture.
            ProjectionActivity.request(this, capture = true)
            return
        }
        // Give the mirror a frame to settle, then grab the latest image.
        handler.postDelayed({
            val image = reader.acquireLatestImage()
            if (image == null) { toast("Screenshot failed, try again"); return@postDelayed }
            try {
                val plane = image.planes[0]
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val rowPadding = rowStride - pixelStride * width
                val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(plane.buffer)
                val cropped = if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
                save(cropped)
            } catch (e: Exception) {
                toast("Screenshot failed: ${e.message}")
            } finally {
                image.close()
            }
        }, 120)
    }

    private fun save(bmp: Bitmap) {
        val name = "Screenshot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { toast("Couldn't save screenshot"); return }
        try {
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            toast("Screenshot saved")
        } catch (e: Exception) {
            toast("Couldn't save screenshot: ${e.message}")
        }
    }

    private fun startForegroundNotice() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Snap Key screenshot", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Snap Key screenshot ready")
            .setContentText("Tap to open SnapMapper")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        startForeground(NOTIF_ID, n, type)
    }

    private fun stopWith(msg: String) {
        toast(msg); teardown(); stopSelf()
    }

    private fun teardown() {
        armed = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        virtualDisplay = null; imageReader = null; projection = null
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 7
        const val ACTION_ARM = "arm"
        const val ACTION_CAPTURE = "capture"
        const val ACTION_STOP = "stop"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_THEN_CAPTURE = "then_capture"

        /** True while screenshots are armed (foreground service holding screen capture). */
        @Volatile var armed = false
            private set

        /** Take a screenshot now: capture if armed, otherwise ask for consent then capture. */
        fun takeScreenshot(context: Context) {
            val app = context.applicationContext
            if (armed) {
                app.startService(Intent(app, CaptureService::class.java).setAction(ACTION_CAPTURE))
            } else {
                ProjectionActivity.request(app, capture = true)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.startService(Intent(app, CaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
