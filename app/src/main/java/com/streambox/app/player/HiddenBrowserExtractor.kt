package com.streambox.app.player

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Hidden Browser Extractor - DOM-only extraction
 * 
 * Flow:
 * 1. Load page → parse DOM → extract next URL (no clicking!)
 * 2. Navigate to next page → parse DOM → extract next URL
 * 3. Final page → extract all download links from DOM
 * 
 * This is fast because:
 * - No waiting for ad countdowns
 * - No clicking through overlays
 * - Just DOM parsing at each step
 * 
 * All extraction rules come from the extension's stream.js
 */
class HiddenBrowserExtractor(private val context: Context) {

    companion object {
        private const val TAG = "HiddenBrowserExtractor"
        private const val DEFAULT_TIMEOUT = 45_000L
        private const val PAGE_LOAD_DELAY = 1500L // Wait for DOM to be ready
        
        // Helper to log to both Android Log and DebugLogManager
        private fun logD(message: String) {
            android.util.Log.d(TAG, message)
            com.streambox.app.utils.DebugLogManager.d(TAG, message)
        }
        
        private fun logE(message: String) {
            android.util.Log.e(TAG, message)
            com.streambox.app.utils.DebugLogManager.e(TAG, message)
        }
    }

    private var webView: WebView? = null
    private var extractedLinks = mutableListOf<ExtractedLink>()
    private var completionCallback: ((List<ExtractedLink>) -> Unit)? = null
    private var steps: JSONArray? = null
    private var currentStep = 0
    private var blockedPatterns: List<String> = emptyList()

    data class ExtractedLink(
        val url: String,
        val title: String,
        val server: String = "",
        val quality: String = ""
    )

    /**
     * Extract download URLs using DOM-only navigation
     */
    suspend fun extract(url: String, rules: JSONObject): List<ExtractedLink> = withTimeout(DEFAULT_TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            extractedLinks.clear()
            steps = rules.optJSONArray("steps")
            
            // Load blocked patterns from extension rules
            val patternsJson = rules.optJSONArray("blockedPatterns")
            val patternList = mutableListOf<String>()
            if (patternsJson != null) {
                for (i in 0 until patternsJson.length()) {
                    patternList.add(patternsJson.getString(i))
                }
            } else {
                // Default safe blocks only if none provided
                patternList.add("analytics")
                patternList.add("tracking")
            }
            blockedPatterns = patternList
            
            currentStep = 0
            
            Handler(Looper.getMainLooper()).post {
                try {
                    setupWebView()
                    
                    completionCallback = { links ->
                        cleanupWebView()
                        if (continuation.isActive) {
                            continuation.resume(links)
                        }
                    }
                    
                    logD("Starting DOM extraction for: $url")
                    logD("Steps: ${steps?.length() ?: 0}")
                    webView?.loadUrl(url)
                    
                } catch (e: Exception) {
                    logE("Extraction failed: ${e.message}")
                    cleanupWebView()
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
            }
            
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post {
                    cleanupWebView()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                
                // Block images, CSS, fonts for faster loading - we only need DOM
                blockNetworkImage = true
                loadsImagesAutomatically = false
                mediaPlaybackRequiresUserGesture = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                
                // Mobile user agent
                userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val urlLower = url.lowercase()
                
                // CAPTURE video URLs - m3u8 for streaming, mp4 for direct downloads
                val isM3u8 = (urlLower.contains(".m3u8") || urlLower.contains("/hls")) && 
                    !urlLower.contains("beacon") && !urlLower.contains("analytics")
                
                // Capture direct MP4/MKV download URLs (from speedostream, hubcloud, etc.)
                val isMp4 = (urlLower.contains(".mp4") || urlLower.contains(".mkv")) &&
                    (urlLower.contains("ydc1wes.me") || urlLower.contains("/v/") || urlLower.contains("/d/")) &&
                    !urlLower.contains("thumb") && !urlLower.contains("preview")
                
                if (isM3u8 || isMp4) {
                    val videoType = if (isM3u8) "m3u8" else "mp4"
                    logD("🎬 Captured $videoType URL: ${url.take(100)}...")
                    
                    // Complete with this video URL - only once
                    if (completionCallback != null) {
                        val capturedLink = ExtractedLink(
                            url = url,
                            title = if (isMp4) "Download" else "Stream",
                            server = if (isMp4) "Direct Download" else "Stream",
                            quality = when {
                                urlLower.contains("_x") || urlLower.contains(",x,") -> "UHD"
                                urlLower.contains("_h") || urlLower.contains(",h,") -> "HD"
                                else -> "SD"
                            }
                        )
                        
                        // Call completion callback on main thread
                        Handler(Looper.getMainLooper()).post {
                            completionCallback?.invoke(listOf(capturedLink))
                            completionCallback = null  // Prevent duplicate calls
                        }
                    }
                    
                    // Don't block MP4 downloads - let them proceed (for download functionality)
                    if (isM3u8) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                }
                
                // Block ads and unnecessary resources based on EXTENSION rules
                if (blockedPatterns.any { urlLower.contains(it) }) {
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }
                
                // Always block common media types to speed up scraping if not captured above
                val mediaTypes = listOf(
                    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
                    ".woff", ".woff2", ".ttf"
                )
                
                if (mediaTypes.any { urlLower.contains(it) }) {
                     return WebResourceResponse("text/plain", "UTF-8", null)
                }
                
                return super.shouldInterceptRequest(view, request)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                logD("Page loaded: $url")
                
                // Wait a bit for DOM to settle, then process
                Handler(Looper.getMainLooper()).postDelayed({
                    processCurrentStep(view, url ?: "")
                }, PAGE_LOAD_DELAY)
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    logE("Page load error: ${error?.description}")
                }
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            // Block all popups
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
            
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.cancel()
                return true
            }
        }
    }

    /**
     * Process current step - extract data from DOM and navigate or complete
     */
    private fun processCurrentStep(view: WebView?, url: String) {
        if (view == null || steps == null) {
            completionCallback?.invoke(extractedLinks)
            return
        }
        
        if (currentStep >= steps!!.length()) {
            logD("All steps completed, returning ${extractedLinks.size} links")
            completionCallback?.invoke(extractedLinks)
            return
        }
        
        val step = steps!!.optJSONObject(currentStep)
        if (step == null) {
            currentStep++
            processCurrentStep(view, url)
            return
        }
        
        val action = step.optString("action")
        logD("Step $currentStep: $action on $url")
        
        when (action) {
            "extractUrl" -> extractUrlFromDom(view, step)
            "extractLinks" -> extractLinksFromDom(view, step)
            "waitAndClick" -> waitAndClickButton(view, step)
            "wait", "waitForElement" -> waitForElement(view, step)
            "click", "clickElement" -> clickElement(view, step)
            "extractVideoUrl" -> extractVideoUrlFromDom(view, step)
            "complete" -> completionCallback?.invoke(extractedLinks)
            else -> {
                logE("Unknown action: $action")
                currentStep++
                processCurrentStep(view, url)
            }
        }
    }

    /**
     * Extract a single URL from DOM and navigate to it
     */
    private fun extractUrlFromDom(view: WebView, step: JSONObject) {
        val selectors = step.optJSONArray("selectors") ?: JSONArray()
        val patterns = step.optJSONArray("patterns") ?: JSONArray()
        
        // Build JS to find URL
        val selectorsJs = buildSelectorsList(selectors)
        val patternsJs = buildPatternsList(patterns)
        
        val script = """
            (function() {
                var selectors = $selectorsJs;
                var patterns = $patternsJs;
                
                // Try each selector
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var el = document.querySelector(selectors[i]);
                        if (el && el.href && el.href.startsWith('http')) {
                            return el.href;
                        }
                    } catch(e) {}
                }
                
                // Fallback: search all links for patterns
                var links = document.querySelectorAll('a');
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].href || '';
                    var text = (links[i].textContent || '').toLowerCase();
                    for (var j = 0; j < patterns.length; j++) {
                        var p = patterns[j].toLowerCase();
                        if (href.toLowerCase().indexOf(p) !== -1 || text.indexOf(p) !== -1) {
                            if (href.startsWith('http')) {
                                return href;
                            }
                        }
                    }
                }
                
                return null;
            })();
        """.trimIndent()
        
        view.evaluateJavascript(script) { result ->
            val extractedUrl = result?.trim('"')?.takeIf { it != "null" && it.startsWith("http") }
            
            if (extractedUrl != null) {
                logD("Extracted URL: ${extractedUrl.take(80)}...")
                currentStep++
                // Navigate to extracted URL
                view.loadUrl(extractedUrl)
            } else {
                logE("Failed to extract URL at step $currentStep")
                completionCallback?.invoke(extractedLinks)
            }
        }
    }

    /**
     * Extract multiple download links from DOM (final step)
     * All patterns come from extension - nothing hardcoded
     */
    private fun extractLinksFromDom(view: WebView, step: JSONObject) {
        val selectors = step.optJSONArray("selectors") ?: JSONArray()
        val excludePatterns = step.optJSONArray("excludePatterns") ?: JSONArray()
        // Server patterns from extension (optional - if not provided, accept all matched selectors)
        val serverPatterns = step.optJSONArray("serverPatterns") ?: JSONArray()
        
        val selectorsJs = buildSelectorsList(selectors)
        val excludeJs = buildPatternsList(excludePatterns)
        val serverPatternsJs = buildPatternsList(serverPatterns)
        
        val script = """
            (function() {
                var selectors = $selectorsJs;
                var excludePatterns = $excludeJs;
                var serverPatterns = $serverPatternsJs;
                var results = [];
                var seen = {};
                
                // Try all selectors
                for (var s = 0; s < selectors.length; s++) {
                    try {
                        var elements = document.querySelectorAll(selectors[s]);
                        for (var i = 0; i < elements.length; i++) {
                            var el = elements[i];
                            var href = el.href || el.getAttribute('href') || '';
                            var text = (el.textContent || '').trim();
                            
                            if (!href.startsWith('http')) continue;
                            if (seen[href]) continue;
                            
                            // Check excludes
                            var excluded = false;
                            for (var j = 0; j < excludePatterns.length; j++) {
                                if (href.toLowerCase().indexOf(excludePatterns[j].toLowerCase()) !== -1) {
                                    excluded = true;
                                    break;
                                }
                            }
                            if (excluded) continue;
                            
                            // Extract server name from [Server Name] format
                            var serverMatch = text.match(/\[(.+?)\]/);
                            var server = serverMatch ? serverMatch[1] : '';
                            
                            // Check if it's a download link (use extension patterns or accept all if none provided)
                            var isDownloadLink = serverPatterns.length === 0 || serverPatterns.some(function(p) {
                                return href.toLowerCase().indexOf(p.toLowerCase()) !== -1;
                            });
                            var hasServerClass = el.className.indexOf('btn-success') !== -1 || 
                                                 el.className.indexOf('btn-primary') !== -1;
                            
                            if (isDownloadLink || hasServerClass || server) {
                                seen[href] = true;
                                results.push({
                                    url: href,
                                    title: text.substring(0, 60),
                                    server: server || 'Download'
                                });
                            }
                        }
                    } catch(e) {}
                }
                
                return JSON.stringify(results);
            })();
        """.trimIndent()
        
        view.evaluateJavascript(script) { result ->
            try {
                val cleaned = result?.trim('"')?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: "[]"
                val jsonArray = JSONArray(cleaned)
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    extractedLinks.add(ExtractedLink(
                        url = obj.optString("url"),
                        title = obj.optString("title"),
                        server = obj.optString("server")
                    ))
                }
                
                logD("Extracted ${extractedLinks.size} download links")
            } catch (e: Exception) {
                logE("Failed to parse extracted links: ${e.message}")
            }
            
            currentStep++
            // Check if there are more steps or complete
            if (currentStep >= (steps?.length() ?: 0)) {
                completionCallback?.invoke(extractedLinks)
            } else {
                webView?.url?.let { processCurrentStep(webView!!, it) }
            }
        }
    }

    /**
     * Wait for a condition and click a button
     * Useful for intermediary pages with timers (like gadgetsweb.xyz)
     */
    private fun waitAndClickButton(view: WebView, step: JSONObject) {
        val selectors = step.optJSONArray("selectors") ?: JSONArray()
        val waitMs = step.optLong("waitMs", 1000) // Default wait 1 second
        val waitForSelector = step.optString("waitForSelector", "") // Selector to wait for
        val waitForText = step.optString("waitForText", "") // Wait for button text to contain this
        val maxRetries = step.optInt("maxRetries", 15) // Max retries for waiting
        val retryInterval = step.optLong("retryInterval", 1000) // Retry every second
        
        val selectorsJs = buildSelectorsList(selectors)
        
        // Script to find and click the button
        val clickScript = """
            (function() {
                var selectors = $selectorsJs;
                
                // Try each selector
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var el = document.querySelector(selectors[i]);
                        if (el && !el.disabled) {
                            el.click();
                            return 'clicked';
                        }
                    } catch(e) {}
                }
                
                // Also try by text content for buttons
                var buttons = document.querySelectorAll('button, a.btn, a.btn2, input[type="submit"], input[type="button"]');
                for (var i = 0; i < buttons.length; i++) {
                    var text = (buttons[i].textContent || buttons[i].value || '').toLowerCase();
                    for (var j = 0; j < selectors.length; j++) {
                        if (text.indexOf(selectors[j].toLowerCase()) !== -1) {
                            buttons[i].click();
                            return 'clicked';
                        }
                    }
                }
                
                return 'notfound';
            })();
        """.trimIndent()
        
        // If we need to wait for button text to change
        if (waitForText.isNotEmpty()) {
            waitForButtonText(view, selectors, waitForText, maxRetries, retryInterval) { found ->
                if (found) {
                    // Button text changed, now wait additional time and click
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        view.evaluateJavascript(clickScript) { result ->
                            handleClickResult(view, result)
                        }
                    }, waitMs)
                } else {
                    logE("Timeout waiting for button text: $waitForText")
                    // Try to click anyway
                    view.evaluateJavascript(clickScript) { result ->
                        handleClickResult(view, result)
                    }
                }
            }
        }
        // If we need to wait for a specific element first
        else if (waitForSelector.isNotEmpty()) {
            waitForElement(view, waitForSelector, maxRetries, retryInterval) { found ->
                if (found) {
                    // Element found, now wait additional time and click
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        view.evaluateJavascript(clickScript) { result ->
                            handleClickResult(view, result)
                        }
                    }, waitMs)
                } else {
                    logE("Timeout waiting for element: $waitForSelector")
                    // Try to click anyway
                    view.evaluateJavascript(clickScript) { result ->
                        handleClickResult(view, result)
                    }
                }
            }
        } else {
            // Just wait and click
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                view.evaluateJavascript(clickScript) { result ->
                    handleClickResult(view, result)
                }
            }, waitMs)
        }
    }
    
    /**
     * Wait for an element to appear in DOM
     */
    private fun waitForElement(
        view: WebView, 
        step: JSONObject
    ) {
        val selector = step.optString("selector", "video")
        val timeout = step.optInt("timeout", 10000)
        val checkInterval = 500L
        val maxRetries = (timeout / checkInterval).toInt()
        
        waitForElement(view, selector, maxRetries, checkInterval, 0) { found ->
            if (found) {
                logD("Element found, proceeding: $selector")
                currentStep++
                view.url?.let { processCurrentStep(view, it) }
            } else {
                logE("Timeout waiting for element: $selector")
                // Could be end of chain or error, maybe just proceed?
                // For now, proceed
                currentStep++
                view.url?.let { processCurrentStep(view, it) }
            }
        }
    }
    
    private fun waitForElement(
        view: WebView, 
        selector: String, 
        maxRetries: Int, 
        retryInterval: Long,
        retryCount: Int = 0,
        callback: (Boolean) -> Unit
    ) {
        val checkScript = """
            (function() {
                try {
                    var el = document.querySelector('$selector');
                    if (el && !el.disabled && el.offsetParent !== null) {
                        return 'found';
                    }
                } catch(e) {}
                return 'waiting';
            })();
        """.trimIndent()
        
        view.evaluateJavascript(checkScript) { result ->
            val status = result?.trim('"') ?: "waiting"
            
            if (status == "found") {
                logD("Element found: $selector")
                callback(true)
            } else if (retryCount < maxRetries) {
                logD("Waiting for element ($retryCount/$maxRetries): $selector")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    waitForElement(view, selector, maxRetries, retryInterval, retryCount + 1, callback)
                }, retryInterval)
            } else {
                logE("Max retries reached waiting for: $selector")
                callback(false)
            }
        }
    }
    
    /**
     * Wait for button text to contain specific text (for timer buttons)
     * e.g., Wait for button to change from "PLEASE WAIT..." to "GET LINKS"
     */
    private fun waitForButtonText(
        view: WebView, 
        selectors: JSONArray,
        targetText: String,
        maxRetries: Int, 
        retryInterval: Long,
        retryCount: Int = 0,
        callback: (Boolean) -> Unit
    ) {
        val selectorsJs = buildSelectorsList(selectors)
        val targetTextLower = targetText.lowercase()
        
        val checkScript = """
            (function() {
                var selectors = $selectorsJs;
                var targetText = '${targetTextLower.replace("'", "\\'")}';
                
                // Check each selector
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var el = document.querySelector(selectors[i]);
                        if (el) {
                            var text = (el.innerText || el.textContent || '').toLowerCase();
                            if (text.indexOf(targetText) !== -1) {
                                return 'found';
                            }
                        }
                    } catch(e) {}
                }
                return 'waiting';
            })();
        """.trimIndent()
        
        view.evaluateJavascript(checkScript) { result ->
            val status = result?.trim('"') ?: "waiting"
            
            if (status == "found") {
                logD("Button text found: $targetText")
                callback(true)
            } else if (retryCount < maxRetries) {
                logD("Waiting for button text ($retryCount/$maxRetries): $targetText")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    waitForButtonText(view, selectors, targetText, maxRetries, retryInterval, retryCount + 1, callback)
                }, retryInterval)
            } else {
                logE("Max retries reached waiting for button text: $targetText")
                callback(false)
            }
        }
    }
    
    /**
     * Handle the result of a click action
     */
    private fun handleClickResult(view: WebView, result: String?) {
        val status = result?.trim('"') ?: "notfound"
        logD("Click result: $status")
        
        if (status == "clicked") {
            currentStep++
            // Wait a bit for page to update after click, then process next step
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                webView?.url?.let { processCurrentStep(webView!!, it) }
            }, 500)
        } else {
            logE("Failed to click button at step $currentStep")
            // Move to next step anyway
            currentStep++
            webView?.url?.let { processCurrentStep(webView!!, it) }
        }
    }

    private fun buildSelectorsList(arr: JSONArray): String {
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add("'${arr.getString(i).replace("'", "\\'")}'")
        }
        return "[${list.joinToString(",")}]"
    }

    private fun buildPatternsList(arr: JSONArray): String {
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add("'${arr.getString(i).replace("'", "\\'")}'")
        }
        return "[${list.joinToString(",")}]"
    }

    /**
     * Click an element on the page (e.g., play button)
     */
    private fun clickElement(view: WebView, step: JSONObject) {
        val selectors = step.optJSONArray("selectors") ?: JSONArray()
        // Allow single selector string as fallback
        val singleSelector = step.optString("selector", "")
        if (singleSelector.isNotEmpty() && selectors.length() == 0) {
            selectors.put(singleSelector)
        }
        
        val selectorsJs = buildSelectorsList(selectors)
        
        logD("clickElement - trying selectors: $selectorsJs")
        
        val script = """
            (function() {
                var selectors = $selectorsJs;
                
                for (var s = 0; s < selectors.length; s++) {
                    try {
                        var el = document.querySelector(selectors[s]);
                        if (el) {
                            console.log('Clicking element: ' + selectors[s]);
                            el.click();
                            return 'clicked:' + selectors[s];
                        }
                    } catch(e) {}
                }
                
                // Fallback: try clicking the video element directly
                var video = document.querySelector('video');
                if (video) {
                    console.log('Clicking video element');
                    video.click();
                    return 'clicked:video';
                }
                
                return 'notfound';
            })();
        """.trimIndent()
        
        view.evaluateJavascript(script) { result ->
            val status = result?.trim('"') ?: "notfound"
            logD("clickElement result: $status")
            
            // Wait a bit for the click action to take effect (video to load)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                currentStep++
                webView?.url?.let { processCurrentStep(webView!!, it) }
            }, 1500)  // Wait 1.5 seconds after click
        }
    }

    private fun extractVideoUrlFromDom(view: WebView, step: JSONObject) {
        val script = """
            (function() {
                var video = document.querySelector('video');
                if (video && video.src) return video.src;
                var source = document.querySelector('video source');
                if (source && source.src) return source.src;
                return null;
            })();
        """.trimIndent()
        
        view.evaluateJavascript(script) { result ->
            val url = result?.trim('"')?.takeIf { it != "null" && it.startsWith("http") }
            if (url != null) {
                extractedLinks.add(ExtractedLink(url, "Direct Video", "SpeedoStream", "HD"))
                completionCallback?.invoke(extractedLinks)
            } else {
                currentStep++
                processCurrentStep(view, view.url ?: "")
            }
        }
    }

    private fun cleanupWebView() {
         webView?.stopLoading()
         webView?.destroy()
         webView = null
    }

    /**
     * Fetch the full HTML of a page using WebView (Bypasses Cloudflare)
     */
    suspend fun fetchHtml(url: String): String = withTimeout(DEFAULT_TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                try {
                    cleanupWebView() // Clear previous
                    
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Use Mobile UA for better Cloudflare pass rate
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                
                                // Wait 5 seconds for Cloudflare/Loading
                                mainHandler.postDelayed({
                                    evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                                        // The result is a JSON string (e.g. "<html>..."), need to unquote
                                        val finalHtml = try {
                                            if (html != null && html != "null") {
                                                if (html.startsWith("\"") && html.endsWith("\"")) {
                                                     JSONObject("{\"d\":$html}").getString("d")
                                                } else {
                                                    html
                                                }
                                            } else {
                                                ""
                                            }
                                        } catch (e: Exception) {
                                            html ?: ""
                                        }
                                        
                                        if (continuation.isActive) {
                                            continuation.resume(finalHtml)
                                        }
                                        cleanupWebView()
                                    }
                                }, 5000) 
                            }
                            
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                            }
                        }
                        loadUrl(url)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume("")
                    }
                    cleanupWebView()
                }
            }
            
            continuation.invokeOnCancellation {
                mainHandler.post { cleanupWebView() }
            }
        }
    }
}
