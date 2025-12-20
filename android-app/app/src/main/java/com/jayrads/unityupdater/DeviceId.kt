package com.jayrads.unityupdater

import android.content.Context
import java.util.UUID

object DeviceId {
    private const val PREFS = "unity_updater_prefs"
    private const val KEY = "device_id"

    fun getOrCreate(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = p.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        p.edit().putString(KEY, created).apply()
        return created
    }
}
