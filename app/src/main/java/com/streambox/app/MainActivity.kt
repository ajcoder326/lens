package com.streambox.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.streambox.app.extension.ExtensionInstaller
import com.streambox.app.ui.navigation.StreamBoxNavHost
import com.streambox.app.ui.theme.StreamBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var extensionInstaller: ExtensionInstaller
    
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
