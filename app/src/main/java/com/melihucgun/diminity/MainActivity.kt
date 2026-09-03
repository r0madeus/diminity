package com.melihucgun.diminity

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.melihucgun.diminity.data.AppThemeMode
import com.melihucgun.diminity.data.DimSettingsRepository
import com.melihucgun.diminity.ui.theme.DiminityTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val repository = remember { DimSettingsRepository.getInstance(context) }
            val settingsState by repository.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = settingsState?.themeMode ?: AppThemeMode.SYSTEM

            DiminityTheme(themeMode = themeMode) {
                DiminitySettingsScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        lifecycleScope.launch {
                            repository.setThemeMode(newMode)
                        }
                    },
                )
            }
        }
    }
}

private data class PresetItem(
    val nameResId: Int,
    val dimLevel: Float,
    val blueFilterLevel: Float,
    val label: String,
)

@Composable
fun DiminitySettingsScreen(
    modifier: Modifier = Modifier,
    currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { DimSettingsRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        )
    }

    val settingsState by repository.settingsFlow.collectAsStateWithLifecycle(initialValue = null)

    var hasOverlayPermission by remember {
        mutableStateOf(
            try {
                Settings.canDrawOverlays(context)
            } catch (_: Exception) {
                false
            }
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var notificationPermissionMessage by remember { mutableStateOf<String?>(null) }
    var tileAddStatusMessage by remember { mutableStateOf<String?>(null) }

    var dimLevel by remember { mutableFloatStateOf(0.35f) }
    var blueFilterLevel by remember { mutableFloatStateOf(0.0f) }

    val versionName = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    LaunchedEffect(settingsState) {
        settingsState?.let { settings ->
            dimLevel = settings.dimLevel
            blueFilterLevel = settings.blueFilterLevel
        }
    }

    val isServiceRunning by DimOverlayService.isRunning.collectAsStateWithLifecycle()
    val notificationPermissionWarningString = stringResource(R.string.notification_permission_warning)

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            notificationPermissionMessage = null
            if (hasOverlayPermission) {
                startDimOverlayService(context, dimLevel, blueFilterLevel)
            }
        } else {
            notificationPermissionMessage = notificationPermissionWarningString
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = try {
                    Settings.canDrawOverlays(context)
                } catch (_: Exception) {
                    false
                }
                hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val onRequestIgnoreBatteryOptimization = {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                )
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val onToggleDimming = {
        if (isServiceRunning) {
            stopDimOverlayService(context)
        } else {
            if (!hasOverlayPermission) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if ((!hasNotificationPermission) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notificationPermissionMessage = null
                startDimOverlayService(context, dimLevel, blueFilterLevel)
            }
        }
    }

    val onOpenGitHub = {
        try {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/r0madeus/diminity".toUri())
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderSection(onOpenGitHub = onOpenGitHub)

            Spacer(modifier = Modifier.height(4.dp))

            EkKarartmaSwitchCard(
                isServiceRunning = isServiceRunning,
                hasOverlayPermission = hasOverlayPermission,
                onToggleDimming = onToggleDimming,
            )

            HazirProfillerCard(
                currentDimLevel = dimLevel,
                currentBlueFilterLevel = blueFilterLevel,
                onSelectPreset = { newDim, newBlue ->
                    dimLevel = newDim
                    blueFilterLevel = newBlue
                    coroutineScope.launch {
                        repository.setSettings(newDim, newBlue)
                    }
                    if (isServiceRunning) {
                        updateDimOverlayService(context, newDim, newBlue)
                    }
                },
            )

            KarartmaSeviyesiSliderCard(
                dimLevel = dimLevel,
                onDimLevelChange = { newLevel ->
                    dimLevel = newLevel
                    if (isServiceRunning) {
                        updateDimOverlayService(context, newLevel, blueFilterLevel)
                    }
                },
                onDimLevelChangeFinished = {
                    coroutineScope.launch {
                        repository.setDimLevel(dimLevel)
                    }
                },
            )

            MaviIsikFiltresiSliderCard(
                blueFilterLevel = blueFilterLevel,
                onBlueFilterLevelChange = { newLevel ->
                    blueFilterLevel = newLevel
                    if (isServiceRunning) {
                        updateDimOverlayService(context, dimLevel, newLevel)
                    }
                },
                onBlueFilterLevelChangeFinished = {
                    coroutineScope.launch {
                        repository.setBlueFilterLevel(blueFilterLevel)
                    }
                },
            )

            if ((notificationPermissionMessage != null) && (!isServiceRunning)) {
                NotificationPermissionWarningCard(message = notificationPermissionMessage!!)
            }

            TemaModuCard(
                currentThemeMode = currentThemeMode,
                onThemeModeChange = onThemeModeChange,
            )

            if (!isBatteryOptimizationIgnored) {
                BatteryOptimizationCard(onRequestIgnore = onRequestIgnoreBatteryOptimization)
            }

            QuickSettingsTileCard(
                onRequestAddTile = {
                    requestAddQuickSettingsTile(context) { message ->
                        tileAddStatusMessage = message
                    }
                },
                statusMessage = tileAddStatusMessage,
            )

            OverlayPermissionInfoCard()

            FooterSection(versionName = versionName)

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun requestAddQuickSettingsTile(
    context: Context,
    onResult: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult(context.getString(R.string.qs_status_unsupported))
            return
        }

        val componentName = ComponentName(context, DiminityQuickSettingsTile::class.java)
        val executor = ContextCompat.getMainExecutor(context)
        val tileIcon = AndroidIcon.createWithResource(context, R.drawable.ic_stat_diminity)

        try {
            statusBarManager.requestAddTileService(
                componentName,
                context.getString(R.string.app_name),
                tileIcon,
                executor,
            ) { resultCode ->
                when (resultCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                        onResult(context.getString(R.string.qs_status_added))
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                        onResult(context.getString(R.string.qs_status_already_added))
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> {
                        onResult(context.getString(R.string.qs_status_not_added))
                    }
                    else -> {
                        onResult(context.getString(R.string.qs_status_error))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(context.getString(R.string.qs_status_error))
        }
    } else {
        onResult(context.getString(R.string.qs_status_requires_android13))
    }
}

private fun startDimOverlayService(context: Context, dimLevel: Float, blueFilterLevel: Float) {
    val intent = Intent(context, DimOverlayService::class.java).apply {
        action = DimOverlayService.ACTION_START
        putExtra(DimOverlayService.EXTRA_DIM_LEVEL, dimLevel)
        putExtra(DimOverlayService.EXTRA_BLUE_FILTER_LEVEL, blueFilterLevel)
    }
    context.startForegroundService(intent)
}

private fun stopDimOverlayService(context: Context) {
    val intent = Intent(context, DimOverlayService::class.java).apply {
        action = DimOverlayService.ACTION_STOP
    }
    context.startService(intent)
}

private fun updateDimOverlayService(context: Context, dimLevel: Float, blueFilterLevel: Float) {
    val intent = Intent(context, DimOverlayService::class.java).apply {
        action = DimOverlayService.ACTION_UPDATE
        putExtra(DimOverlayService.EXTRA_DIM_LEVEL, dimLevel)
        putExtra(DimOverlayService.EXTRA_BLUE_FILTER_LEVEL, blueFilterLevel)
    }
    context.startService(intent)
}

@Composable
private fun HeaderSection(
    onOpenGitHub: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        )

        IconButton(
            onClick = onOpenGitHub,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = stringResource(R.string.open_github_repository),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FooterSection(
    versionName: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_footer_info, versionName),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
        )
    }
}

@Composable
private fun EkKarartmaSwitchCard(
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    onToggleDimming: () -> Unit,
) {
    val statusText = when {
        isServiceRunning -> stringResource(R.string.status_active)
        !hasOverlayPermission -> stringResource(R.string.status_permission_required)
        else -> stringResource(R.string.status_permission_granted)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onToggleDimming() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.extra_dim_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                    ),
                    color = if (isServiceRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = isServiceRunning,
                onCheckedChange = { onToggleDimming() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

@Composable
private fun TemaModuCard(
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_mode_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeOptionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.theme_system),
                    isSelected = currentThemeMode == AppThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(AppThemeMode.SYSTEM) },
                )
                ThemeOptionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.theme_light),
                    isSelected = currentThemeMode == AppThemeMode.LIGHT,
                    onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                )
                ThemeOptionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.theme_dark),
                    isSelected = currentThemeMode == AppThemeMode.DARK,
                    onClick = { onThemeModeChange(AppThemeMode.DARK) },
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionButton(
    modifier: Modifier = Modifier,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun BatteryOptimizationCard(
    onRequestIgnore: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onRequestIgnore() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.background_protection_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.background_protection_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationPermissionWarningCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.30f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoIcon(
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun HazirProfillerCard(
    currentDimLevel: Float,
    currentBlueFilterLevel: Float,
    onSelectPreset: (dim: Float, blueFilter: Float) -> Unit,
) {
    val presets = remember {
        listOf(
            PresetItem(R.string.preset_balanced, 0.35f, 0.35f, "%35 • %35"),
            PresetItem(R.string.preset_night_reading, 0.55f, 0.65f, "%55 • %65"),
            PresetItem(R.string.preset_sleep, 0.75f, 0.85f, "%75 • %85"),
            PresetItem(R.string.preset_reset, 0.00f, 0.00f, "%0 • %0"),
        )
    }

    val currentDimPercent = (currentDimLevel * 100).roundToInt()
    val currentBluePercent = (currentBlueFilterLevel * 100).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.presets_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val p1 = presets[0]
                    val p1Selected = currentDimPercent == 35 && currentBluePercent == 35
                    PresetButton(
                        modifier = Modifier.weight(1f),
                        item = p1,
                        isSelected = p1Selected,
                        onClick = { onSelectPreset(p1.dimLevel, p1.blueFilterLevel) },
                    )

                    val p2 = presets[1]
                    val p2Selected = currentDimPercent == 55 && currentBluePercent == 65
                    PresetButton(
                        modifier = Modifier.weight(1f),
                        item = p2,
                        isSelected = p2Selected,
                        onClick = { onSelectPreset(p2.dimLevel, p2.blueFilterLevel) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val p3 = presets[2]
                    val p3Selected = currentDimPercent == 75 && currentBluePercent == 85
                    PresetButton(
                        modifier = Modifier.weight(1f),
                        item = p3,
                        isSelected = p3Selected,
                        onClick = { onSelectPreset(p3.dimLevel, p3.blueFilterLevel) },
                    )

                    val p4 = presets[3]
                    val p4Selected = currentDimPercent == 0 && currentBluePercent == 0
                    PresetButton(
                        modifier = Modifier.weight(1f),
                        item = p4,
                        isSelected = p4Selected,
                        onClick = { onSelectPreset(p4.dimLevel, p4.blueFilterLevel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    modifier: Modifier = Modifier,
    item: PresetItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subTextColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.80f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(item.nameResId),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
                color = contentColor,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                ),
                color = subTextColor,
            )
        }
    }
}

@Composable
private fun KarartmaSeviyesiSliderCard(
    dimLevel: Float,
    onDimLevelChange: (Float) -> Unit,
    onDimLevelChangeFinished: () -> Unit,
) {
    val percentageText = "%${(dimLevel * 100).roundToInt()}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dim_level_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = percentageText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Slider(
                value = dimLevel,
                onValueChange = onDimLevelChange,
                onValueChangeFinished = onDimLevelChangeFinished,
                valueRange = 0f..0.80f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

@Composable
private fun MaviIsikFiltresiSliderCard(
    blueFilterLevel: Float,
    onBlueFilterLevelChange: (Float) -> Unit,
    onBlueFilterLevelChangeFinished: () -> Unit,
) {
    val percentageText = "%${(blueFilterLevel * 100).roundToInt()}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.blue_filter_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = percentageText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Slider(
                value = blueFilterLevel,
                onValueChange = onBlueFilterLevelChange,
                onValueChangeFinished = onBlueFilterLevelChangeFinished,
                valueRange = 0f..1.00f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

@Composable
private fun QuickSettingsTileCard(
    onRequestAddTile: () -> Unit,
    statusMessage: String?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onRequestAddTile() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_settings_card_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusMessage ?: stringResource(R.string.quick_settings_card_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                ),
                color = if (statusMessage != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun OverlayPermissionInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            InfoIcon(
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.overlay_permission_info),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = tint,
            radius = radius * 0.9f,
            center = center,
            style = Stroke(width = radius * 0.18f),
        )

        drawCircle(
            color = tint,
            radius = radius * 0.12f,
            center = Offset(center.x, center.y - radius * 0.38f),
        )

        drawLine(
            color = tint,
            start = Offset(center.x, center.y - radius * 0.08f),
            end = Offset(center.x, center.y + radius * 0.45f),
            strokeWidth = radius * 0.22f,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DiminitySettingsScreenPreview() {
    DiminityTheme {
        DiminitySettingsScreen()
    }
}
