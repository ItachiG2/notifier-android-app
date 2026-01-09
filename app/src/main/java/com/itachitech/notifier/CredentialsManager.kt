package com.itachitech.notifier

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object CredentialsManager {

    private const val PREFS_NAME = "NotifierPrefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_PIN = "pin"
    private const val SERVICE_ID_PREFIX = "com.itachitech.notifier."

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCredentials(context: Context, username: String, pin: String) {
        getPrefs(context).edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PIN, pin)
            .apply()
    }

    fun getUsername(context: Context): String? {
        return getPrefs(context).getString(KEY_USERNAME, null)
    }

    fun hasCredentials(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_USERNAME) && prefs.contains(KEY_PIN)
    }

    fun logout(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PIN)
            .apply()
    }

    fun getHashedServiceId(context: Context): String? {
        val prefs = getPrefs(context)
        val username = prefs.getString(KEY_USERNAME, null)
        val pin = prefs.getString(KEY_PIN, null)

        if (username == null || pin == null) {
            return null
        }

        val input = "$SERVICE_ID_PREFIX$username$pin"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        // Return a portion of the hash as the service ID for brevity
        return hashBytes.take(16).joinToString("") { "%02x".format(it) }
    }
}