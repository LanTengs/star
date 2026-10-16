package com.lanteng.star.app.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lanteng.star.app.R
import com.lanteng.star.app.data.Store
import com.lanteng.star.app.launch.BadgeLauncher
import com.lanteng.star.app.ui.MainActivity
import com.lanteng.star.app.ui.NfcReadActivity
import com.lanteng.star.app.util.Prefs
import kotlin.math.sqrt

/**
 * The floating panel.
 *
 * Entirely local: it draws an overlay, offers shortcuts into the app and cycles
 * a label. Every optional behaviour (auto hide, icon only, outside tap, volume
 * keys, shake, account label) is driven by the local preference values.
 */
class FloatingService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var actions: View? = null
    private var quickRow: LinearLayout? = null
    private var icon: ImageView? = null
    private var accountLabel: TextView? = null
    private var expanded = false

    private var sensorManager: SensorManager? = null
    private var shakeListener: SensorEventListener? = null
    private var volumeObserver: ContentObserver? = null
    private var lastVolumeChangeAt = 0L
    private var lastVolumeUri: Uri? = null
    private var lastShakeAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        if (!canDrawOverlays(this)) {
            stopSelf()
            return
        }

        running = true
        addPanel()
        registerSensor()
        registerVolumeObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        unregisterSensor()
        unregisterVolumeObserver()
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ panel

    private fun addPanel() {
        val themed = android.view.ContextThemeWrapper(this, R.style.Theme_StarBadges)
        val view = LayoutInflater.from(themed).inflate(R.layout.floating_panel, null)

        icon = view.findViewById(R.id.iv_icon)
        actions = view.findViewById(R.id.panel_actions)
        accountLabel = view.findViewById(R.id.tv_account)
        quickRow = view.findViewById(R.id.quick_row)

        applyCustomIcon()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 320
        }

        view.findViewById<ImageButton>(R.id.btn_open_app).setOnClickListener {
            open(MainActivity::class.java)
        }
        view.findViewById<ImageButton>(R.id.btn_quick_nfc).setOnClickListener {
            open(NfcReadActivity::class.java)
        }
        view.findViewById<ImageButton>(R.id.btn_collapse).setOnClickListener { collapse() }

        icon?.setOnTouchListener(DragToToggle(layoutParams))

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && prefs.clickOutsideToHide) {
                collapse()
            }
            false
        }

        runCatching { windowManager.addView(view, layoutParams) }
        panel = view
        params = layoutParams

        if (prefs.iconOnlyPanel) collapse() else applyExpandedState()
    }

    private fun applyCustomIcon() {
        val uri = prefs.customIconUri
        if (uri.isEmpty()) return
        runCatching { icon?.setImageURI(Uri.parse(uri)) }
    }

    private fun toggle() {
        if (expanded) collapse() else expand()
    }

    private fun expand() {
        expanded = true
        applyExpandedState()
    }

    private fun collapse() {
        expanded = false
        applyExpandedState()
    }

    private fun applyExpandedState() {
        // "icon only" keeps the panel permanently collapsed.
        val showActions = expanded && !prefs.iconOnlyPanel
        actions?.visibility = if (showActions) View.VISIBLE else View.GONE

        if (showActions) rebuildQuickRow()

        val label = accountLabel ?: return
        if (showActions && prefs.switchCard) {
            val account = Store.accounts.firstOrNull()
            label.text = account?.name ?: getString(R.string.spinner_account_all)
            label.visibility = View.VISIBLE
            label.setOnClickListener { cycleAccount() }
        } else {
            label.visibility = View.GONE
        }
    }

    private var accountIndex = 0
    private fun cycleAccount() {
        if (Store.accounts.isEmpty()) return
        accountIndex = (accountIndex + 1) % Store.accounts.size
        accountLabel?.text = Store.accounts[accountIndex].name
    }

    /**
     * One button per badge that has a link, most recently used first. Tapping it
     * hands the stored link back so the effect can be refreshed without digging
     * the card out again.
     */
    private fun rebuildQuickRow() {
        val row = quickRow ?: return
        row.removeAllViews()

        Store.badges
            .filter { !it.badgeUrl.isNullOrBlank() }
            .sortedByDescending { it.lastUsedAt ?: 0L }
            .take(MAX_QUICK_BUTTONS)
            .forEach { badge ->
                val ready = BadgeLauncher.isReady(badge, prefs)
                val button = ImageButton(row.context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setImageResource(R.drawable.ic_play)
                    contentDescription = badge.name
                    alpha = if (ready) 1f else 0.35f
                    setOnClickListener {
                        if (BadgeLauncher.isReady(badge, prefs) &&
                            BadgeLauncher.trigger(this@FloatingService, badge, prefs)
                        ) {
                            rebuildQuickRow()
                            // Matches the setting's own wording: the panel is
                            // collapsed after a badge has been used.
                            if (prefs.autoHidePanel) collapse()
                        }
                    }
                }
                row.addView(button)
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun open(clazz: Class<*>) {
        val intent = Intent(this, clazz).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        runCatching { startActivity(intent) }
    }

    /** Distinguishes a tap (toggle) from a drag (move the panel). */
    private inner class DragToToggle(
        private val layoutParams: WindowManager.LayoutParams,
    ) : View.OnTouchListener {

        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragged = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > TOUCH_SLOP || kotlin.math.abs(dy) > TOUCH_SLOP) {
                        dragged = true
                    }
                    if (dragged) {
                        layoutParams.x = startX + dx.toInt()
                        layoutParams.y = startY + dy.toInt()
                        panel?.let { runCatching { windowManager.updateViewLayout(it, layoutParams) } }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) toggle()
                    return true
                }
            }
            return false
        }
    }

    // ------------------------------------------------------------ extra inputs

    private fun registerSensor() {
        if (!prefs.shakeToggle) return
        val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                val magnitude = sqrt(
                    values[0] * values[0] + values[1] * values[1] + values[2] * values[2],
                ) / SensorManager.GRAVITY_EARTH
                if (magnitude > SHAKE_THRESHOLD_G) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeAt > SHAKE_DEBOUNCE_MS) {
                        lastShakeAt = now
                        toggle()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        sensorManager = manager
        shakeListener = listener
    }

    private fun unregisterSensor() {
        val manager = sensorManager ?: return
        shakeListener?.let { manager.unregisterListener(it) }
        sensorManager = null
        shakeListener = null
    }

    /**
     * Both volume keys pressed together.
     *
     * Android gives a background app no way to intercept key events, so this
     * watches the two volume settings instead and treats "both streams changed
     * within a short window" as the gesture. It is an approximation and is
     * documented as such.
     */
    private fun registerVolumeObserver() {
        if (!prefs.volumeKeysToggle) return
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val target = uri ?: return
                val now = System.currentTimeMillis()
                if (target != lastVolumeUri && now - lastVolumeChangeAt < VOLUME_PAIR_WINDOW_MS) {
                    toggle()
                }
                lastVolumeUri = target
                lastVolumeChangeAt = now
            }
        }
        val resolver = contentResolver
        // The persisted Settings.System keys for the media and ring streams.
        listOf(VOLUME_SETTING_MUSIC, VOLUME_SETTING_RING).forEach { name ->
            runCatching {
                resolver.registerContentObserver(
                    Settings.System.getUriFor(name),
                    false,
                    observer,
                )
            }
        }
        volumeObserver = observer
    }

    private fun unregisterVolumeObserver() {
        volumeObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        volumeObserver = null
    }

    // ------------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_service_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_service_running))
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.floating_service_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "floating_panel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.lanteng.star.app.action.STOP_FLOATING"
        private const val TOUCH_SLOP = 12f
        private const val SHAKE_THRESHOLD_G = 2.6f
        private const val SHAKE_DEBOUNCE_MS = 1200L
        private const val VOLUME_PAIR_WINDOW_MS = 700L
        private const val VOLUME_SETTING_MUSIC = "volume_music"
        private const val VOLUME_SETTING_RING = "volume_ring"
        private const val MAX_QUICK_BUTTONS = 5

        @Volatile
        private var running = false

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (!canDrawOverlays(context)) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }

        /** Re-inflates the overlay so a newly picked icon takes effect. */
        fun refreshIcon(context: Context) {
            if (!running) return
            stop(context)
            start(context)
        }
    }
}
