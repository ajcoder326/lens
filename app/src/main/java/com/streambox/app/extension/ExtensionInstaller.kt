package com.streambox.app.extension

import android.content.Context
import android.util.Log
import com.streambox.app.data.local.ExtensionDao
import com.streambox.app.extension.model.Extension
import com.streambox.app.extension.model.ExtensionManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs bundled extensions from assets folder on first run
 */
@Singleton
class ExtensionInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionDao: ExtensionDao
) {
    companion object {
        private const val TAG = "ExtensionInstaller"
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val extensionsDir = File(context.filesDir, "extensions")
    
    /**
     * Install all bundled extensions from assets
     * Called on app startup
     */
    suspend fun installBundledExtensions() = withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            val bundledExtensions = assetManager.list("extensions") ?: return@withContext
            
            Log.d(TAG, "Found ${bundledExtensions.size} bundled extensions: ${bundledExtensions.toList()}")
            
            for (extName in bundledExtensions) {
                try {
                    installExtensionFromAssets(extName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install bundled extension: $extName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list bundled extensions", e)
        }
    }
    
    private suspend fun installExtensionFromAssets(extName: String) {
        val assetPath = "extensions/$extName"
        val assetManager = context.assets
        
        // Read manifest
        val manifestContent = try {
            assetManager.open("$assetPath/manifest.json").bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "No manifest.json found for $extName")
            return
        }
        
        val manifest = try {
            json.decodeFromString<ExtensionManifest>(manifestContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse manifest for $extName", e)
            return
        }
        
        // Generate a stable ID based on name
        val extensionId = extName.lowercase().replace(Regex("[^a-z0-9]"), "_")
        
        // Check if already installed
        val existing = extensionDao.getById(extensionId)
        if (existing != null) {
            Log.d(TAG, "Extension $extName already installed, updating files...")
        }
        
        // Create extension directory
        val extDir = File(extensionsDir, extensionId)
        extDir.mkdirs()
        
        // Copy all JS files from assets
        val files = assetManager.list(assetPath) ?: emptyArray()
        Log.d(TAG, "Copying ${files.size} files for $extName: ${files.toList()}")
        
        for (fileName in files) {
            if (fileName.endsWith(".js")) {
                try {
                    val content = assetManager.open("$assetPath/$fileName").bufferedReader().readText()
                    File(extDir, fileName).writeText(content)
                    Log.d(TAG, "Copied $fileName (${content.length} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy $fileName", e)
                }
            }
        }
        
        // Create or update extension in database
        val extension = Extension(
            id = extensionId,
            name = manifest.name,
            version = manifest.version,
            icon = manifest.icon,
            author = manifest.author,
            description = manifest.description,
            sourceUrl = "bundled://$extName",
            installedAt = existing?.installedAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            enabled = true
        )
        
        extensionDao.insert(extension)
        Log.d(TAG, "Installed bundled extension: ${manifest.name} (id=$extensionId)")
    }
}
