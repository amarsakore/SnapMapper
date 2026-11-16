package com.amar.snapmapper

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date

/**
 * Turns Snap Key presses into single / double / long gestures and runs the chosen action.
 *
 * This is a plain singleton — **no accessibility service**. Presses arrive from ColorOS through
 * [com.coloros.translate.ui.cube.KeyMessengerService] (single tap) and
 * [com.coloros.translate.ui.cube.LongPressActivity] (long press), which both call in here.
 */
object PressEngine {

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var runner: ActionRunner? = null
    private var prefs: Prefs? = null

    @Volatile var lastPressAt = 0L
        private set

    private fun init(context: Context) {
        if (app == null) {
            app = context.applicationContext
            runner = ActionRunner(app!!)
            prefs = Prefs(app!!)
        }
    }

    fun onTap(context: Context) {
        init(context)
        lastPressAt = System.currentTimeMillis()
        handler.post { handleTap(context) }
    }

    fun onLong(context: Context) {
        init(context)
        lastPressAt = System.currentTimeMillis()
        handler.post {
            handler.removeCallbacks(tapRunnable)
            tapPending = false
            fire(Gesture.LONG, context)
        }
    }

    // ---- single / double timing ----

    private var tapPending = false
    private var tapContext: Context? = null
    private val tapRunnable = Runnable {
        tapPending = false
        fire(Gesture.SINGLE, tapContext ?: app!!)
    }

    private fun handleTap(context: Context) {
        val p = prefs!!
        when {
            tapPending -> {
                handler.removeCallbacks(tapRunnable)
                tapPending = false
                fire(Gesture.DOUBLE, context)
            }
            !doubleInUse(p) -> fire(Gesture.SINGLE, context)
            else -> {
                tapPending = true
                tapContext = context
                handler.postDelayed(tapRunnable, p.doublePressMs.toLong())
            }
        }
    }

    private fun doubleInUse(p: Prefs): Boolean =
        p.action(Gesture.DOUBLE) != KeyAction.NONE ||
            (p.lockedActionsEnabled && p.action(Gesture.DOUBLE, Scope.LOCKED) != KeyAction.NONE)

    // ---- running an action ----

    private fun fire(g: Gesture, launch: Context) {
        val ctx = app ?: return
        val p = prefs ?: return
        val r = runner ?: return

        val audio = ctx.getSystemService(AudioManager::class.java)
        if (p.pauseInCalls &&
            (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION)
        ) {
            history(p, g, "Ignored during call")
            return
        }

        val power = ctx.getSystemService(PowerManager::class.java)
        val screenOn = power.isInteractive
        val locked = !screenOn || ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        var scope = Scope.NORMAL
        var where = ""
        if (locked && p.lockedActionsEnabled) {
            scope = Scope.LOCKED
            where = "locked"
        } else if (!locked && g == Gesture.SINGLE && p.appRules.isNotEmpty()) {
            foregroundApp(ctx)?.takeIf { it in p.appRules }?.let {
                scope = Scope.app(it)
                where = ActionRunner.ctxLabel(ctx, it) ?: it
            }
        }

        val action = p.action(g, scope)
        if (action == KeyAction.NONE) {
            history(p, g, "Nothing set", where)
            return
        }
        val ownHaptics = action == KeyAction.RING_VIBRATE || action == KeyAction.RING_MODE
        if (p.vibrate && !ownHaptics) tick(ctx)

        val result = r.run(action, g, p, scope, launch)
        if (p.showPopup && screenOn && action != KeyAction.FLASHLIGHT) popup(ctx, result)
        history(p, g, result, where)
    }

    private fun tick(ctx: Context) {
        ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun history(p: Prefs, g: Gesture, what: String, where: String = "") {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val suffix = if (where.isNotEmpty()) "  ·  $where" else ""
        p.addHistory("$time  ${g.label} → $what$suffix")
    }

    /** Current foreground app via Usage access (null if the permission isn't granted). */
    private fun foregroundApp(ctx: Context): String? {
        return try {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 10_000, now)
            val e = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
            }
            last
        } catch (ex: Exception) {
            null
        }
    }

    // ---- camera pill (optional; needs "Display over other apps") ----

    private var islandRoot: FrameLayout? = null
    private var islandPill: View? = null
    private var islandIcon: ImageView? = null
    private var islandLp: WindowManager.LayoutParams? = null
    private val hideIsland = Runnable {
        val pill = islandPill ?: return@Runnable
        pill.animate().alpha(0f).scaleX(0.4f).scaleY(0.9f).setDuration(220)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                pill.visibility = View.INVISIBLE
                islandRoot?.visibility = View.GONE
            }.start()
    }

    private fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()

    fun previewIsland(context: Context) {
        init(context)
        popup(app!!, "Vibrate")
    }

    private fun popup(ctx: Context, message: String) {
        if (!Settings.canDrawOverlays(ctx)) return
        val wm = ctx.getSystemService(WindowManager::class.java)
        if (!ensureIsland(ctx, wm)) return
        val pill = islandPill ?: return
        val p = prefs!!
        val (iconRes, tint) = when (message) {
            "Vibrate" -> R.drawable.ic_island_vibrate to 0xFF8BA4FF.toInt()
            "Ring" -> R.drawable.ic_island_ring to 0xFF5BD68A.toInt()
            "Silent" -> R.drawable.ic_island_silent to 0xFFFF6B6B.toInt()
            "Flashlight on" -> R.drawable.ic_island_flash_on to 0xFFFFC24D.toInt()
            "Flashlight off" -> R.drawable.ic_island_flash_off to 0xFFB0B0B0.toInt()
            "Screenshot" -> R.drawable.ic_island_check to 0xFF8BA4FF.toInt()
            else -> R.drawable.ic_island_check to 0xFF5BD68A.toInt()
        }
        islandIcon?.apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(tint)
        }
        pill.contentDescription = message
        handler.removeCallbacks(hideIsland)
        pill.animate().cancel()
        pill.pivotX = (islandLp?.width ?: 0) / 2f
        pill.pivotY = (islandLp?.height ?: 0) / 2f
        islandRoot?.visibility = View.VISIBLE
        if (pill.visibility != View.VISIBLE) {
            pill.alpha = 0f; pill.scaleX = 0.4f; pill.scaleY = 0.9f
            pill.visibility = View.VISIBLE
        }
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
        handler.postDelayed(hideIsland, 1300)
    }

    private fun islandLayout(ctx: Context, wm: WindowManager): WindowManager.LayoutParams {
        val metrics = wm.currentWindowMetrics
        val cutout = metrics.windowInsets.displayCutout?.boundingRects
            ?.filter { it.top < dp(ctx, 80f) }?.minByOrNull { it.top }
        val camCenterX = cutout?.centerX() ?: (metrics.bounds.width() / 2)
        val centerY = (cutout?.top ?: 0) + (cutout?.height() ?: dp(ctx, 40f)) / 2 + dp(ctx, prefs!!.islandOffsetDp.toFloat())
        val w = dp(ctx, 80f)
        val h = dp(ctx, 26f)
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = camCenterX - w / 2
            y = maxOf(0, centerY - h / 2)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "SnapMapper pill"
        }
    }

    private fun ensureIsland(ctx: Context, wm: WindowManager): Boolean {
        val lp = islandLayout(ctx, wm)
        val root = islandRoot
        if (root != null) {
            val old = islandLp!!
            if (old.x != lp.x || old.y != lp.y || old.width != lp.width) {
                islandLp = lp
                try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
            }
            return true
        }
        val cutoutW = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
            ?.filter { it.top < dp(ctx, 80f) }?.minByOrNull { it.top }?.width() ?: dp(ctx, 21f)
        val sideW = (lp.width - cutoutW) / 2
        val iconSize = dp(ctx, 16f)
        fun iconView(res: Int?, color: Int) = FrameLayout(ctx).apply {
            val iv = ImageView(ctx).apply {
                res?.let { setImageResource(it) }
                imageTintList = android.content.res.ColorStateList.valueOf(color)
            }
            addView(iv, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            if (res == null) islandIcon = iv
        }
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = lp.height / 2f
                setColor(0xFF000000.toInt())
            }
            addView(iconView(R.drawable.ic_island_snap, 0xFFFFFFFF.toInt()), LinearLayout.LayoutParams(sideW, lp.height))
            addView(View(ctx), LinearLayout.LayoutParams(cutoutW, lp.height))
            addView(iconView(null, 0xFFFFFFFF.toInt()), LinearLayout.LayoutParams(sideW, lp.height))
            visibility = View.INVISIBLE
        }
        val frame = FrameLayout(ctx).apply {
            addView(pill, FrameLayout.LayoutParams(lp.width, lp.height))
            visibility = View.GONE
        }
        return try {
            wm.addView(frame, lp)
            islandRoot = frame; islandPill = pill; islandLp = lp
            true
        } catch (e: Exception) {
            false
        }
    }
}
