package com.meni.bubble_app

import android.content.Context
import android.content.SharedPreferences

object CustomUrlManager {

    private const val PREFS_NAME = "CustomUrlPrefs"
    private const val KEY_CUSTOM_URL = "custom_url"
    private const val KEY_CUSTOM_TITLE = "custom_title"

    private lateinit var sharedPreferences: SharedPreferences

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun save(url: String, title: String) {
        sharedPreferences.edit()
            .putString(KEY_CUSTOM_URL, url)
            .putString(KEY_CUSTOM_TITLE, title)
            .apply()
    }

    fun getUrl(): String? {
        return sharedPreferences.getString(KEY_CUSTOM_URL, null)
    }

    fun getTitle(): String? {
        return sharedPreferences.getString(KEY_CUSTOM_TITLE, null)
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    fun hasCustomUrl(): Boolean {
        return getUrl() != null
    }
}