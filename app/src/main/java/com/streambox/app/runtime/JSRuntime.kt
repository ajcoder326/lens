package com.streambox.app.runtime

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom WrapFactory that's Android-compatible
 * Avoids accessing classes like javax.lang.model.SourceVersion that don't exist on Android
 */
class AndroidWrapFactory : WrapFactory() {
    override fun wrap(cx: Context?, scope: Scriptable?, obj: Any?, staticType: Class<*>?): Any? {
        return try {
            // Avoid wrapping certain types that can cause issues on Android
            if (obj is Throwable) {
                // For throwables, just return the message string to avoid class loading issues
                return obj.message ?: obj.toString()
            }
            super.wrap(cx, scope, obj, staticType)
        } catch (e: NoClassDefFoundError) {
            // Fallback: return string representation
            obj?.toString() ?: Undefined.instance
        } catch (e: ClassNotFoundException) {
            obj?.toString() ?: Undefined.instance
        }
    }
}

/**
 * Custom ContextFactory for Android
 */
class AndroidContextFactory : ContextFactory() {
    override fun makeContext(): Context {
        val cx = super.makeContext()
        cx.wrapFactory = AndroidWrapFactory()
        return cx
    }
}

/**
 * JavaScript Runtime wrapper using Mozilla Rhino
 * Provides sandboxed JS execution environment for extensions
 */
@Singleton
class JSRuntime @Inject constructor(
    private val jsApis: JSApis
) {
    companion object {
        private const val TAG = "JSRuntime"
        
        init {
            // Initialize with our custom ContextFactory
            if (!ContextFactory.hasExplicitGlobal()) {
                ContextFactory.initGlobal(AndroidContextFactory())
            }
        }
    }
    
    /**
     * Execute a JavaScript module and return a ScriptableObject
     * that can be used to call functions
     */
    suspend fun executeModule(moduleCode: String): Result<ScriptableObject> = withContext(Dispatchers.IO) {
        try {
            val context = Context.enter()
            context.optimizationLevel = -1 // Required for Android
            context.languageVersion = Context.VERSION_ES6
            
            val scope = context.initStandardObjects()
            
            // Inject APIs into the scope
            injectApis(context, scope)
            
            // Execute the module code
            val result = context.evaluateString(scope, moduleCode, "extension", 1, null)
            
            // The result should be a module object
            val moduleObject = if (result is ScriptableObject) {
                result
            } else {
                scope
            }
            
            Context.exit()
            Result.success(moduleObject)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute module", e)
            Result.failure(e)
        }
    }
    
    /**
     * Evaluate JS code and get a variable's value
     * Used for catalog.js which exports 'catalog' as a variable
     */
    suspend fun <T> evaluateAndGetVariable(
        moduleCode: String,
        variableName: String,
        resultMapper: (Any?) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        var context: Context? = null
        try {
            context = Context.enter()
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            
            val scope = context.initStandardObjects()
            injectApis(context, scope)
            
            // Execute the module code
            context.evaluateString(scope, moduleCode, "extension", 1, null)
            
            // Get the variable
            val variable = scope.get(variableName, scope)
            Log.d(TAG, "Variable '$variableName' type: ${variable?.javaClass?.simpleName}, value: $variable")
            
            if (variable == null || variable is Undefined) {
                Log.w(TAG, "Variable '$variableName' not found or undefined")
                return@withContext Result.success(resultMapper(null))
            }
            
            val unwrapped = unwrapResult(variable, scope)
            Log.d(TAG, "Unwrapped result: $unwrapped")
            
            Result.success(resultMapper(unwrapped))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get variable $variableName", e)
            Result.failure(e)
        } finally {
            context?.let { Context.exit() }
        }
    }
    
    /**
     * Execute a function from a module with given arguments
     */
    suspend fun <T> callFunction(
        moduleCode: String,
        functionName: String,
        args: List<Any?> = emptyList(),
        resultMapper: (Any?) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        var context: Context? = null
        try {
            context = Context.enter()
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            
            val scope = context.initStandardObjects()
            
            // Inject APIs
            injectApis(context, scope)
            
            // Execute module code
            context.evaluateString(scope, moduleCode, "extension", 1, null)
            
            // Get the function
            val func = scope.get(functionName, scope)
            if (func !is Function) {
                return@withContext Result.failure(Exception("Function '$functionName' not found"))
            }
            
            // Convert args to Rhino types
            val rhinoArgs = args.map { arg ->
                when (arg) {
                    null -> Undefined.instance
                    is String -> arg
                    is Number -> arg
                    is Boolean -> arg
                    is Map<*, *> -> {
                        val obj = context.newObject(scope)
                        arg.forEach { (k, v) ->
                            ScriptableObject.putProperty(obj, k.toString(), Context.javaToJS(v, scope))
                        }
                        obj
                    }
                    else -> Context.javaToJS(arg, scope)
                }
            }.toTypedArray()
            
            // Call the function
            val result = func.call(context, scope, scope, rhinoArgs)
            
            // Handle Promise-like results
            val finalResult = unwrapResult(result, scope)
            
            Result.success(resultMapper(finalResult))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call function $functionName", e)
            Result.failure(e)
        } finally {
            context?.let { Context.exit() }
        }
    }
    
    private fun injectApis(context: Context, scope: Scriptable) {
        // Inject axios-like fetch API
        val axiosObj = context.newObject(scope)
        ScriptableObject.putProperty(axiosObj, "get", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val url = Context.toString(args.getOrNull(0))
                val options = args.getOrNull(1) as? ScriptableObject
                val headers = extractHeaders(options)
                return wrapPromise(cx, scope) {
                    jsApis.httpGet(url, headers)
                }
            }
        })
        ScriptableObject.putProperty(axiosObj, "post", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val url = Context.toString(args.getOrNull(0))
                val body = args.getOrNull(1)?.let { Context.toString(it) } ?: ""
                val options = args.getOrNull(2) as? ScriptableObject
                val headers = extractHeaders(options)
                return wrapPromise(cx, scope) {
                    jsApis.httpPost(url, body, headers)
                }
            }
        })
        ScriptableObject.putProperty(scope, "axios", axiosObj)
        
        // Inject cheerio-like load function
        val cheerioLoader = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val html = Context.toString(args.getOrNull(0))
                return jsApis.createCheerioContext(cx, scope, html)
            }
        }
        val cheerioObj = context.newObject(scope)
        ScriptableObject.putProperty(cheerioObj, "load", cheerioLoader)
        ScriptableObject.putProperty(scope, "cheerio", cheerioObj)
        
        // Inject console
        val consoleObj = context.newObject(scope)
        ScriptableObject.putProperty(consoleObj, "log", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val message = args.joinToString(" ") { Context.toString(it) }
                Log.d("JS", message)
                com.streambox.app.utils.DebugLogManager.js("Extension", message)
                return Undefined.instance
            }
        })
        ScriptableObject.putProperty(consoleObj, "error", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val message = args.joinToString(" ") { Context.toString(it) }
                Log.e("JS", message)
                com.streambox.app.utils.DebugLogManager.e("JS-Error", message)
                return Undefined.instance
            }
        })
        ScriptableObject.putProperty(consoleObj, "warn", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val message = args.joinToString(" ") { Context.toString(it) }
                Log.w("JS", message)
                com.streambox.app.utils.DebugLogManager.w("JS-Warn", message)
                return Undefined.instance
            }
        })
        ScriptableObject.putProperty(scope, "console", consoleObj)
        
        // Inject atob and btoa for base64
        ScriptableObject.putProperty(scope, "atob", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val encoded = Context.toString(args.getOrNull(0))
                return jsApis.base64Decode(encoded)
            }
        })
        ScriptableObject.putProperty(scope, "btoa", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val text = Context.toString(args.getOrNull(0))
                return jsApis.base64Encode(text)
            }
        })
        
        // Inject crypto utilities
        val cryptoObj = context.newObject(scope)
        ScriptableObject.putProperty(cryptoObj, "md5", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                return jsApis.md5(Context.toString(args.getOrNull(0)))
            }
        })
        ScriptableObject.putProperty(cryptoObj, "aesDecrypt", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val data = Context.toString(args.getOrNull(0))
                val key = Context.toString(args.getOrNull(1))
                val iv = args.getOrNull(2)?.let { Context.toString(it) }
                return jsApis.aesDecrypt(data, key, iv)
            }
        })
        ScriptableObject.putProperty(scope, "crypto", cryptoObj)
        
        // Inject storage APIs
        val storageObj = context.newObject(scope)
        ScriptableObject.putProperty(storageObj, "save", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val key = Context.toString(args.getOrNull(0))
                val value = Context.toString(args.getOrNull(1))
                jsApis.saveData(key, value)
                return Undefined.instance
            }
        })
        ScriptableObject.putProperty(storageObj, "load", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                val key = Context.toString(args.getOrNull(0))
                return jsApis.loadData(key)
            }
        })
        ScriptableObject.putProperty(scope, "storage", storageObj)
        
        // Inject browser APIs (WebView)
        val browserObj = context.newObject(scope)
        ScriptableObject.putProperty(browserObj, "get", object : BaseFunction() {
             override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                 val url = Context.toString(args.getOrNull(0))
                 // Block thread until WebView returns (Rhino doesn't support await)
                 return kotlinx.coroutines.runBlocking {
                     jsApis.browserGet(url)
                 }
             }
        })
        ScriptableObject.putProperty(scope, "browser", browserObj)
    }
    
    private fun extractHeaders(options: ScriptableObject?): Map<String, String> {
        if (options == null) return emptyMap()
        
        val headers = mutableMapOf<String, String>()
        val headersObj = options.get("headers", options)
        if (headersObj is ScriptableObject) {
            for (id in headersObj.ids) {
                val key = id.toString()
                val value = headersObj.get(key, headersObj)
                if (value != null && value !is Undefined) {
                    headers[key] = Context.toString(value)
                }
            }
        }
        return headers
    }
    
    private fun wrapPromise(cx: Context, scope: Scriptable, block: suspend () -> Any?): Any {
        // For simplicity, execute synchronously and return result
        // In production, this should use Continuation or callbacks
        return try {
            kotlinx.coroutines.runBlocking {
                val result = block()
                // Convert result to JS object
                when (result) {
                    is String -> {
                        val obj = cx.newObject(scope)
                        ScriptableObject.putProperty(obj, "data", result)
                        obj
                    }
                    is Map<*, *> -> {
                        val obj = cx.newObject(scope)
                        result.forEach { (k, v) ->
                            ScriptableObject.putProperty(obj, k.toString(), Context.javaToJS(v, scope))
                        }
                        obj
                    }
                    else -> Context.javaToJS(result, scope)
                }
            }
        } catch (e: Exception) {
            // Don't use WrappedException - it can cause ClassNotFoundException on Android
            Log.e(TAG, "Error in wrapPromise", e)
            throw RuntimeException("JS Error: ${e.message}")
        }
    }
    
    private fun unwrapResult(result: Any?, scope: Scriptable): Any? {
        return when (result) {
            is Undefined -> null
            is NativeJavaObject -> result.unwrap()
            is NativeArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until result.length.toInt()) {
                    list.add(unwrapResult(result.get(i, result), scope))
                }
                list
            }
            is ScriptableObject -> {
                val map = mutableMapOf<String, Any?>()
                for (id in result.ids) {
                    val key = id.toString()
                    map[key] = unwrapResult(result.get(key, result), scope)
                }
                map
            }
            else -> result
        }
    }
}
