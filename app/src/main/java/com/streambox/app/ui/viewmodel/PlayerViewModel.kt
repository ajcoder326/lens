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
import com.streambox.app.runtime.JSApis
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
    val selectedStreamIndex: Int = 0,
    val isWebViewMode: Boolean = false,
    val webViewUrl: String? = null,
    // Link Navigator State
    val showLinkNavigator: Boolean = false,
    val linkNavigatorLinks: List<com.streambox.app.utils.ExtractedLink> = emptyList(),
    val linkNavigatorCurrentUrl: String? = null,
    val linkNavigatorLoading: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionManager: ExtensionManager,
    private val jsApis: JSApis
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
                    
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                        logE("ExoPlayer error: ${error.errorCodeName} - ${error.message}")
                        
                        val userMessage = when (error.errorCode) {
                            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 
                                "⚠️ Device Not Compatible\n\nThis video uses a codec (HEVC/H.265) that your device cannot decode.\n\nTry selecting a different stream quality."
                            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                                "⚠️ Access Denied (403)\n\nThe server rejected the request. The link may have expired."
                            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                "⚠️ Network Error\n\nCould not connect to server. Check your internet connection."
                            else -> "Playback error: ${error.errorCodeName}"
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = userMessage
                            )
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
                        
                        // Always update available streams so user can switch later
                        _uiState.update { 
                            it.copy(availableStreams = streams)
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
        logD("handleStream type: ${stream.type}, link: ${stream.link}")
        logD("handleStream automation: ${stream.automation?.take(100)}")
        logD("handleStream headers: ${stream.headers}")
        
        when (stream.type) {
            "webview" -> {
                // WebView iframe playback
                logD("Opening WebView for iframe: ${stream.link}")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isWebViewMode = true, 
                        webViewUrl = stream.link
                    ) 
                }
            }
            "browser" -> {
                logD("Opening Visible Browser for: ${stream.link}")
                extractWithVisibleBrowser(stream)
            }
            "navigate" -> {
                logD("Opening Link Navigator for: ${stream.link}")
                startLinkNavigator(stream.link, stream.headers)
            }
            "http" -> {
                // HTTP extraction - fetch page and extract video URL via regex
                logD("HTTP extraction for: ${stream.link}")
                extractViaHttp(stream)
            }
            "automate" -> {
                // Use hidden browser with automation rules
                if (stream.automation != null) {
                    logD("Calling extractWithHiddenBrowser with automation rules")
                    extractWithHiddenBrowser(stream.link, stream.automation)
                } else {
                    logE("Automate type but no automation rules")
                    playUrl(stream.link, stream.headers)
                }
            }
            "m3u8" -> {
                logD("Playing m3u8 stream with headers")
                playUrl(stream.link, stream.headers, isHls = true)
            }
            "direct" -> {
                logD("Playing direct stream")
                playUrl(stream.link, stream.headers)
            }
            else -> {
                logD("Playing unknown type as direct: ${stream.type}")
                playUrl(stream.link, stream.headers)
            }
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
    
    fun showStreamSelection() {
        if (_uiState.value.availableStreams.isNotEmpty()) {
            _uiState.update { it.copy(showStreamSelection = true) }
        }
    }
    
    /**
     * Extract video URL via HTTP request and regex patterns
     */
    private fun extractViaHttp(stream: StreamSource) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    loadingMessage = "Extracting video link..."
                ) 
            }
            
            try {
                // Parse extraction rules from stream
                val automation = stream.automation?.let { JSONObject(it) }
                val extraction = automation?.optJSONObject("extraction")
                
                if (extraction == null) {
                    logE("No extraction rules found in stream")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Invalid extraction configuration"
                        )
                    }
                    return@launch
                }
                
                val method = extraction.optString("method", "GET")
                val headersJson = extraction.optJSONObject("headers")
                val patternsArray = extraction.optJSONArray("patterns")
                val videoHeadersJson = extraction.optJSONObject("videoHeaders")
                
                // Build headers map
                val headers = mutableMapOf<String, String>()
                headersJson?.keys()?.forEach { key ->
                    headers[key] = headersJson.getString(key)
                }
                
                logD("HTTP $method: ${stream.link} with headers: $headers")
                
                // Fetch page content
                val html = if (method == "GET") {
                    jsApis.httpGet(stream.link, headers)
                } else {
                    jsApis.httpPost(stream.link, "", headers)
                }
                
                logD("Fetched HTML (${html.length} chars), trying ${patternsArray?.length() ?: 0} patterns")
                
                // Try each regex pattern
                var videoUrl: String? = null
                if (patternsArray != null) {
                    for (i in 0 until patternsArray.length()) {
                        val pattern = patternsArray.getString(i)
                        val regex = Regex(pattern)
                        val match = regex.find(html)
                        if (match != null && match.groupValues.size > 1) {
                            videoUrl = match.groupValues[1]
                            logD("Pattern matched: $pattern")
                            logD("Extracted URL: ${videoUrl.take(100)}")
                            break
                        }
                    }
                }
                
                if (videoUrl != null) {
                    // Build video headers
                    val videoHeaders = mutableMapOf<String, String>()
                    videoHeadersJson?.keys()?.forEach { key ->
                        videoHeaders[key] = videoHeadersJson.getString(key)
                    }
                    
                    logD("Playing extracted video with headers: $videoHeaders")
                    playUrl(videoUrl, videoHeaders.takeIf { it.isNotEmpty() }, isHls = videoUrl.contains(".m3u8"))
                } else {
                    logE("No video URL found with any pattern")
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Failed to extract video URL"
                        )
                    }
                }
            } catch (e: Exception) {
                logE("HTTP extraction failed: ${e.message}")
                Log.e(TAG, "HTTP extraction error", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Extraction failed: ${e.message}"
                    )
                }
            }
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
            
            logD("extractWithHiddenBrowser - Starting for: $pageUrl")
            logD("extractWithHiddenBrowser - Automation JSON: ${automationJson.take(200)}")
            
            try {
                val rules = JSONObject(automationJson)
                logD("extractWithHiddenBrowser - Parsed rules, steps: ${rules.optJSONArray("steps")?.length() ?: 0}")
                val extractedLinks = hiddenBrowserExtractor.extract(pageUrl, rules)
                logD("extractWithHiddenBrowser - Found ${extractedLinks.size} links")
                
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
    
    /**
     * Open visible browser for manual extraction (e.g. for difficult captchas/overlays)
     */
    private fun extractWithVisibleBrowser(stream: StreamSource) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true, 
                    loadingMessage = "Waiting for video in browser..."
                ) 
            }
            
            try {
                val requestId = java.util.UUID.randomUUID().toString()
                val deferred = kotlinx.coroutines.CompletableDeferred<String>()
                com.streambox.app.utils.BrowserBus.requests[requestId] = deferred
                
                val intent = android.content.Intent(context, com.streambox.app.ui.VisibleBrowserActivity::class.java).apply {
                    putExtra("URL", stream.link)
                    putExtra("REQUEST_ID", requestId)
                    if (stream.headers?.containsKey("User-Agent") == true) {
                        putExtra("USER_AGENT", stream.headers["User-Agent"])
                    }
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                
                // Wait for result
                val resultJsonStr = deferred.await()
                com.streambox.app.utils.BrowserBus.requests.remove(requestId)
                
                if (resultJsonStr.isNotEmpty()) {
                    val json = JSONObject(resultJsonStr)
                    val videoUrl = json.getString("url")
                    val headersJson = json.optJSONObject("headers")
                    val headers = mutableMapOf<String, String>()
                    headersJson?.keys()?.forEach { key ->
                        headers[key] = headersJson.getString(key)
                    }
                    
                    logD("Browser captured video: $videoUrl")
                    playUrl(videoUrl, headers)
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Browser closed without playing"
                        )
                    }
                }
            } catch (e: Exception) {
                logE("Visible browser error: ${e.message}")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Browser error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Start the Link Navigator with an initial URL
     */
    private fun startLinkNavigator(url: String, headers: Map<String, String>?) {
        _uiState.update { 
            it.copy(
                isLoading = false,
                showLinkNavigator = true,
                linkNavigatorLoading = true,
                linkNavigatorCurrentUrl = url,
                linkNavigatorLinks = emptyList()
            )
        }
        
        viewModelScope.launch {
            fetchAndExtractLinks(url, headers)
        }
    }

    /**
     * Navigate to a selected link
     */
    fun navigateLink(link: com.streambox.app.utils.ExtractedLink) {
        logD("User selected link: ${link.text} -> ${link.url}")
        
        // If it's a video URL, play it directly
        if (link.type == com.streambox.app.utils.LinkType.VIDEO || 
            com.streambox.app.utils.LinkExtractor.isVideoUrl(link.url)) {
            logD("Direct video URL detected, playing: ${link.url}")
            _uiState.update { it.copy(showLinkNavigator = false) }
            playUrl(link.url, mapOf(
                "Referer" to (_uiState.value.linkNavigatorCurrentUrl ?: link.url),
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
            ))
            return
        }
        
        // Otherwise, fetch the next page
        _uiState.update { 
            it.copy(
                linkNavigatorLoading = true,
                linkNavigatorCurrentUrl = link.url,
                linkNavigatorLinks = emptyList()
            )
        }
        
        viewModelScope.launch {
            fetchAndExtractLinks(link.url, null)
        }
    }

    /**
     * Fetch a page and extract links
     */
    private suspend fun fetchAndExtractLinks(url: String, headers: Map<String, String>?) {
        try {
            logD("Fetching page for link extraction: $url")
            
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .cookieJar(jsApis.getCookieJar())
                .build()
            
            val requestBuilder = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            
            headers?.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: ""
            
            logD("Fetched ${html.length} chars from $url")
            
            // Check if we got redirected to a video URL
            val finalUrl = response.request.url.toString()
            if (com.streambox.app.utils.LinkExtractor.isVideoUrl(finalUrl)) {
                logD("Redirected to video URL: $finalUrl")
                _uiState.update { it.copy(showLinkNavigator = false) }
                playUrl(finalUrl, mapOf("Referer" to url))
                return
            }
            
            // Extract links from HTML
            val links = com.streambox.app.utils.LinkExtractor.extractLinks(html, url)
            logD("Extracted ${links.size} links")
            
            // Check if any extracted link is a direct video URL
            val videoLink = links.find { it.type == com.streambox.app.utils.LinkType.VIDEO }
            if (videoLink != null) {
                logD("Found video link in extracted links: ${videoLink.url}")
                _uiState.update { it.copy(showLinkNavigator = false) }
                playUrl(videoLink.url, mapOf("Referer" to url))
                return
            }
            
            _uiState.update { 
                it.copy(
                    linkNavigatorLoading = false,
                    linkNavigatorLinks = links,
                    linkNavigatorCurrentUrl = finalUrl
                )
            }
            
        } catch (e: Exception) {
            logE("Link extraction error: ${e.message}")
            _uiState.update { 
                it.copy(
                    linkNavigatorLoading = false,
                    error = "Failed to load page: ${e.message}"
                )
            }
        }
    }

    /**
     * Dismiss the Link Navigator dialog
     */
    fun dismissLinkNavigator() {
        _uiState.update { 
            it.copy(
                showLinkNavigator = false,
                linkNavigatorLinks = emptyList(),
                linkNavigatorCurrentUrl = null
            )
        }
    }

    fun dismissStreamSelection() {
        _uiState.update { it.copy(showStreamSelection = false) }
        
        // Only play default if nothing is loaded yet
        if (_player?.currentMediaItem == null) {
            val streams = _uiState.value.availableStreams
            if (streams.isNotEmpty()) {
                handleStream(streams[0])
            }
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
