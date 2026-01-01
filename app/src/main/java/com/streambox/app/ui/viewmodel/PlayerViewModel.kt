package com.streambox.app.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.streambox.app.extension.ExtensionManager
import com.streambox.app.extension.model.StreamSource
import com.streambox.app.player.HiddenBrowserExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

private const val TAG = "PlayerViewModel"

// Helper to log to both Android Log and DebugLogManager
private fun logD(message: String) {
    Log.d(TAG, message)
    com.streambox.app.utils.DebugLogManager.d(TAG, message)
}

private fun logE(message: String) {
    Log.e(TAG, message)
    com.streambox.app.utils.DebugLogManager.e(TAG, message)
}

data class PlayerUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val availableStreams: List<StreamSource> = emptyList(),
    val showStreamSelection: Boolean = false,
    val selectedStreamIndex: Int = 0
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionManager: ExtensionManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    private var _player: ExoPlayer? = null
    val player: ExoPlayer?
        get() = _player
    
    private val hiddenBrowserExtractor = HiddenBrowserExtractor(context)
    
    init {
        createPlayer()
    }
    
    private fun createPlayer() {
        _player = ExoPlayer.Builder(context)
            .setSeekForwardIncrementMs(10_000)
            .setSeekBackIncrementMs(10_000)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isPlaying = isPlaying) }
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _uiState.update { it.copy(isLoading = true, loadingMessage = "Buffering...") }
                            }
                            Player.STATE_READY -> {
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false,
                                        duration = this@apply.duration
                                    )
                                }
                            }
                            Player.STATE_ENDED -> {
                                // Handle end of playback
                            }
                            Player.STATE_IDLE -> {
                                // Idle
                            }
                        }
                    }
                })
            }
        
        // Update position periodically
        viewModelScope.launch {
            while (isActive) {
                _player?.let { p ->
                    _uiState.update { 
                        it.copy(
                            currentPosition = p.currentPosition,
                            bufferedPosition = p.bufferedPosition
                        )
                    }
                }
                delay(1000)
            }
        }
    }
    
    fun loadStream(streamUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading...", error = null) }
            
            Log.d(TAG, "loadStream called with: $streamUrl")
            
            try {
                val activeExtension = extensionManager.activeExtension.value
                
                if (activeExtension != null) {
                    try {
                        // Get stream info from extension (includes automation rules)
                        val streams = extensionManager.getStreams(activeExtension.id, streamUrl, "movie")
                        Log.d(TAG, "Got ${streams.size} streams from extension")
                        
                        // Log each stream for debugging
                        streams.forEachIndexed { index, s ->
                            Log.d(TAG, "Stream[$index]: server=${s.server}, type=${s.type}, link=${s.link.take(50)}, hasAutomation=${s.automation != null}")
                        }
                        
                        if (streams.isEmpty()) {
                            // No streams found, try playing URL directly
                            Log.d(TAG, "No streams, playing URL directly")
                            playUrl(streamUrl)
                        } else if (streams.size == 1) {
                            handleStream(streams[0])
                        } else {
                            // Multiple streams, show selection dialog
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    availableStreams = streams,
                                    showStreamSelection = true
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Extension stream error: ${e.message}")
                        // Fallback to direct URL
                        playUrl(streamUrl)
                    }
                } else {
                    playUrl(streamUrl)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load stream"
                    )
                }
            }
        }
    }
    
    /**
     * Handle a single stream based on its type
     */
    private fun handleStream(stream: StreamSource) {
        Log.d(TAG, "handleStream type: ${stream.type}, link: ${stream.link}, headers: ${stream.headers}")
        
        when (stream.type) {
            "automate" -> {
                // Use hidden browser with automation rules
                if (stream.automation != null) {
                    extractWithHiddenBrowser(stream.link, stream.automation)
                } else {
                    Log.e(TAG, "Automate type but no automation rules")
                    playUrl(stream.link, stream.headers)
                }
            }
            "m3u8" -> playUrl(stream.link, stream.headers, isHls = true)
            "direct" -> playUrl(stream.link, stream.headers)
            else -> playUrl(stream.link, stream.headers)
        }
    }
    
    fun selectStream(index: Int) {
        val streams = _uiState.value.availableStreams
        if (index in streams.indices) {
            val selectedStream = streams[index]
            _uiState.update { 
                it.copy(
                    showStreamSelection = false,
                    selectedStreamIndex = index
                )
            }
            
            handleStream(selectedStream)
        }
    }
    
    /**
     * Extract video URLs using hidden browser with automation rules from extension
     */
    private fun extractWithHiddenBrowser(pageUrl: String, automationJson: String) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    loadingMessage = "Extracting download links..."
                ) 
            }
            
            Log.d(TAG, "Starting hidden browser extraction for: $pageUrl")
            
            try {
                val rules = JSONObject(automationJson)
                val extractedLinks = hiddenBrowserExtractor.extract(pageUrl, rules)
                Log.d(TAG, "Hidden browser extraction found ${extractedLinks.size} links")
                
                if (extractedLinks.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "No download links found"
                        )
                    }
                } else if (extractedLinks.size == 1) {
                    // Single link, play directly
                    playUrl(extractedLinks[0].url)
                } else {
                    // Multiple links, show selection
                    val streams = extractedLinks.map { link ->
                        StreamSource(
                            server = link.server.ifEmpty { link.title },
                            link = link.url,
                            type = "direct",  // These are final download URLs
                            quality = link.quality
                        )
                    }
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            availableStreams = streams,
                            showStreamSelection = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hidden browser extraction failed: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Extraction failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun dismissStreamSelection() {
        _uiState.update { it.copy(showStreamSelection = false) }
        // Play first stream as default
        val streams = _uiState.value.availableStreams
        if (streams.isNotEmpty()) {
            handleStream(streams[0])
        }
    }
    
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun playUrl(url: String, headers: Map<String, String>? = null, isHls: Boolean = false) {
        Log.d(TAG, "Playing URL: $url, headers: $headers, isHls: $isHls")
        _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading video...") }
        
        val mediaItem = MediaItem.fromUri(url)
        
        _player?.apply {
            if (headers != null && headers.isNotEmpty()) {
                // Use custom DataSource with headers
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(headers)
                    .setConnectTimeoutMs(30_000)
                    .setReadTimeoutMs(30_000)
                
                val mediaSource = if (isHls || url.contains(".m3u8")) {
                    HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                } else {
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                }
                
                setMediaSource(mediaSource)
            } else {
                setMediaItem(mediaItem)
            }
            prepare()
            playWhenReady = true
        }
        
        _uiState.update { it.copy(isLoading = false) }
    }
    
    fun togglePlayPause() {
        _player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
    }
    
    fun seekTo(position: Long) {
        _player?.seekTo(position)
    }
    
    fun seekForward() {
        _player?.seekForward()
    }
    
    fun seekBackward() {
        _player?.seekBack()
    }
    
    fun release() {
        _player?.release()
        _player = null
    }
    
    override fun onCleared() {
        super.onCleared()
        release()
    }
}
