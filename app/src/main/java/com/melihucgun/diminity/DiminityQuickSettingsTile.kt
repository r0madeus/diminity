package com.melihucgun.diminity

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class DiminityQuickSettingsTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val isServiceRunning = DimOverlayService.isRunning.value

        if (isServiceRunning) {
            val intent = Intent(this, DimOverlayService::class.java).apply {
                action = DimOverlayService.ACTION_STOP
            }
            startService(intent)
        } else {
            val hasOverlayPermission = Settings.canDrawOverlays(this)
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasOverlayPermission && hasNotificationPermission) {
                val intent = Intent(this, DimOverlayService::class.java).apply {
                    action = DimOverlayService.ACTION_START
                }
                startForegroundService(intent)
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    startActivityAndCollapse(intent)
                }
            }
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isServiceRunning = DimOverlayService.isRunning.value
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_diminity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isServiceRunning) {
                getString(R.string.qs_tile_subtitle_active)
            } else {
                getString(R.string.qs_tile_subtitle_inactive)
            }
        }
        tile.state = if (isServiceRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
