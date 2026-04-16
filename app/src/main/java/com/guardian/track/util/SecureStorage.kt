package com.guardian.track.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs by lazy { createSecurePrefs() }

    private fun createSecurePrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return try {
            EncryptedSharedPreferences.create(
                context,
                "guardian_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {

            android.util.Log.e("SecureStorage", "Corrupted encrypted prefs, resetting", e)

            // 🔥 DELETE BROKEN DATA
            context.deleteSharedPreferences("guardian_secure_prefs")

            // 🔁 RETRY (clean state)
            EncryptedSharedPreferences.create(
                context,
                "guardian_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun saveApiKey(key: String) =
        prefs.edit().putString("api_key", key).apply()

    fun getApiKey(): String =
        prefs.getString("api_key", "") ?: ""

    fun saveEmergencyNumber(number: String) =
        prefs.edit().putString("emergency_phone", number).apply()

    fun getEmergencyNumber(): String =
        prefs.getString("emergency_phone", "") ?: ""
}