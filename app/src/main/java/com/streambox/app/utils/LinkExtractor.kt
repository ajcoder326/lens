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
 * Used by Native Link Navigator to replace WebView navigation
 */
object LinkExtractor {

    /**
     * Extract links from HTML content
     * @param html The HTML content to parse
     * @param baseUrl The base URL for resolving relative links
     * @return List of extracted links, ordered by relevance
     */
    fun extractLinks(html: String, baseUrl: String): List<ExtractedLink> {
        val links = mutableListOf<ExtractedLink>()
        
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            
            // 1. Look for quality selection links (highest priority)
            val qualityLinks = doc.select("a[href*='_x'], a[href*='_l'], a[href*='_h'], a[href*='quality']")
            qualityLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().ifEmpty { extractQualityFromUrl(href) }
                if (href.isNotEmpty() && !isAdLink(href)) {
                    links.add(ExtractedLink(text, href, LinkType.QUALITY))
                }
            }
            
            // 2. Look for download buttons
            val downloadLinks = doc.select("a:contains(Download), a:contains(download), input[name='imhuman'], button:contains(Download)")
            downloadLinks.forEach { element ->
                val href = if (element.tagName() == "a") element.absUrl("href") else ""
                val text = element.text().ifEmpty { "Download" }
                if (href.isNotEmpty() && !isAdLink(href)) {
                    links.add(ExtractedLink(text, href, LinkType.DOWNLOAD))
                }
            }
            
            // 3. Look for direct file links (mp4, mkv, m3u8)
            val directLinks = doc.select("a[href*='.mp4'], a[href*='.mkv'], a[href*='.m3u8'], a[href*='ydc1wes']")
            directLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().ifEmpty { "Direct Link" }
                if (href.isNotEmpty() && !isAdLink(href)) {
                    val type = if (isVideoUrl(href)) LinkType.VIDEO else LinkType.DIRECT_LINK
                    links.add(ExtractedLink(text, href, type))
                }
            }
            
            // 4. Look for form submit buttons (for imhuman verification)
            val forms = doc.select("form")
            forms.forEach { form ->
                val action = form.absUrl("action")
                val submitBtn = form.selectFirst("input[type='submit'], button[type='submit']")
                if (action.isNotEmpty() && submitBtn != null) {
                    val text = submitBtn.attr("value").ifEmpty { submitBtn.text().ifEmpty { "Continue" } }
                    links.add(ExtractedLink(text, action, LinkType.NAVIGATION))
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
    fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mkv")) &&
               !lower.contains("thumb") && !lower.contains("preview") && !lower.contains("poster")
    }

    /**
     * Extract quality info from URL
     */
    private fun extractQualityFromUrl(url: String): String {
        return when {
            url.contains("_x") || url.contains("uhd") || url.contains("1080") -> "UHD (1080p)"
            url.contains("_h") || url.contains("720") -> "HD (720p)"
            url.contains("_l") || url.contains("480") -> "SD (480p)"
            else -> "Quality Option"
        }
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
