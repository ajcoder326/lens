package com.streambox.app

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.streambox.app.extension.ExtensionInstaller
import com.streambox.app.player.PipHelper
import com.streambox.app.ui.navigation.StreamBoxNavHost
import com.streambox.app.ui.theme.StreamBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// CompositionLocal to provide PIP state to composables
val LocalPipMode = compositionLocalOf { mutableStateOf(false) }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var extensionInstaller: ExtensionInstaller
    
    // Track PIP mode state
    private val isInPipMode = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Install bundled extensions on first run
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "Installing bundled extensions...")
                extensionInstaller.installBundledExtensions()
                Log.d("MainActivity", "Extensions installed successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to install extensions", e)
            }
        }
        
        setContent {
            StreamBoxTheme {
                CompositionLocalProvider(LocalPipMode provides isInPipMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        StreamBoxNavHost()
                    }
                }
            }
        }
    }
    
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
        Log.d("MainActivity", "PIP mode changed: $isInPictureInPictureMode")
    }
    
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Note: Auto-enter PIP is handled by PipHelper.setupAutoPip() on Android 12+
        // For older versions, PlayerScreen handles this via onUserLeaveHint callback
    }
}
