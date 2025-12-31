package com.streambox.app.runtime

import android.content.Context
import android.util.Log
import com.streambox.app.extension.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes JavaScript extension modules
 * Provides typed interface for calling extension functions
 */
@Singleton
class ExtensionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsRuntime: JSRuntime
) {
    companion object {
        private const val TAG = "ExtensionExecutor"
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    private val cacheDir = File(context.filesDir, "extensions")
    
    /**
     * Get catalog items from catalog.js module
     */
    suspend fun getCatalog(extensionId: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "catalog")
        if (moduleCode == null) {
            Log.e(TAG, "catalog.js not found for extension: $extensionId")
            return@withContext emptyList()
        }
        
        Log.d(TAG, "Loaded catalog.js for $extensionId, length: ${moduleCode.length}")
        
        jsRuntime.evaluateAndGetVariable(
            moduleCode = moduleCode,
            variableName = "catalog"
        ) { result ->
            Log.d(TAG, "Catalog result: $result")
            parseList<CatalogItem>(result)
        }.getOrElse { 
            Log.e(TAG, "Failed to get catalog", it)
            emptyList() 
        }
    }
    
    /**
     * Get genres from catalog.js module
     */
    suspend fun getGenres(extensionId: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "catalog") ?: return@withContext emptyList()
        
        jsRuntime.evaluateAndGetVariable(
            moduleCode = moduleCode,
            variableName = "genres"
        ) { result ->
            parseList<CatalogItem>(result)
        }.getOrElse { emptyList() }
    }
    
    /**
     * Get posts from posts.js module
     */
    suspend fun getPosts(extensionId: String, filter: String, page: Int): List<Post> = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "posts")
        if (moduleCode == null) {
            Log.e(TAG, "posts.js not found for extension: $extensionId")
            return@withContext emptyList()
        }
        
        Log.d(TAG, "Calling getPosts(filter='$filter', page=$page) for extension: $extensionId")
        
        val wrappedCode = buildPostsModuleCode(moduleCode)
        
        jsRuntime.callFunction(
            moduleCode = wrappedCode,
            functionName = "getPosts",
            args = listOf(filter, page, null)
        ) { result ->
            Log.d(TAG, "getPosts result type: ${result?.javaClass?.simpleName}")
            val posts = parseList<Post>(result)
            Log.d(TAG, "Parsed ${posts.size} posts")
            posts
        }.getOrElse { 
            Log.e(TAG, "Failed to get posts", it)
            emptyList() 
        }
    }
    
    /**
     * Search posts using posts.js module
     */
    suspend fun searchPosts(extensionId: String, query: String, page: Int): List<Post> = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "posts") ?: return@withContext emptyList()
        
        val wrappedCode = buildPostsModuleCode(moduleCode)
        
        jsRuntime.callFunction(
            moduleCode = wrappedCode,
            functionName = "getSearchPosts",
            args = listOf(query, page, null)
        ) { result ->
            parseList<Post>(result)
        }.getOrElse { 
            Log.e(TAG, "Failed to search posts", it)
            emptyList() 
        }
    }
    
    /**
     * Get metadata from meta.js module
     */
    suspend fun getMetadata(extensionId: String, link: String): ContentInfo? = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "meta") ?: return@withContext null
        
        val wrappedCode = """
            $moduleCode
            
            // Ensure getMetaData is available
            if (typeof getMetaData === 'undefined' && typeof module !== 'undefined' && module.exports) {
                var getMetaData = module.exports.getMetaData || module.exports;
            }
        """.trimIndent()
        
        jsRuntime.callFunction(
            moduleCode = wrappedCode,
            functionName = "getMetaData",
            args = listOf(link, null)
        ) { result ->
            parseObject<ContentInfo>(result)
        }.getOrElse { 
            Log.e(TAG, "Failed to get metadata", it)
            null 
        }
    }
    
    /**
     * Get streams from stream.js module
     */
    suspend fun getStreams(extensionId: String, link: String, type: String): List<StreamSource> = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "stream") ?: run {
            Log.e(TAG, "stream.js module not found for extension: $extensionId")
            return@withContext emptyList()
        }
        
        Log.d(TAG, "Executing stream.js getStreams with link: $link")
        
        val wrappedCode = """
            $moduleCode
            
            // Support both getStreams and getStream function names
            if (typeof getStreams === 'undefined' && typeof getStream !== 'undefined') {
                var getStreams = getStream;
            }
            if (typeof getStreams === 'undefined' && typeof module !== 'undefined' && module.exports) {
                var getStreams = module.exports.getStreams || module.exports.getStream || module.exports;
            }
        """.trimIndent()
        
        jsRuntime.callFunction(
            moduleCode = wrappedCode,
            functionName = "getStreams",  // Use getStreams (plural)
            args = listOf(link, type, null)
        ) { result ->
            Log.d(TAG, "stream.js returned: ${result?.toString()?.take(200)}")
            parseList<StreamSource>(result)
        }.getOrElse { 
            Log.e(TAG, "Failed to get streams", it)
            emptyList() 
        }
    }
    
    /**
     * Get episodes from episodes.js module
     */
    suspend fun getEpisodes(extensionId: String, link: String): List<Episode> = withContext(Dispatchers.IO) {
        val moduleCode = loadModule(extensionId, "episodes") ?: return@withContext emptyList()
        
        val wrappedCode = """
            $moduleCode
            
            if (typeof getEpisodes === 'undefined' && typeof module !== 'undefined' && module.exports) {
                var getEpisodes = module.exports.getEpisodes || module.exports;
            }
        """.trimIndent()
        
        jsRuntime.callFunction(
            moduleCode = wrappedCode,
            functionName = "getEpisodes",
            args = listOf(link, null)
        ) { result ->
            parseList<Episode>(result)
        }.getOrElse { 
            Log.e(TAG, "Failed to get episodes", it)
            emptyList() 
        }
    }
    
    // ============ Helper Methods ============
    
    private fun loadModule(extensionId: String, moduleName: String): String? {
        val moduleFile = File(cacheDir, "$extensionId/$moduleName.js")
        Log.d(TAG, "Loading module: ${moduleFile.absolutePath}")
        Log.d(TAG, "Module exists: ${moduleFile.exists()}")
        
        return if (moduleFile.exists()) {
            val content = moduleFile.readText()
            Log.d(TAG, "Module loaded successfully, size: ${content.length} bytes")
            content
        } else {
            Log.e(TAG, "Module not found: ${moduleFile.absolutePath}")
            // List available extensions for debugging
            val extDirs = cacheDir.listFiles()
            Log.d(TAG, "Available extensions in cache: ${extDirs?.map { it.name }}")
            if (cacheDir.exists()) {
                val extDir = File(cacheDir, extensionId)
                if (extDir.exists()) {
                    Log.d(TAG, "Files in extension dir: ${extDir.listFiles()?.map { it.name }}")
                }
            }
            null
        }
    }
    
    private fun buildPostsModuleCode(moduleCode: String): String {
        return """
            $moduleCode
            
            // Ensure getPosts and getSearchPosts are available
            if (typeof getPosts === 'undefined' && typeof module !== 'undefined' && module.exports) {
                var getPosts = module.exports.getPosts;
            }
            if (typeof getSearchPosts === 'undefined' && typeof module !== 'undefined' && module.exports) {
                var getSearchPosts = module.exports.getSearchPosts;
            }
        """.trimIndent()
    }
    
    private fun createProviderContext(): Map<String, Any> {
        // This creates a minimal context object
        // The actual axios/cheerio are injected into the JS scope by JSRuntime
        return mapOf(
            "baseUrl" to "",
            "headers" to mapOf<String, String>()
        )
    }
    
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> parseList(result: Any?): List<T> {
        if (result == null) return emptyList()
        
        return when (result) {
            is List<*> -> result.mapNotNull { item ->
                try {
                    when (item) {
                        is Map<*, *> -> parseMapToObject<T>(item as Map<String, Any?>)
                        else -> null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse list item", e)
                    null
                }
            }
            else -> emptyList()
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> parseObject(result: Any?): T? {
        if (result == null) return null
        
        return try {
            when (result) {
                is Map<*, *> -> parseMapToObject<T>(result as Map<String, Any?>)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse object", e)
            null
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> parseMapToObject(map: Map<String, Any?>): T? {
        Log.d(TAG, "parseMapToObject called for ${T::class.simpleName} with map keys: ${map.keys}")
        // Use direct manual parsing - it's more reliable than JSON serialization
        return createDefaultObject<T>(map)
    }
    
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> createDefaultObject(map: Map<String, Any?>): T? {
        // Manual parsing for known types
        return when (T::class) {
            Post::class -> Post(
                title = map["title"]?.toString() ?: "",
                link = map["link"]?.toString() ?: "",
                image = map["image"]?.toString() ?: ""
            ) as T
            CatalogItem::class -> CatalogItem(
                title = map["title"]?.toString() ?: "",
                filter = map["filter"]?.toString() ?: ""
            ) as T
            ContentInfo::class -> run {
                val title = map["title"]?.toString() ?: ""
                val image = map["image"]?.toString() ?: ""
                val synopsis = map["synopsis"]?.toString() ?: ""
                Log.d(TAG, "Creating ContentInfo - title: '${title.take(30)}', image: '${image.take(50)}', synopsis length: ${synopsis.length}")
                
                // Parse linkList
                val linkList = (map["linkList"] as? List<*>)?.mapNotNull { item ->
                    val linkMap = item as? Map<*, *> ?: return@mapNotNull null
                    val directLinks = (linkMap["directLinks"] as? List<*>)?.mapNotNull { dl ->
                        val dlMap = dl as? Map<*, *> ?: return@mapNotNull null
                        DirectLink(
                            title = dlMap["title"]?.toString() ?: "",
                            link = dlMap["link"]?.toString() ?: "",
                            type = dlMap["type"]?.toString()
                        )
                    }
                    ContentLink(
                        title = linkMap["title"]?.toString() ?: "",
                        quality = linkMap["quality"]?.toString(),
                        episodesLink = linkMap["episodesLink"]?.toString() ?: linkMap["link"]?.toString(),
                        directLinks = directLinks
                    )
                } ?: emptyList()
                Log.d(TAG, "Parsed linkList with ${linkList.size} items")
                
                ContentInfo(
                    title = title,
                    image = image,
                    synopsis = synopsis,
                    type = map["type"]?.toString() ?: "movie",
                    rating = map["rating"]?.toString(),
                    year = map["year"]?.toString(),
                    tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() },
                    linkList = linkList
                ) as T
            }
            StreamSource::class -> {
                // Parse automation rules if present
                val automationObj = map["automation"]
                val automationJson = when (automationObj) {
                    is Map<*, *> -> {
                        try {
                            org.json.JSONObject(automationObj as Map<*, *>).toString()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert Map to JSON", e)
                            null
                        }
                    }
                    is org.mozilla.javascript.NativeObject -> {
                        try {
                            // Convert Rhino NativeObject to JSON
                            convertNativeObjectToJson(automationObj).toString()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert NativeObject to JSON", e)
                            null
                        }
                    }
                    is String -> automationObj
                    else -> {
                        Log.d(TAG, "Automation type: ${automationObj?.javaClass?.name}")
                        null
                    }
                }
                
                Log.d(TAG, "StreamSource automation: ${automationJson?.take(100)}")
                
                StreamSource(
                    server = map["server"]?.toString() ?: "",
                    link = map["link"]?.toString() ?: "",
                    type = map["type"]?.toString() ?: "m3u8",
                    quality = map["quality"]?.toString(),
                    automation = automationJson
                ) as T
            }
            Episode::class -> Episode(
                title = map["title"]?.toString() ?: "",
                link = map["link"]?.toString() ?: ""
            ) as T
            else -> null
        }
    }
    
    /**
     * Convert Rhino NativeObject to org.json.JSONObject recursively
     */
    private fun convertNativeObjectToJson(obj: org.mozilla.javascript.NativeObject): org.json.JSONObject {
        val json = org.json.JSONObject()
        for (id in obj.ids) {
            val key = id.toString()
            val value = obj.get(key, obj)
            json.put(key, convertToJsonValue(value))
        }
        return json
    }
    
    /**
     * Convert any Rhino value to JSON-compatible value
     */
    private fun convertToJsonValue(value: Any?): Any? {
        return when (value) {
            null, org.mozilla.javascript.Undefined.instance -> org.json.JSONObject.NULL
            is org.mozilla.javascript.NativeObject -> convertNativeObjectToJson(value)
            is org.mozilla.javascript.NativeArray -> {
                val arr = org.json.JSONArray()
                for (i in 0 until value.length.toInt()) {
                    arr.put(convertToJsonValue(value.get(i, value)))
                }
                arr
            }
            is Number -> value
            is Boolean -> value
            is String -> value
            else -> value.toString()
        }
    }
}
