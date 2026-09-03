package com.melihucgun.diminity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.melihucgun.diminity.data.DimSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

class DimOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.melihucgun.diminity.ACTION_START"
        const val ACTION_STOP = "com.melihucgun.diminity.ACTION_STOP"
        const val ACTION_UPDATE = "com.melihucgun.diminity.ACTION_UPDATE"
        const val EXTRA_DIM_LEVEL = "extra_dim_level"
        const val EXTRA_BLUE_FILTER_LEVEL = "extra_blue_filter_level"

        const val CHANNEL_ID = "diminity_overlay_channel"
        const val NOTIFICATION_ID = 1001

        var isRunning = mutableStateOf(false)
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentDimLevel = 0.35f
    private var currentBlueFilterLevel = 0.0f

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()

        val repository = DimSettingsRepository.getInstance(this)
        serviceScope.launch {
            repository.settingsFlow.collectLatest { settings ->
                currentDimLevel = settings.dimLevel
                currentBlueFilterLevel = settings.blueFilterLevel
                if (isRunning.value) {
                    showOrUpdateOverlay(currentDimLevel, currentBlueFilterLevel)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopDimmingService()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE -> {
                if ((intent != null) && intent.hasExtra(EXTRA_DIM_LEVEL)) {
                    currentDimLevel = intent.getFloatExtra(EXTRA_DIM_LEVEL, currentDimLevel)
                }
                if ((intent != null) && intent.hasExtra(EXTRA_BLUE_FILTER_LEVEL)) {
                    currentBlueFilterLevel = intent.getFloatExtra(EXTRA_BLUE_FILTER_LEVEL, currentBlueFilterLevel)
                }

                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        } else {
                            0
                        },
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                isRunning.value = true
                showOrUpdateOverlay(currentDimLevel, currentBlueFilterLevel)
                requestTileUpdate()
            }
        }

        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isRunning.value && (overlayView != null) && (layoutParams != null)) {
            try {
                windowManager?.updateViewLayout(overlayView, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showOrUpdateOverlay(dimLevel: Float, blueFilterLevel: Float) {
        val wm = windowManager ?: return

        val dimOpacity = dimLevel.coerceIn(0.0f, 0.80f)
        val effectiveFilter = blueFilterLevel.coerceIn(0.0f, 1.0f).pow(1.7f)
        val warmOpacity = effectiveFilter * 0.28f

        val rawCombinedAlpha = 1.0f - (1.0f - dimOpacity) * (1.0f - warmOpacity)
        val finalAlpha = rawCombinedAlpha.coerceIn(0.0f, 0.80f)

        val warmWeight = if (rawCombinedAlpha > 0f) {
            (warmOpacity / rawCombinedAlpha).coerceIn(0.0f, 1.0f)
        } else {
            0.0f
        }

        val blendedR = (255f * warmWeight).roundToInt().coerceIn(0, 255)
        val blendedG = (185f * warmWeight).roundToInt().coerceIn(0, 255)
        val blendedB = (85f * warmWeight).roundToInt().coerceIn(0, 255)
        val overlayColor = Color.rgb(blendedR, blendedG, blendedB)

        if (overlayView == null) {
            val view = View(this).apply {
                setBackgroundColor(overlayColor)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                alpha = finalAlpha
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            try {
                wm.addView(view, params)
                overlayView = view
                layoutParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            overlayView?.setBackgroundColor(overlayColor)
            layoutParams?.let { params ->
                params.alpha = finalAlpha
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                try {
                    wm.updateViewLayout(overlayView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopDimmingService() {
        removeOverlay()
        isRunning.value = false
        requestTileUpdate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        cancelNotification()
        stopSelf()
    }

    private fun cancelNotification() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
        layoutParams = null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlay()
        isRunning.value = false
        requestTileUpdate()
        cancelNotification()
        super.onDestroy()
    }

    private fun requestTileUpdate() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, DiminityQuickSettingsTile::class.java),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.service_notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, DimOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_content))
            .setSmallIcon(R.drawable.ic_stat_diminity)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, getString(R.string.service_notification_action_stop), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
