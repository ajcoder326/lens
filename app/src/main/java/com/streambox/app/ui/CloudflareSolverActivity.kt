package com.streambox.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.ComponentActivity
import com.streambox.app.utils.BrowserBus
import org.json.JSONObject

class CloudflareSolverActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var overlay: android.widget.FrameLayout
    private var requestId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isSolved = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestId = intent.getStringExtra("REQUEST_ID")
        val url = intent.getStringExtra("URL") ?: "about:blank"
        
        val root = android.widget.FrameLayout(this)
        
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    startChecking()
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(webView)
        
        // Overlay to hide WebView content when not needed (e.g. standard loading)
        overlay = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#121212")) // Dark background
            val progressBar = android.widget.ProgressBar(context).apply {
                isIndeterminate = true
            }
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            addView(progressBar, params)
            
            val text = android.widget.TextView(context).apply {
                text = "Bypassing Security..."
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
            }
            val textParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
                topMargin = 100 
            }
            addView(text, textParams)
        }
        root.addView(overlay)
        
        setContentView(root)
        
        webView.loadUrl(url)
    }

    private fun startChecking() {
        handler.removeCallbacksAndMessages(null)
        handler.post(checkRunnable)
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isDestroyed || isFinishing || isSolved) return
            
            val title = webView.title ?: ""
            
            // If Cloudflare/DDOS detected, HIDE OVERLAY so user can interact
            if (title.contains("Just a moment") || 
                title.contains("DDoS-Guard") || 
                title.contains("Security Check") ||
                title.contains("Attention Required")) {
                
                overlay.visibility = android.view.View.GONE
                // User must solve it. We keep checking until title changes.
                handler.postDelayed(this, 1000)
                return
            }
            
            // If title is valid and NOT Cloudflare, we assume success.
            if (title.isNotEmpty() && title != "about:blank") {
                isSolved = true
                webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                    val finalHtml = try {
                        if (html != null && html.length > 2 && html.startsWith("\"") && html.endsWith("\"")) {
                             JSONObject("{\"d\":$html}").getString("d")
                        } else {
                            html ?: ""
                        }
                    } catch (e: Exception) {
                        html ?: ""
                    }
                    
                    val cookies = CookieManager.getInstance().getCookie(webView.url) ?: ""
                    
                    val resultJson = JSONObject()
                    resultJson.put("html", finalHtml)
                    resultJson.put("cookies", cookies)
                    resultJson.put("currentUrl", webView.url)
                    
                    if (requestId != null) {
                        BrowserBus.requests[requestId]?.complete(resultJson.toString())
                    }
                    finish()
                }
            } else {
                // Still loading...
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
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
