package com.streambox.app.runtime

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.streambox.app.player.HiddenBrowserExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JavaScript APIs exposed to extensions
 * Provides axios-like HTTP, cheerio-like DOM parsing, crypto, storage, and WebView
 */
@Singleton
class JSApis @Inject constructor(
    private val httpClient: OkHttpClient,
    @ApplicationContext private val context: android.content.Context
) {
    companion object {
        private const val TAG = "JSApis"
        private const val PREFS_NAME = "extension_prefs"
    }
    
    // Cookie Store: Map<Domain, CookieString>
    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    // Lazy initialization of SharedPreferences
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    
    // ============ Storage APIs ============
    
    fun saveData(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        Log.d(TAG, "Saved data for key: $key")
    }
    
    fun loadData(key: String): String {
        return prefs.getString(key, "") ?: ""
    }
    
    // ============ Browser APIs ============
    
    suspend fun browserGet(url: String): String {
        val requestId = java.util.UUID.randomUUID().toString()
        val deferred = kotlinx.coroutines.CompletableDeferred<String>() // Use fully qualified or import
        com.streambox.app.utils.BrowserBus.requests[requestId] = deferred
        
        val intent = android.content.Intent(context, com.streambox.app.ui.CloudflareSolverActivity::class.java).apply {
            putExtra("URL", url)
            putExtra("REQUEST_ID", requestId)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        
        val resultRaw = deferred.await()
        com.streambox.app.utils.BrowserBus.requests.remove(requestId)
        
        // Attempt to parse JSON envelope (new protocol)
        try {
            if (resultRaw.trim().startsWith("{")) {
                val json = JSONObject(resultRaw)
                if (json.has("html")) {
                    val html = json.getString("html")
                    
                    if (json.has("cookies")) {
                        val cookies = json.getString("cookies")
                        val currentUrl = json.optString("currentUrl", url)
                        try {
                            val domain = java.net.URI(currentUrl).host
                            if (domain != null && cookies.isNotEmpty()) {
                                cookieStore[domain] = cookies
                                Log.d(TAG, "Stored cookies for $domain")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse domain from $currentUrl", e)
                        }
                    }
                    return html
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse browser result as JSON, returning raw", e)
        }
        
        return resultRaw
    }
    
    // ============ HTTP APIs (axios-like) ============
    
    suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder().url(url)
            
            // Auto-inject cookies from Browser sessions
            val hasCookie = headers.keys.any { it.equals("Cookie", ignoreCase = true) }
            if (!hasCookie) {
                try {
                    val domain = java.net.URI(url).host
                    if (domain != null && cookieStore.containsKey(domain)) {
                        val cookies = cookieStore[domain]
                        if (!cookies.isNullOrEmpty()) {
                            requestBuilder.addHeader("Cookie", cookies)
                            // Log.d(TAG, "Injected cookies for $domain")
                        }
                    }
                } catch (e: Exception) {
                    // Ignore URI parse errors
                }
            }
            
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "HTTP GET failed: $url", e)
            throw e
        }
    }
    
    suspend fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        try {
            val contentType = headers["Content-Type"] ?: "application/x-www-form-urlencoded"
            val requestBody = body.toRequestBody(contentType.toMediaType())
            
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
            
            // Auto-inject cookies from Browser sessions
            val hasCookie = headers.keys.any { it.equals("Cookie", ignoreCase = true) }
            if (!hasCookie) {
                try {
                    val domain = java.net.URI(url).host
                    if (domain != null && cookieStore.containsKey(domain)) {
                        val cookies = cookieStore[domain]
                        if (!cookies.isNullOrEmpty()) {
                            requestBuilder.addHeader("Cookie", cookies)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore URI parse errors
                }
            }
            
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "HTTP POST failed: $url", e)
            throw e
        }
    }
    
    // ============ Cheerio-like DOM API ============
    
    fun createCheerioContext(cx: Context, scope: Scriptable, html: String): Scriptable {
        val document = Jsoup.parse(html)
        return CheerioWrapper(cx, scope, document)
    }
    
    /**
     * Wrapper that provides jQuery/Cheerio-like API
     */
    inner class CheerioWrapper(
        private val cx: Context,
        private val parentScope: Scriptable,
        private val document: Document
    ) : BaseFunction() {
        
        init {
            setParentScope(parentScope)
            try {
                setPrototype(getObjectPrototype(parentScope))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set prototype for CheerioLoader", e)
            }
        }
        
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
            val selector = Context.toString(args.getOrNull(0) ?: "")
            val elements = document.select(selector)
            return ElementsWrapper(cx, parentScope, elements)
        }
        
        override fun get(name: String, start: Scriptable): Any {
            return when (name) {
                "html" -> document.html()
                "text" -> document.text()
                else -> super.get(name, start)
            }
        }
    }
    
    /**
     * Wrapper for Elements collection
     */
    inner class ElementsWrapper(
        private val cx: Context,
        private val parentScope: Scriptable,
        private val elements: Elements
    ) : ScriptableObject() {
        
        init {
            setParentScope(parentScope)
            try {
                setPrototype(getObjectPrototype(parentScope))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set prototype for ElementsWrapper", e)
            }
        }
        
        override fun getClassName(): String = "ElementsWrapper"
        
        override fun get(name: String, start: Scriptable): Any {
            return when (name) {
                "length" -> elements.size
                "first" -> createFirstFunction()
                "last" -> createLastFunction()
                "eq" -> createEqFunction()
                "find" -> createFindFunction()
                "text" -> createTextFunction()
                "html" -> createHtmlFunction()
                "attr" -> createAttrFunction()
                "each" -> createEachFunction()
                "map" -> createMapFunction()
                "filter" -> createFilterFunction()
                "parent" -> createParentFunction()
                "children" -> createChildrenFunction()
                "next" -> createNextFunction()
                "prev" -> createPrevFunction()
                "hasClass" -> createHasClassFunction()
                "addClass" -> this
                "removeClass" -> this
                else -> {
                    name.toIntOrNull()?.let { index ->
                        if (index in 0 until elements.size) {
                            return ElementWrapper(cx, parentScope, elements[index])
                        }
                    }
                    super.get(name, start)
                }
            }
        }
        
        override fun get(index: Int, start: Scriptable): Any {
            return if (index in 0 until elements.size) {
                ElementWrapper(cx, parentScope, elements[index])
            } else {
                Undefined.instance
            }
        }
        
        private fun createFirstFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                return if (elements.isNotEmpty()) ElementWrapper(cx, scope, elements.first()) else ElementsWrapper(cx, scope, Elements())
            }
        }
        
        private fun createLastFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                return if (elements.isNotEmpty()) ElementWrapper(cx, scope, elements.last()) else ElementsWrapper(cx, scope, Elements())
            }
        }
        
        private fun createEqFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val index = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                return if (index in 0 until elements.size) ElementWrapper(cx, scope, elements[index]) else ElementsWrapper(cx, scope, Elements())
            }
        }
        
        private fun createFindFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val selector = Context.toString(args.getOrNull(0) ?: "")
                return ElementsWrapper(cx, scope, elements.select(selector))
            }
        }
        
        private fun createTextFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                return elements.text()
            }
        }
        
        private fun createHtmlFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                return elements.html()
            }
        }
        
        private fun createAttrFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val attrName = Context.toString(args.getOrNull(0) ?: "")
                return elements.attr(attrName) ?: ""
            }
        }
        
        private fun createEachFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val callback = args.getOrNull(0) as? Function ?: return this@ElementsWrapper
                elements.forEachIndexed { index, element ->
                    val wrapper = ElementWrapper(cx, scope, element)
                    callback.call(cx, scope, scope, arrayOf(index, wrapper))
                }
                return this@ElementsWrapper
            }
        }
        
        private fun createMapFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val callback = args.getOrNull(0) as? Function ?: return cx.newArray(scope, 0)
                val results = elements.mapIndexed { index, element ->
                    val wrapper = ElementWrapper(cx, scope, element)
                    callback.call(cx, scope, scope, arrayOf(index, wrapper))
                }
                return cx.newArray(scope, results.toTypedArray())
            }
        }
        
        private fun createFilterFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val selector = Context.toString(args.getOrNull(0) ?: "")
                val filtered = elements.mapNotNull { if (it.`is`(selector)) it else null }
                return ElementsWrapper(cx, scope, Elements(filtered))
            }
        }
        
        private fun createParentFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val parents = Elements(elements.mapNotNull { it.parent() })
                return ElementsWrapper(cx, parentScope, parents)
            }
        }
        
        private fun createChildrenFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val allChildren = Elements()
                elements.forEach { allChildren.addAll(it.children()) }
                return ElementsWrapper(cx, parentScope, allChildren)
            }
        }
        
        private fun createNextFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val nextSiblings = Elements(elements.mapNotNull { it.nextElementSibling() })
                return ElementsWrapper(cx, parentScope, nextSiblings)
            }
        }
        
        private fun createPrevFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val prevSiblings = Elements(elements.mapNotNull { it.previousElementSibling() })
                return ElementsWrapper(cx, parentScope, prevSiblings)
            }
        }
        
        private fun createHasClassFunction() = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val className = Context.toString(args.getOrNull(0) ?: "")
                return elements.any { it.hasClass(className) }
            }
        }
    }
    
    /**
     * Wrapper for single Element
     */
    inner class ElementWrapper(
        private val cx: Context,
        private val parentScope: Scriptable,
        private val element: Element
    ) : ScriptableObject() {
        
        init {
            setParentScope(parentScope)
            try {
                setPrototype(getObjectPrototype(parentScope))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set prototype for ElementWrapper", e)
            }
        }
        
        override fun getClassName(): String = "ElementWrapper"
        
        override fun get(name: String, start: Scriptable): Any {
            return when (name) {
                "find" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        val selector = Context.toString(args.getOrNull(0) ?: "")
                        return ElementsWrapper(cx, scope, element.select(selector))
                    }
                }
                "text" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        return element.text()
                    }
                }
                "html" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        return element.html()
                    }
                }
                "attr" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        val attrName = Context.toString(args.getOrNull(0) ?: "")
                        return element.attr(attrName) ?: ""
                    }
                }
                "parent" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        val parent = element.parent()
                        return if (parent != null) ElementWrapper(cx, scope, parent) else Undefined.instance
                    }
                }
                "children" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        return ElementsWrapper(cx, scope, element.children())
                    }
                }
                "next" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        val next = element.nextElementSibling()
                        return if (next != null) ElementWrapper(cx, scope, next) else Undefined.instance
                    }
                }
                "prev" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        val prev = element.previousElementSibling()
                        return if (prev != null) ElementWrapper(cx, scope, prev) else Undefined.instance
                    }
                }
                "hasClass" -> object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                        val className = Context.toString(args.getOrNull(0) ?: "")
                        return element.hasClass(className)
                    }
                }
                else -> super.get(name, start)
            }
        }
    }
    
    // ============ Crypto APIs ============
    
    fun base64Encode(text: String): String {
        return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    
    fun base64Decode(encoded: String): String {
        return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    }
    
    fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun aesDecrypt(data: String, key: String, iv: String? = null): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
            
            val ivSpec = if (iv != null) {
                IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
            } else {
                IvParameterSpec(ByteArray(16))
            }
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decodedData = Base64.decode(data, Base64.DEFAULT)
            String(cipher.doFinal(decodedData), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "AES decrypt failed", e)
            ""
        }
    }
}
