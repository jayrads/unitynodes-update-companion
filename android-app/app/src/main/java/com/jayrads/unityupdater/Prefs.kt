package com.jayrads.unityupdater

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREFS = "unity_updater_prefs"

    private const val KEY_DL_ID = "download_id"
    private const val KEY_APK_PATH = "apk_path"
    private const val KEY_PENDING_INSTALL_PATH = "pending_install_path"
    private const val KEY_VERIFYING = "verifying_download"

    // --- TOFU publisher identity ---
    private const val KEY_TRUSTED_SIGNER_SHA256 = "trusted_signer_sha256"
    private const val KEY_PENDING_INSTALL_CHANGED_SIGNER = "pending_install_changed_signer"
    private const val KEY_PENDING_INSTALL_SIGNER_SHA256 = "pending_install_signer_sha256"
    private const val KEY_PENDING_INSTALL_SIGNER_PREV_SHA256 = "pending_install_signer_prev_sha256"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setDownloadId(ctx: Context, id: Long) {
        prefs(ctx).edit().putLong(KEY_DL_ID, id).apply()
    }

    fun getDownloadId(ctx: Context): Long =
        prefs(ctx).getLong(KEY_DL_ID, -1L)

    fun setApkPath(ctx: Context, path: String) {
        prefs(ctx).edit().putString(KEY_APK_PATH, path).apply()
    }

    fun getApkPath(ctx: Context): String? =
        prefs(ctx).getString(KEY_APK_PATH, null)

    fun setPendingInstall(ctx: Context, apkPath: String) {
        prefs(ctx).edit().putString(KEY_PENDING_INSTALL_PATH, apkPath).apply()
    }

    fun getPendingInstall(ctx: Context): String? =
        prefs(ctx).getString(KEY_PENDING_INSTALL_PATH, null)

    fun clearPendingInstall(ctx: Context) {
        prefs(ctx).edit().remove(KEY_PENDING_INSTALL_PATH).apply()
    }

    fun setVerifying(ctx: Context, verifying: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_VERIFYING, verifying).apply()
    }

    fun isVerifying(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_VERIFYING, false)

    // ---------- TOFU helpers ----------

    fun setTrustedSignerSha256(ctx: Context, sha256: String) {
        prefs(ctx).edit().putString(KEY_TRUSTED_SIGNER_SHA256, sha256).apply()
    }

    fun getTrustedSignerSha256(ctx: Context): String? =
        prefs(ctx).getString(KEY_TRUSTED_SIGNER_SHA256, null)

    fun setPendingInstallSignerInfo(
        ctx: Context,
        signerSha256: String,
        previousSignerSha256: String?,
        changed: Boolean
    ) {
        prefs(ctx).edit()
            .putBoolean(KEY_PENDING_INSTALL_CHANGED_SIGNER, changed)
            .putString(KEY_PENDING_INSTALL_SIGNER_SHA256, signerSha256)
            .putString(KEY_PENDING_INSTALL_SIGNER_PREV_SHA256, previousSignerSha256)
            .apply()
    }

    fun isPendingInstallSignerChanged(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PENDING_INSTALL_CHANGED_SIGNER, false)

    fun getPendingInstallSignerSha256(ctx: Context): String? =
        prefs(ctx).getString(KEY_PENDING_INSTALL_SIGNER_SHA256, null)

    fun getPendingInstallPrevSignerSha256(ctx: Context): String? =
        prefs(ctx).getString(KEY_PENDING_INSTALL_SIGNER_PREV_SHA256, null)

    fun clearPendingInstallSignerInfo(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_PENDING_INSTALL_CHANGED_SIGNER)
            .remove(KEY_PENDING_INSTALL_SIGNER_SHA256)
            .remove(KEY_PENDING_INSTALL_SIGNER_PREV_SHA256)
            .apply()
    }

    fun clearAllInstallState(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_DL_ID)
            .remove(KEY_APK_PATH)
            .remove(KEY_PENDING_INSTALL_PATH)
            .remove(KEY_VERIFYING)
            .remove(KEY_TRUSTED_SIGNER_SHA256)
            .remove(KEY_PENDING_INSTALL_CHANGED_SIGNER)
            .remove(KEY_PENDING_INSTALL_SIGNER_SHA256)
            .remove(KEY_PENDING_INSTALL_SIGNER_PREV_SHA256)
            .apply()
    }

}
