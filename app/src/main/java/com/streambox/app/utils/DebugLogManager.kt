package com.streambox.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Centralized debug logging manager for StreamBox
 * Captures extension execution flow, extraction process, and errors
 * Logs can be viewed in-app and copied to clipboard
 */
object DebugLogManager {
    
    private const val MAX_LOGS = 500
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    @Volatile
    var isEnabled: Boolean = false
        private set
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            val levelStr = when (level) {
                LogLevel.DEBUG -> "D"
                LogLevel.INFO -> "I"
                LogLevel.WARN -> "W"
                LogLevel.ERROR -> "E"
                LogLevel.JS -> "JS"
            }
            return "[$time] [$levelStr/$tag] $message"
        }
    }
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR, JS
    }
    
    fun enable() {
        isEnabled = true
        clear()
        log(LogLevel.INFO, "DebugLog", "=== Debug logging ENABLED ===")
    }
    
    fun disable() {
        log(LogLevel.INFO, "DebugLog", "=== Debug logging DISABLED ===")
        isEnabled = false
    }
    
    fun toggle(): Boolean {
        if (isEnabled) disable() else enable()
        return isEnabled
    }
    
    fun clear() {
        logs.clear()
    }
    
    fun log(level: LogLevel, tag: String, message: String) {
        if (!isEnabled) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        logs.add(entry)
        
        // Trim if too many logs
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        
        // Also output to Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
            LogLevel.JS -> Log.d("JS/$tag", message)
        }
    }
    
    // Convenience methods
    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)
    fun js(tag: String, message: String) = log(LogLevel.JS, tag, message)
    
    fun getLogs(): List<LogEntry> = logs.toList()
    
    fun getLogsFormatted(): String {
        if (logs.isEmpty()) {
            return "No logs captured yet.\n\nEnable debug logging and perform some actions in the app."
        }
        
        val sb = StringBuilder()
        sb.appendLine("=== StreamBox Debug Log ===")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Total entries: ${logs.size}")
        sb.appendLine("".padEnd(50, '='))
        sb.appendLine()
        
        logs.forEach { entry ->
            sb.appendLine(entry.format())
        }
        
        return sb.toString()
    }
    
    fun copyToClipboard(context: Context): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("StreamBox Debug Logs", getLogsFormatted())
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Log.e("DebugLogManager", "Failed to copy to clipboard", e)
            false
        }
    }
    
    fun getLogCount(): Int = logs.size
}
