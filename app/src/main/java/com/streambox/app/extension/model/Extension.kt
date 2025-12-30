package com.streambox.app.extension.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents an installed extension in the app
 */
@Entity(tableName = "extensions")
data class Extension(
    @PrimaryKey
    val id: String,
    val name: String,
    val version: String,
    val icon: String?,
    val author: String?,
    val description: String?,
    val sourceUrl: String,
    val installedAt: Long,
    val updatedAt: Long,
    val enabled: Boolean = true
)

/**
 * Extension manifest format (fetched from remote URL)
 * This is what users provide via URL
 */
@Serializable
data class ExtensionManifest(
    val name: String,
    val version: String,
    val icon: String? = null,
    val author: String? = null,
    val description: String? = null,
    val modules: ExtensionModules
)

/**
 * JavaScript module paths within an extension
 */
@Serializable
data class ExtensionModules(
    val catalog: String,    // catalog.js - defines categories
    val posts: String,      // posts.js - get post listings
    val meta: String,       // meta.js - get content details
    val stream: String,     // stream.js - get stream URLs
    val episodes: String? = null  // episodes.js - optional for series
)

/**
 * Cached module code for an extension
 */
data class CachedModules(
    val extensionId: String,
    val catalog: String,
    val posts: String,
    val meta: String,
    val stream: String,
    val episodes: String?
)
