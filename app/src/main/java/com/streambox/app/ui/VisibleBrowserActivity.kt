package com.streambox.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.streambox.app.utils.BrowserBus
import org.json.JSONObject

class VisibleBrowserActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var requestId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isSolved = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestId = intent.getStringExtra("REQUEST_ID")
        val url = intent.getStringExtra("URL") ?: "about:blank"
        val userAgent = intent.getStringExtra("USER_AGENT")
        
        val root = android.widget.FrameLayout(this)
        
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false // Block automated popups
            settings.setSupportMultipleWindows(true) // Required to intercept popups via onCreateWindow
            
            // Viewport settings for proper mobile display
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            
            // Zoom controls
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            
            if (userAgent != null) {
                settings.userAgentString = userAgent
            } else {
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            }
            
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    
                    // Ad Block Check
                    if (com.streambox.app.utils.AdBlocker.isAd(reqUrl)) {
                         return com.streambox.app.utils.AdBlocker.createEmptyResponse()
                    }
                    
                    checkUrlForVideo(reqUrl)
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    // Block all popups
                    return false
                }
            }
        }
        root.addView(webView)
        
        // Add a "Close" button
        val closeBtn = android.widget.Button(this).apply {
            text = "X"
            setBackgroundColor(android.graphics.Color.RED)
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { finish() }
        }
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = 50
            rightMargin = 50
        }
        root.addView(closeBtn, params)
        
        setContentView(root)
        
        Toast.makeText(this, "Browser Mode: Play video to capture", Toast.LENGTH_LONG).show()
        
        webView.loadUrl(url)
    }

    private fun checkUrlForVideo(url: String) {
        if (isSolved) return
        
        val urlLower = url.lowercase()
        
        // CAPTURE video URLs - m3u8 for streaming, mp4 for direct downloads
        val isM3u8 = (urlLower.contains(".m3u8") || urlLower.contains("/hls")) && 
            !urlLower.contains("beacon") && !urlLower.contains("analytics")
        
        // Capture direct MP4/MKV download URLs
        val isMp4 = (urlLower.contains(".mp4") || urlLower.contains(".mkv")) &&
            (urlLower.contains("ydc1wes.me") || urlLower.contains("/v/") || urlLower.contains("/d/") || urlLower.contains("storage")) &&
            !urlLower.contains("thumb") && !urlLower.contains("preview")
            
        if (isM3u8 || isMp4) {
            isSolved = true
            
            val videoType = if (isM3u8) "m3u8" else "mp4"
            val quality = if (urlLower.contains("uhd") || urlLower.contains("1080")) "UHD" else "HD"
            
            handler.post {
                val resultJson = JSONObject()
                resultJson.put("url", url)
                resultJson.put("type", videoType)
                resultJson.put("quality", quality)
                resultJson.put("headers", JSONObject().apply {
                    put("Referer", webView.url ?: url)
                    put("User-Agent", webView.settings.userAgentString)
                })
                
                Toast.makeText(this, "Video Captured!", Toast.LENGTH_SHORT).show()
                if (requestId != null) {
                    BrowserBus.requests[requestId]?.complete(resultJson.toString())
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        
        // If not solved, return empty
        if (!isSolved && requestId != null) {
             val deferred = BrowserBus.requests[requestId]
             if (deferred != null && !deferred.isCompleted) {
                 deferred.complete("")
             }
        }
    }
}
