package com.example.filedownloader.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

//Object to check current network state
object NetworkUtil {
    fun getNetworkState(context: Context): NetworkState {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

        if (networkCapabilities != null) {
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.CELLULAR
                else -> NetworkState.DISCONNECTED
            }
        }

        return NetworkState.DISCONNECTED
    }

    enum class NetworkState {
        WIFI,
        CELLULAR,
        DISCONNECTED
    }
}