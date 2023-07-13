package com.example.filedownloader.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.filedownloader.models.DownloadTask

// This class is used to receive the broadcast from the notification buttons
class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("TASK_ID")
        val downloadListener = taskId?.let { DownloadListenerHolder.getDownloadListener(it) }
        if (downloadListener != null) {
            val action = intent.action
            if (action == "com.example.filedownloader.PAUSE_ACTION") {
                downloadListener.pauseThisDownload(taskId)
            } else if (action == "com.example.filedownloader.CANCEL_ACTION") {
                downloadListener.cancelThisDownload(taskId)
            }
        }
    }
}