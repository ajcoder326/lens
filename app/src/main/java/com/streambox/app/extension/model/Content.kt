package com.streambox.app.extension.model

import kotlinx.serialization.Serializable

/**
 * A post/item in a content catalog (movie, show, etc.)
 */
@Serializable
data class Post(
    val title: String,
    val link: String,
    val image: String,
    val provider: String? = null
)

/**
 * Catalog category definition
 */
@Serializable
data class CatalogItem(
    val title: String,
    val filter: String
)

/**
 * Content metadata/info
 */
@Serializable
data class ContentInfo(
    val title: String,
    val image: String,
    val synopsis: String = "",
    val imdbId: String? = null,
    val type: String = "movie", // "movie" or "series"
    val tags: List<String>? = null,
    val cast: List<String>? = null,
    val rating: String? = null,
    val year: String? = null,
    val linkList: List<ContentLink> = emptyList(),
    // Additional metadata for hero display
    val logo: String? = null,
    val background: String? = null,
    val poster: String? = null
)

/**
 * Link to content (season, quality variant, etc.)
 */
@Serializable
data class ContentLink(
    val title: String,
    val quality: String? = null,
    val episodesLink: String? = null,
    val directLinks: List<DirectLink>? = null
)

/**
 * Direct link to episode or playable content
 */
@Serializable
data class DirectLink(
    val title: String,
    val link: String,
    val type: String? = null // "movie" or "series"
)

/**
 * Episode information
 */
@Serializable
data class Episode(
    val title: String,
    val link: String
)

/**
 * Stream source for playback
 */
@Serializable
data class StreamSource(
    val server: String,
    val link: String,
    val type: String, // "m3u8", "mp4", "mkv", "automate", "direct"
    val quality: String? = null,
    val subtitles: List<Subtitle>? = null,
    val headers: Map<String, String>? = null,
    val automation: String? = null  // JSON string of automation rules for hidden browser
)

/**
 * Subtitle track
 */
@Serializable
data class Subtitle(
    val title: String,
    val language: String,
    val type: String, // MIME type
    val uri: String
)
