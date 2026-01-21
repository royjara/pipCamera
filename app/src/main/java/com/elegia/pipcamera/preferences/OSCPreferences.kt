package com.elegia.pipcamera.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class for managing OSC configuration preferences
 */
class OSCPreferences private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "osc_preferences"
        private const val KEY_OSC_HOST = "osc_host"
        private const val KEY_OSC_PORT = "osc_port"
        private const val KEY_OSC_ADDRESS = "osc_address"

        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8000
        private const val DEFAULT_ADDRESS = "/audio/stream"

        @Volatile
        private var INSTANCE: OSCPreferences? = null

        fun getInstance(context: Context): OSCPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OSCPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var oscHost: String
        get() = sharedPreferences.getString(KEY_OSC_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
        set(value) = sharedPreferences.edit().putString(KEY_OSC_HOST, value).apply()

    var oscPort: Int
        get() = sharedPreferences.getInt(KEY_OSC_PORT, DEFAULT_PORT)
        set(value) = sharedPreferences.edit().putInt(KEY_OSC_PORT, value).apply()

    var oscAddress: String
        get() = sharedPreferences.getString(KEY_OSC_ADDRESS, DEFAULT_ADDRESS) ?: DEFAULT_ADDRESS
        set(value) = sharedPreferences.edit().putString(KEY_OSC_ADDRESS, value).apply()

    /**
     * Save all OSC configuration values at once
     */
    fun saveOSCConfig(host: String, port: Int, address: String) {
        sharedPreferences.edit().apply {
            putString(KEY_OSC_HOST, host)
            putInt(KEY_OSC_PORT, port)
            putString(KEY_OSC_ADDRESS, address)
            apply()
        }
    }

    /**
     * Get all OSC configuration values as a triple
     */
    fun getOSCConfig(): Triple<String, Int, String> {
        return Triple(oscHost, oscPort, oscAddress)
    }

    /**
     * Clear all stored OSC preferences
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}