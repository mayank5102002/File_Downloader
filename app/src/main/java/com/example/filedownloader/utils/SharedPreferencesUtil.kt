package com.example.filedownloader.utils

import android.content.Context
import android.content.SharedPreferences


// This class is used to store and retrieve the maximum number of parallel downloads
object SharedPreferencesUtil {
    private const val PREFS_NAME = "MyPrefs"
    private const val KEY_MAX_PARALLEL_DOWNLOADS = "max_parallel_downloads"

    fun getMaxParallelDownloads(context: Context): Int {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(KEY_MAX_PARALLEL_DOWNLOADS, 1) // Default value is 1 if not found
    }

    fun setMaxParallelDownloads(context: Context, maxParallelDownloads: Int) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putInt(KEY_MAX_PARALLEL_DOWNLOADS, maxParallelDownloads)
        editor.apply()
    }
}