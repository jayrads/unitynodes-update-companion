package com.jayrads.unityupdater

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runCatching { ApiClient.registerToken(applicationContext, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val versionName = message.data["versionName"] ?: "unknown"
        NotificationHelper.showUpdateAvailable(applicationContext, versionName)
    }
}
