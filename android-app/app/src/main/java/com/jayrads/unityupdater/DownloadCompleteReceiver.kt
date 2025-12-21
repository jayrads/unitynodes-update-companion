package com.jayrads.unityupdater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DownloadCompleteReceiver(
    private val onDone: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val expectedId = Prefs.getDownloadId(context)

        Log.d(TAG, "onReceive DOWNLOAD_COMPLETE completedId=$completedId expectedId=$expectedId")

        if (completedId <= 0L || completedId != expectedId) {
            Log.d(TAG, "Ignoring id=$completedId (not ours)")
            return
        }

        // Unregister the one-shot receiver ASAP.
        onDone()

        // Delegate verification + pending install + notification to WorkManager.
        Log.d(TAG, "Our download completed -> enqueue VerifyDownloadWorker id=$completedId")
        VerifyDownloadWorker.enqueue(context.applicationContext, completedId)
    }

    companion object {
        private const val TAG = "DownloadCompleteReceiver"
    }
}
