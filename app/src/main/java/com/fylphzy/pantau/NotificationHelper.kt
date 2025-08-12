package com.fylphzy.pantau

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {

    const val NOTIF_CHANNEL_ID = "location_service_channel"
    private const val NOTIF_CHANNEL_NAME = "Location Service"
    private const val NOTIF_CHANNEL_DESC = "Layanan mengirim lokasi secara berkala"

    fun createNotificationChannelIfNeeded(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(NOTIF_CHANNEL_ID)
        if (existing == null) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = NOTIF_CHANNEL_DESC
            }
            manager.createNotificationChannel(ch)
        }
    }
}
