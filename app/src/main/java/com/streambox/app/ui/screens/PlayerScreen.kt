package com.streambox.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import com.streambox.app.LocalPipMode
import com.streambox.app.player.PipHelper
import com.streambox.app.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String?,
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val uiState by viewModel.uiState.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var controlsTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // PIP mode state
    val isInPipMode by LocalPipMode.current
    val isPipSupported = remember { PipHelper.isPipSupported(context) }
    
    // Volume and brightness state
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var currentBrightness by remember { mutableFloatStateOf(
        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
    ) }
    
    // Gesture indicator state
    var gestureIndicator by remember { mutableStateOf<GestureIndicator?>(null) }
    
    // Zoom mode state: 0 = Fit, 1 = Fill, 2 = Zoom
    var zoomMode by remember { mutableIntStateOf(0) }
    
    // Seek gesture state
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    
    // Lock to landscape and fullscreen
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.decorView?.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        viewModel.loadStream(streamUrl)
    }
    
    // Setup auto-enter PIP when player is ready and playing
    LaunchedEffect(uiState.isPlaying) {
        if (uiState.isPlaying && activity != null && isPipSupported) {
            PipHelper.setupAutoPip(activity, viewModel.player)
        }
    }
    
    // Handle lifecycle events for PIP
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Enter PIP if playing and leaving app (for Android < 12)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                        uiState.isPlaying && 
                        activity != null && 
                        isPipSupported &&
                        !PipHelper.isInPipMode(activity)) {
                        PipHelper.enterPipMode(activity, viewModel.player)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    // If we're in PIP mode and user closed PIP, navigate back
                    if (activity != null && !PipHelper.isInPipMode(activity) && isInPipMode) {
                        // PIP was closed
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Restore orientation on exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Disable auto-PIP when leaving player
            activity?.let { PipHelper.disableAutoPip(it) }
            viewModel.release()
        }
    }
    
    // Auto-hide controls (not in PIP mode)
    LaunchedEffect(showControls, isInPipMode) {
        if (showControls && !isInPipMode) {
            delay(5000)
            showControls = false
        }
    }
    
    // Auto-hide gesture indicator
    LaunchedEffect(gestureIndicator) {
        if (gestureIndicator != null) {
            delay(1000)
            gestureIndicator = null
        }
    }
    
    // Enter PIP mode function
    val enterPipMode: () -> Unit = {
        activity?.let { act ->
            PipHelper.enterPipMode(act, viewModel.player)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            // WebView mode for iframe playback
            uiState.isWebViewMode && uiState.webViewUrl != null -> {
                WebViewPlayer(
                    url = uiState.webViewUrl!!,
                    onBack = onNavigateBack
                )
            }
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.loadingMessage ?: "Loading...",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        OutlinedButton(onClick = { viewModel.loadStream(streamUrl) }) {
                            Text("Retry")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
            else -> {
                // Player View with zoom support
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = viewModel.player
                            useController = false // We use custom controls
                            // Default to FIT mode
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { playerView ->
                        playerView.player = viewModel.player
                        // Update resize mode based on zoom state
                        playerView.resizeMode = when (zoomMode) {
                            0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                )
                
                // Hide all overlays in PIP mode - just show the video
                if (!isInPipMode) {
                    // Gesture detection zones
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left side - Brightness
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { gestureIndicator = null },
                                        onDragCancel = { gestureIndicator = null }
                                    ) { _, dragAmount ->
                                        val delta = -dragAmount / size.height * 1.5f
                                        currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                        activity?.window?.let { window ->
                                            val layoutParams = window.attributes
                                            layoutParams.screenBrightness = currentBrightness
                                            window.attributes = layoutParams
                                        }
                                        gestureIndicator = GestureIndicator.Brightness(currentBrightness)
                                    }
                                }
                                .clickable { showControls = !showControls }
                        )
                        
                        // Right side - Volume
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { gestureIndicator = null },
                                        onDragCancel = { gestureIndicator = null }
                                    ) { _, dragAmount ->
                                        val delta = -dragAmount / size.height * 1.5f
                                        currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                                        val newVolume = (currentVolume * maxVolume).toInt()
                                        audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        newVolume,
                                        0
                                    )
                                    gestureIndicator = GestureIndicator.Volume(currentVolume)
                                }
                            }
                            .clickable { showControls = !showControls }
                    )
                }
                
                // Gesture Indicator Overlay
                gestureIndicator?.let { indicator ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    when (indicator) {
                                        is GestureIndicator.Volume -> 
                                            if (indicator.level > 0) Icons.Default.VolumeUp else Icons.Default.VolumeOff
                                        is GestureIndicator.Brightness -> Icons.Default.BrightnessHigh
                                        is GestureIndicator.Seek -> 
                                            if (indicator.delta >= 0) Icons.Default.FastForward else Icons.Default.FastRewind
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                when (indicator) {
                                    is GestureIndicator.Volume, is GestureIndicator.Brightness -> {
                                        LinearProgressIndicator(
                                            progress = { when (indicator) {
                                                is GestureIndicator.Volume -> indicator.level
                                                is GestureIndicator.Brightness -> indicator.level
                                                else -> 0f
                                            } },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = Color.White.copy(alpha = 0.3f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    is GestureIndicator.Seek -> {}
                                }
                                Text(
                                    text = when (indicator) {
                                        is GestureIndicator.Volume -> "Volume: ${(indicator.level * 100).toInt()}%"
                                        is GestureIndicator.Brightness -> "Brightness: ${(indicator.level * 100).toInt()}%"
                                        is GestureIndicator.Seek -> {
                                            val sign = if (indicator.delta >= 0) "+" else ""
                                            "${formatDuration(indicator.position)} (${sign}${indicator.delta / 1000}s)"
                                        }
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Custom Controls Overlay
                if (showControls) {
                    PlayerControlsOverlay(
                        title = title,
                        isPlaying = uiState.isPlaying,
                        currentPosition = uiState.currentPosition,
                        duration = uiState.duration,
                        bufferedPosition = uiState.bufferedPosition,
                        isPipSupported = isPipSupported,
                        zoomMode = zoomMode,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onSeekForward = { viewModel.seekForward() },
                        onSeekBackward = { viewModel.seekBackward() },
                        onBack = onNavigateBack,
                        onPip = enterPipMode,
                        onZoom = { zoomMode = (zoomMode + 1) % 3 },
                        onQuality = { viewModel.showStreamSelection() },
                        onSubtitles = { /* TODO: Show subtitle picker */ },
                        onAudio = { /* TODO: Show audio picker */ },
                        onSpeed = { /* TODO: Show speed picker */ }
                    )
                }
                } // End of !isInPipMode block
            }
        }
        
        // Stream Selection Dialog
        if (uiState.showStreamSelection && uiState.availableStreams.isNotEmpty()) {
            Dialog(onDismissRequest = { viewModel.dismissStreamSelection() }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Select Server",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        LazyColumn {
                            itemsIndexed(uiState.availableStreams) { index, stream ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.selectStream(index) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = stream.server,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            stream.quality?.let { quality ->
                                                Text(
                                                    text = quality,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = { viewModel.dismissStreamSelection() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
        
        // Link Navigator Dialog
        if (uiState.showLinkNavigator) {
            com.streambox.app.ui.components.LinkNavigatorDialog(
                links = uiState.linkNavigatorLinks,
                currentUrl = uiState.linkNavigatorCurrentUrl,
                isLoading = uiState.linkNavigatorLoading,
                onLinkClick = { link -> viewModel.navigateLink(link) },
                onDismiss = { viewModel.dismissLinkNavigator() }
            )
        }
    }
}

// Gesture indicator types
sealed class GestureIndicator {
    data class Volume(val level: Float) : GestureIndicator()
    data class Brightness(val level: Float) : GestureIndicator()
    data class Seek(val position: Long, val delta: Long) : GestureIndicator()
}

@Composable
fun PlayerControlsOverlay(
    title: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isPipSupported: Boolean = false,
    zoomMode: Int = 0,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit = {},
    onZoom: () -> Unit = {},
    onQuality: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onSpeed: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // PIP button (if supported)
            if (isPipSupported) {
                IconButton(onClick = onPip) {
                    Icon(Icons.Default.PictureInPicture, contentDescription = "Picture in Picture", tint = Color.White)
                }
            }
            
            // Right controls
            IconButton(onClick = onSpeed) {
                Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
            }
            IconButton(onClick = onAudio) {
                Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White)
            }
            IconButton(onClick = onSubtitles) {
                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
            }
            IconButton(onClick = onQuality) {
                Icon(Icons.Default.HighQuality, contentDescription = "Quality", tint = Color.White)
            }
            // Zoom button
            IconButton(onClick = onZoom) {
                Icon(
                    when (zoomMode) {
                        0 -> Icons.Default.FitScreen  // FIT
                        1 -> Icons.Default.ZoomOutMap // ZOOM
                        else -> Icons.Default.Fullscreen // FILL
                    },
                    contentDescription = when (zoomMode) {
                        0 -> "Fit"
                        1 -> "Zoom"
                        else -> "Fill"
                    },
                    tint = Color.White
                )
            }
        }
        
        // Center controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBackward, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Default.Replay10,
                    contentDescription = "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            
            IconButton(onClick = onSeekForward, modifier = Modifier.size(56.dp)) {
                Icon(
                    Icons.Default.Forward10,
                    contentDescription = "Forward 10s",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        // Bottom bar with seeker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Slider(
                value = if (isDragging) sliderValue else (if (duration > 0) currentPosition.toFloat() / duration else 0f),
                onValueChange = { 
                    isDragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek((sliderValue * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatDuration(duration),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * WebView player for iframe-based streaming (TMDB servers)
 */
@Composable
fun WebViewPlayer(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportZoom(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            // Allow all URLs to load in the WebView
                            return false
                        }
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        // Support fullscreen video playback
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            super.onShowCustomView(view, callback)
                        }
                        
                        override fun onHideCustomView() {
                            super.onHideCustomView()
                        }
                    }
                    
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                // Update URL if needed
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            }
        )
        
        // Back button overlay
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
