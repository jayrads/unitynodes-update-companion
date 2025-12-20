package com.jayrads.unityupdater

import android.content.Context

object Prefs {
    private const val PREFS = "unity_updater_prefs"
    private const val KEY_DL_ID = "download_id"
    private const val KEY_APK_PATH = "apk_path"

    fun setDownloadId(ctx: Context, id: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DL_ID, id).apply()
    }

    fun getDownloadId(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DL_ID, -1L)

    fun setApkPath(ctx: Context, path: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_APK_PATH, path).apply()
    }

    fun getApkPath(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APK_PATH, null)
}
