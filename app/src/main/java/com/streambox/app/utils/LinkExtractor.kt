package com.streambox.app.utils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Represents an extracted link from a page
 */
data class ExtractedLink(
    val text: String,
    val url: String,
    val type: LinkType
)

enum class LinkType {
    QUALITY,      // Quality selection (e.g., "1080p", "720p", "UHD")
    DOWNLOAD,     // Download button
    DIRECT_LINK,  // Direct file link
    NAVIGATION,   // General navigation link
    VIDEO         // Video URL (m3u8, mp4)
}

/**
 * Utility to extract actionable links from HTML pages
 * Supports extension-provided selectors via NavigationRules
 */
object LinkExtractor {

    /**
     * Navigation rules provided by extension
     */
    data class NavigationRules(
        val selectors: List<SelectorRule> = emptyList(),
        val videoPattern: String = ""
    )
    
    data class SelectorRule(
        val type: String,      // "quality", "download", "direct", "video"
        val selector: String,  // CSS selector
        val pattern: String    // Regex pattern for text/href matching
    )

    /**
     * Parse navigation rules from JSON string provided by extension
     */
    fun parseNavigationRules(json: String?): NavigationRules {
        if (json.isNullOrEmpty()) return NavigationRules()
        
        try {
            val obj = org.json.JSONObject(json)
            val selectors = mutableListOf<SelectorRule>()
            
            val selectorsArray = obj.optJSONArray("selectors")
            if (selectorsArray != null) {
                for (i in 0 until selectorsArray.length()) {
                    val s = selectorsArray.getJSONObject(i)
                    selectors.add(SelectorRule(
                        type = s.optString("type", "navigation"),
                        selector = s.optString("selector", ""),
                        pattern = s.optString("pattern", "")
                    ))
                }
            }
            
            return NavigationRules(
                selectors = selectors,
                videoPattern = obj.optString("videoPattern", "")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return NavigationRules()
        }
    }

    /**
     * Extract links from HTML content using extension-provided rules
     */
    fun extractLinks(html: String, baseUrl: String, rules: NavigationRules = NavigationRules()): List<ExtractedLink> {
        val links = mutableListOf<ExtractedLink>()
        
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            
            // Use extension-provided rules if available
            if (rules.selectors.isNotEmpty()) {
                for (rule in rules.selectors) {
                    val elements = doc.select(rule.selector)
                    elements.forEach { element ->
                        val href = element.absUrl("href")
                        val text = element.text().trim()
                        
                        // Check if element matches pattern (if pattern provided)
                        val matchesPattern = rule.pattern.isEmpty() || 
                            (text + href).lowercase().contains(Regex(rule.pattern.lowercase()))
                        
                        if (href.isNotEmpty() && !isAdLink(href) && matchesPattern) {
                            val linkType = when (rule.type) {
                                "quality" -> LinkType.QUALITY
                                "download" -> LinkType.DOWNLOAD
                                "direct" -> LinkType.DIRECT_LINK
                                "video" -> LinkType.VIDEO
                                else -> LinkType.NAVIGATION
                            }
                            
                            // Auto-detect video URLs
                            val finalType = if (isVideoUrl(href, rules.videoPattern)) LinkType.VIDEO else linkType
                            links.add(ExtractedLink(text.ifEmpty { rule.type.replaceFirstChar { it.uppercase() } }, href, finalType))
                        }
                    }
                }
            }
            
            // Fallback: Use generic selectors if no rules provided or no links found
            if (links.isEmpty()) {
                // SpeedoStream fallback selectors
                val fallbackSelectors = listOf(
                    "#container table a" to LinkType.QUALITY,
                    "#container span a" to LinkType.DIRECT_LINK,
                    "a[href*='.mp4'], a[href*='.mkv'], a[href*='.m3u8']" to LinkType.VIDEO
                )
                
                for ((selector, type) in fallbackSelectors) {
                    doc.select(selector).forEach { element ->
                        val href = element.absUrl("href")
                        val text = element.text().trim()
                        if (href.isNotEmpty() && !isAdLink(href) && links.none { it.url == href }) {
                            val finalType = if (isVideoUrl(href, rules.videoPattern)) LinkType.VIDEO else type
                            links.add(ExtractedLink(text.ifEmpty { "Link" }, href, finalType))
                        }
                    }
                }
                
                // Check for form submit buttons
                doc.select("form").forEach { form ->
                    val action = form.absUrl("action").ifEmpty { baseUrl }
                    val submitBtn = form.selectFirst("button[type='submit'], input[type='submit']")
                    if (submitBtn != null) {
                        val text = submitBtn.text().trim().ifEmpty { submitBtn.attr("value").ifEmpty { "Submit" } }
                        if (text.lowercase().contains("download") || text.lowercase().contains("file")) {
                            links.add(ExtractedLink(text, action, LinkType.DOWNLOAD))
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return links.distinctBy { it.url }
    }

    /**
     * Check if a URL is a video file URL
     */
    fun isVideoUrl(url: String, customPattern: String = ""): Boolean {
        val lower = url.lowercase()
        val defaultPattern = "\\.m3u8|\\.mp4|\\.mkv"
        val pattern = if (customPattern.isNotEmpty()) "$defaultPattern|$customPattern" else defaultPattern
        
        return lower.contains(Regex(pattern)) &&
               !lower.contains("thumb") && !lower.contains("preview") && !lower.contains("poster")
    }

    /**
     * Check if a URL is an ad/tracking link
     */
    private fun isAdLink(url: String): Boolean {
        val lower = url.lowercase()
        val adPatterns = listOf(
            "popads", "popcash", "bet365", "1xbet", "4rabet", "casino", "bitcoin",
            "doubleclick", "googlesyndication", "facebook.com/tr", "analytics"
        )
        return adPatterns.any { lower.contains(it) }
    }
}
