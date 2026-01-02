package com.streambox.app.utils

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    private val blockedDomains = java.util.HashSet<String>()
    private var isInitialized = false
    
    // Critical fallback domains in case file loading fails
    private val FALLBACK_DOMAINS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "facebook.com", "analytics.google.com", "popads.net", "propellerads.com"
    )

    private val AD_KEYWORDS = listOf(
        "/ads/", "/ad/", "/banner/", "/pop/", "/pixel", 
        "tracker", "analytics", "pagead", "doubleclick"
    )

    fun init(context: android.content.Context) {
        if (isInitialized) return
        
        try {
            // Load from assets
            context.assets.open("ad_hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val domain = line.trim()
                    if (domain.isNotEmpty() && !domain.startsWith("#")) {
                        blockedDomains.add(domain.lowercase())
                    }
                }
            }
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to basic list
            blockedDomains.addAll(FALLBACK_DOMAINS)
        }
    }

    fun isAd(url: String): Boolean {
        try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            
            // 1. Check strict domain match (fastest)
            if (blockedDomains.contains(host)) return true
            
            // 2. Check subdomains (e.g. ads.google.com -> check google.com)
            var currentHost = host
            while (currentHost.contains(".")) {
                val nextDot = currentHost.indexOf(".")
                if (nextDot == -1) break
                currentHost = currentHost.substring(nextDot + 1)
                if (blockedDomains.contains(currentHost)) return true
            }
            
            // 3. Check keywords in path (slower but catches non-domain based ads)
            val path = uri.path?.lowercase() ?: ""
            if (AD_KEYWORDS.any { path.contains(it) }) return true
            
        } catch (e: Exception) {
            // Invalid URL, ignore
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }
}
