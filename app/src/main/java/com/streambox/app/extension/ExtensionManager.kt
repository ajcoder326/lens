package com.streambox.app.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.streambox.app.data.local.ExtensionDao
import com.streambox.app.extension.model.*
import com.streambox.app.runtime.ExtensionExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionDao: ExtensionDao,
    private val httpClient: OkHttpClient,
    private val dataStore: DataStore<Preferences>,
    private val executor: ExtensionExecutor
) {
    companion object {
        private val ACTIVE_EXTENSION_KEY = stringPreferencesKey("active_extension_id")
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val cacheDir = File(context.filesDir, "extensions").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO)
    
    val extensions: StateFlow<List<Extension>> = extensionDao.getAllExtensions()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _activeExtension = MutableStateFlow<Extension?>(null)
    val activeExtension: StateFlow<Extension?> = _activeExtension.asStateFlow()
    
    init {
        // Load active extension on startup
        scope.launch {
            dataStore.data.collect { prefs ->
                val activeId = prefs[ACTIVE_EXTENSION_KEY]
                if (activeId != null) {
                    _activeExtension.value = extensionDao.getById(activeId)
                } else {
                    // Set first enabled extension as active
                    extensions.first { it.isNotEmpty() }.let { list ->
                        if (_activeExtension.value == null) {
                            _activeExtension.value = list.firstOrNull { it.enabled }
                        }
                    }
                }
            }
        }
        
        // Check for updates on startup
        scope.launch {
            try {
                checkForUpdates()
            } catch (e: Exception) {
                android.util.Log.e("ExtensionManager", "Auto-update failed", e)
            }
        }
    }
    
    /**
     * Add extension from manifest URL
     */
    suspend fun addExtension(manifestUrl: String): Result<Extension> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ExtensionManager", "addExtension called with URL: $manifestUrl")
            // Fetch manifest
            val manifest = fetchManifest(manifestUrl)
            android.util.Log.d("ExtensionManager", "Fetched manifest: ${manifest.name}")
            
            // Generate ID
            val extensionId = generateId(manifest.name)
            
            // Download all modules
            val modules = downloadModules(manifestUrl, manifest.modules)
            
            // Save modules to cache
            saveModules(extensionId, modules)
            
            // Create extension entity
            val extension = Extension(
                id = extensionId,
                name = manifest.name,
                version = manifest.version,
                icon = manifest.icon,
                author = manifest.author,
                description = manifest.description,
                sourceUrl = manifestUrl,
                installedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                enabled = true
            )
            
            // Save to database
            extensionDao.insert(extension)
            
            // Set as active if first extension
            if (_activeExtension.value == null) {
                setActiveExtension(extensionId)
            }
            
            Result.success(extension)
        } catch (e: Exception) {
            android.util.Log.e("ExtensionManager", "addExtension FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun removeExtension(extensionId: String) = withContext(Dispatchers.IO) {
        extensionDao.deleteById(extensionId)
        clearModuleCache(extensionId)
        
        // Clear active if this was active
        if (_activeExtension.value?.id == extensionId) {
            _activeExtension.value = null
            dataStore.edit { it.remove(ACTIVE_EXTENSION_KEY) }
        }
    }
    
    suspend fun toggleExtension(extensionId: String) = withContext(Dispatchers.IO) {
        val extension = extensionDao.getById(extensionId) ?: return@withContext
        extensionDao.setEnabled(extensionId, !extension.enabled)
    }
    
    suspend fun setActiveExtension(extensionId: String) = withContext(Dispatchers.IO) {
        val extension = extensionDao.getById(extensionId) ?: return@withContext
        _activeExtension.value = extension
        dataStore.edit { prefs ->
            prefs[ACTIVE_EXTENSION_KEY] = extensionId
        }
    }
    
    suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
         try {
             val installedExtensions = extensionDao.getAllExtensions().first()
             installedExtensions.forEach { extension ->
                 if (extension.sourceUrl.isNotEmpty()) {
                     try {
                         val manifest = fetchManifest(extension.sourceUrl)
                         if (manifest.version != extension.version) {
                             android.util.Log.d("ExtensionManager", "Update found for ${extension.name}: ${extension.version} -> ${manifest.version}")
                             updateExtension(extension, manifest)
                         }
                     } catch (e: Exception) {
                         android.util.Log.e("ExtensionManager", "Failed to check update for ${extension.name}", e)
                     }
                 }
             }
         } catch (e: Exception) {
             android.util.Log.e("ExtensionManager", "checkForUpdates error", e)
         }
    }

    private suspend fun updateExtension(extension: Extension, manifest: ExtensionManifest) {
        val modules = downloadModules(extension.sourceUrl, manifest.modules)
        saveModules(extension.id, modules)
        
        val updatedExtension = extension.copy(
            version = manifest.version,
            icon = manifest.icon,
            author = manifest.author,
            description = manifest.description,
            updatedAt = System.currentTimeMillis()
        )
        
        extensionDao.update(updatedExtension)
        
        // If this was the active extension, update state
        if (_activeExtension.value?.id == extension.id) {
            _activeExtension.value = updatedExtension
        }
    }
    
    // ============ Extension Execution (delegates to ExtensionExecutor) ============
    
    suspend fun getCatalog(extensionId: String): List<CatalogItem> {
        return executor.getCatalog(extensionId)
    }
    
    suspend fun getGenres(extensionId: String): List<CatalogItem> {
        return executor.getGenres(extensionId)
    }
    
    suspend fun getPosts(extensionId: String, filter: String, page: Int): List<Post> {
        return executor.getPosts(extensionId, filter, page)
    }
    
    suspend fun searchPosts(extensionId: String, query: String, page: Int): List<Post> {
        return executor.searchPosts(extensionId, query, page)
    }
    
    suspend fun getMetadata(extensionId: String, link: String): ContentInfo? {
        return executor.getMetadata(extensionId, link)
    }
    
    suspend fun getStreams(extensionId: String, link: String, type: String): List<StreamSource> {
        return executor.getStreams(extensionId, link, type)
    }
    
    suspend fun getEpisodes(extensionId: String, link: String): List<Episode> {
        return executor.getEpisodes(extensionId, link)
    }
    
    // ============ Private Helpers ============
    
    private suspend fun fetchManifest(url: String): ExtensionManifest {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch manifest: ${response.code}")
        }
        
        val body = response.body?.string() ?: throw Exception("Empty manifest")
        return json.decodeFromString<ExtensionManifest>(body)
    }
    
    private suspend fun downloadModules(
        baseUrl: String,
        modules: ExtensionModules
    ): Map<String, String> {
        val baseUri = URI(baseUrl).resolve("./")
        
        return mapOf(
            "catalog" to downloadModule(baseUri, modules.catalog),
            "posts" to downloadModule(baseUri, modules.posts),
            "meta" to downloadModule(baseUri, modules.meta),
            "stream" to downloadModule(baseUri, modules.stream),
            "episodes" to (modules.episodes?.let { downloadModule(baseUri, it) } ?: "")
        ).filterValues { it.isNotEmpty() }
    }
    
    private suspend fun downloadModule(baseUri: URI, path: String): String {
        val url = if (path.startsWith("http")) path else baseUri.resolve(path).toString()
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to download module: ${response.code}")
        }
        
        return response.body?.string() ?: ""
    }
    
    private fun saveModules(extensionId: String, modules: Map<String, String>) {
        val extDir = File(cacheDir, extensionId).apply { mkdirs() }
        modules.forEach { (name, code) ->
            File(extDir, "$name.js").writeText(code)
        }
    }
    
    private fun clearModuleCache(extensionId: String) {
        File(cacheDir, extensionId).deleteRecursively()
    }
    
    private fun generateId(name: String): String {
        val sanitized = name.lowercase().replace(Regex("[^a-z0-9]"), "_")
        return "${sanitized}_${UUID.randomUUID().toString().take(8)}"
    }
}
