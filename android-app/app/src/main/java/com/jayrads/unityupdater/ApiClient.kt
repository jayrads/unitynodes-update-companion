package com.jayrads.unityupdater

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ApiClient {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun fetchLatest(): LatestInfo? {
        val req = Request.Builder()
            .url("${Config.BACKEND_BASE_URL}/latest.json")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val o = JSONObject(body)
            return LatestInfo(
                versionName = o.optString("versionName", ""),
                apkUrl = o.optString("apkUrl", ""),
                fileName = o.optString("fileName", ""),
                publishedAt = o.optString("publishedAt", ""),
                sizeBytes = o.optLong("sizeBytes", 0L),
                sha256 = o.optString("sha256", "")
            )
        }
    }

    fun registerToken(context: Context, token: String) {
        val payload = JSONObject()
            .put("token", token)
            .put("deviceId", DeviceId.getOrCreate(context))
            .put("appVersion", AppVersion.getInstalledVersionName(context))

        val req = Request.Builder()
            .url("${Config.BACKEND_BASE_URL}/registerToken")
            .addHeader("Authorization", "Bearer ${Config.API_KEY}")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(req).execute().close()
    }
}
