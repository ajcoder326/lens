package com.streambox.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.media3.common.Player

/**
 * Helper class for Picture-in-Picture functionality
 */
object PipHelper {
    
    /**
     * Check if PIP is supported on this device
     */
    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    
    /**
     * Check if the activity is currently in PIP mode
     */
    fun isInPipMode(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.isInPictureInPictureMode
    }
    
    /**
     * Enter Picture-in-Picture mode
     * @param activity The activity to enter PIP mode
     * @param player Optional player to get aspect ratio from
     * @param sourceRect Optional source rect for animation
     * @return true if PIP was entered successfully
     */
    fun enterPipMode(
        activity: Activity,
        player: Player? = null,
        sourceRect: Rect? = null
    ): Boolean {
        if (!isPipSupported(activity)) {
            return false
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = buildPipParams(player, sourceRect)
                return activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        
        return false
    }
    
    /**
     * Update PIP params while in PIP mode (e.g., when video aspect ratio changes)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePipParams(activity: Activity, player: Player? = null, sourceRect: Rect? = null) {
        if (isInPipMode(activity)) {
            try {
                val params = buildPipParams(player, sourceRect)
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Build PictureInPictureParams based on video aspect ratio
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(player: Player?, sourceRect: Rect?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        
        // Set aspect ratio based on video
        val aspectRatio = getVideoAspectRatio(player)
        builder.setAspectRatio(aspectRatio)
        
        // Set source rect for smooth animation
        sourceRect?.let {
            builder.setSourceRectHint(it)
        }
        
        // Auto-enter PIP when user leaves app (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }
        
        return builder.build()
    }
    
    /**
     * Get aspect ratio from player, defaulting to 16:9
     */
    private fun getVideoAspectRatio(player: Player?): Rational {
        if (player == null) {
            return Rational(16, 9)
        }
        
        val videoSize = player.videoSize
        if (videoSize.width > 0 && videoSize.height > 0) {
            // Clamp aspect ratio to valid PIP range (1:2.39 to 2.39:1)
            val width = videoSize.width
            val height = videoSize.height
            val ratio = width.toFloat() / height
            
            return when {
                ratio < 0.42f -> Rational(1, 2) // Min ratio
                ratio > 2.39f -> Rational(239, 100) // Max ratio
                else -> Rational(width, height)
            }
        }
        
        return Rational(16, 9)
    }
    
    /**
     * Set up auto-enter PIP mode for the activity (Android 12+)
     * Call this when playback starts
     */
    fun setupAutoPip(activity: Activity, player: Player?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isPipSupported(activity)) {
            try {
                val params = buildPipParams(player, null)
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Disable auto-enter PIP (call when stopping playback)
     */
    fun disableAutoPip(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isPipSupported(activity)) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(false)
                    .build()
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
