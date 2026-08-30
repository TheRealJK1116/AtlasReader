package com.atlasreader.core.common

import android.util.Log

/**
 * Central logging façade. Keeps a single place to add crash reporting, log
 * rotation and privacy filtering later without touching call sites.
 */
object AtlasLog {
    private const val TAG = "AtlasReader"

    fun v(message: String) = Log.v(TAG, message)
    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, tr: Throwable? = null) = Log.w(TAG, message, tr)
    fun e(message: String, tr: Throwable? = null) = Log.e(TAG, message, tr)
}
