package com.example.filedownloader.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build


// This class is used to detect network changes and start the service
class NetworkChangeReceiver : BroadcastReceiver() {

    // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
    override fun onReceive(context: Context, intent: Intent) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = cm.getNetworkCapabilities(cm.activeNetwork)

        val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        if (isConnected) {
            startService(context)
        }
    }

    // This method is used to start the service
    private fun startService(context: Context){
        val serviceIntent = Intent(context, DownloadService::class.java)

        // Start the foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}