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
            
            // SpeedoStream specific: Quality links in #container table
            val containerTableLinks = doc.select("#container table a, div#container table a")
            containerTableLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().trim()
                if (href.isNotEmpty() && !isAdLink(href) && text.isNotEmpty()) {
                    val type = if (text.lowercase().contains("quality") || href.contains("_x") || href.contains("_h") || href.contains("_l")) {
                        LinkType.QUALITY
                    } else LinkType.NAVIGATION
                    links.add(ExtractedLink(text, href, type))
                }
            }
            
            // SpeedoStream specific: Direct download link in #container span
            val directSpanLinks = doc.select("#container span a, div#container span a")
            directSpanLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().trim()
                if (href.isNotEmpty() && !isAdLink(href)) {
                    val type = if (isVideoUrl(href)) LinkType.VIDEO else LinkType.DIRECT_LINK
                    links.add(ExtractedLink(text.ifEmpty { "Direct Link" }, href, type))
                }
            }
            
            // SpeedoStream specific: Form with submit button (Download File button)
            val forms = doc.select("form")
            forms.forEach { form ->
                val action = form.absUrl("action").ifEmpty { baseUrl }
                val submitBtn = form.selectFirst("button[type='submit'], input[type='submit']")
                if (submitBtn != null) {
                    val text = submitBtn.text().trim().ifEmpty { 
                        submitBtn.attr("value").ifEmpty { "Download" }
                    }
                    // For form submissions, we mark this specially - the action URL is where to POST
                    if (text.lowercase().contains("download") || text.lowercase().contains("file")) {
                        links.add(ExtractedLink(text, action, LinkType.DOWNLOAD))
                    }
                }
            }
            
            // General: Quality selection links (fallback)
            val qualityLinks = doc.select("a[href*='_x'], a[href*='_l'], a[href*='_h'], a[href*='quality']")
            qualityLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().ifEmpty { extractQualityFromUrl(href) }
                if (href.isNotEmpty() && !isAdLink(href) && links.none { it.url == href }) {
                    links.add(ExtractedLink(text, href, LinkType.QUALITY))
                }
            }
            
            // General: Direct file links (mp4, mkv, m3u8)
            val directLinks = doc.select("a[href*='.mp4'], a[href*='.mkv'], a[href*='.m3u8'], a[href*='ydc1wes']")
            directLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().ifEmpty { "Direct Link" }
                if (href.isNotEmpty() && !isAdLink(href) && links.none { it.url == href }) {
                    val type = if (isVideoUrl(href)) LinkType.VIDEO else LinkType.DIRECT_LINK
                    links.add(ExtractedLink(text, href, type))
                }
            }
            
            // General: Any link with "download" text
            val downloadLinks = doc.select("a:contains(Download), a:contains(download)")
            downloadLinks.forEach { element ->
                val href = element.absUrl("href")
                val text = element.text().trim()
                if (href.isNotEmpty() && !isAdLink(href) && links.none { it.url == href }) {
                    links.add(ExtractedLink(text.ifEmpty { "Download" }, href, LinkType.DOWNLOAD))
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
