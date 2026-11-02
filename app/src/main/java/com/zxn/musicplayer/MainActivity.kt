package com.zxn.musicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zxn.musicplayer.ui.AppRoot
import com.zxn.musicplayer.ui.LibraryViewModel
import com.zxn.musicplayer.ui.PlayerViewModel
import com.zxn.musicplayer.ui.components.ProvideShimmer
import com.zxn.musicplayer.ui.screens.OnboardingScreen
import com.zxn.musicplayer.ui.theme.ThemePrefs
import com.zxn.musicplayer.ui.theme.MusicTheme

class MainActivity : ComponentActivity() {
    private val playerVm: PlayerViewModel by viewModels()
    private val libraryVm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemePrefs.load(this)
        com.zxn.musicplayer.ui.components.SeekPrefs.load(this)
        setContent {
            MusicTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val artReady by (application as MusicApp).container.artPreloader.ready
                        .collectAsState()
                    var showLogo by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        ProvideShimmer(active = !artReady) { PermissionGate {
                            // Rescan on first show and whenever the app returns to the foreground.
                            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { libraryVm.refresh() }
                            AppRoot(playerVm, libraryVm)
                        } }
                        if (showLogo) com.zxn.musicplayer.ui.components.LogoSplash { showLogo = false }
                    }
                }
            }
        }
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    var denied by remember { mutableStateOf(false) }

    fun finish() { prefs.edit().putBoolean("onboarded", true).apply(); onboarded = true }

    // Step 2 (Android 13+): optional notification permission, then we're done whatever the answer.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; denied = !ok
        if (ok) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else finish()
        }
    }

    if (granted && onboarded) {
        content()
    } else {
        OnboardingScreen(
            startAtPermissions = onboarded,
            permissionDenied = denied,
            onAllow = {
                if (granted) {
                    // access already granted (e.g. existing install): just continue
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else finish()
                } else audioLauncher.launch(audioPermission)
            },
            onOpenSettings = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
    }
}
