package com.streambox.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.streambox.app.extension.ExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L
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
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Extracting stream...", error = null) }
            
            try {
                // For now, directly play the URL
                // In future, this will extract streams using extension
                val activeExtension = extensionManager.activeExtension.value
                
                val finalUrl = if (activeExtension != null) {
                    try {
                        // Try to extract stream using extension
                        val streams = extensionManager.getStreams(activeExtension.id, streamUrl, "movie")
                        streams.firstOrNull()?.link ?: streamUrl
                    } catch (e: Exception) {
                        // Fallback to direct URL
                        streamUrl
                    }
                } else {
                    streamUrl
                }
                
                _uiState.update { it.copy(loadingMessage = "Loading video...") }
                
                val mediaItem = MediaItem.fromUri(finalUrl)
                _player?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
                
                _uiState.update { it.copy(isLoading = false) }
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
