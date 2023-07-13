package com.example.filedownloader.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

// This class is used to check if the app is in foreground or background
class AppVisibilityTracker(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private val activityCount = AtomicInteger(0)
    private var isAppVisible = true

    init {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (activityCount.incrementAndGet() == 1) {
            isAppVisible = true
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // No need to track individual activity resume events
    }

    override fun onActivityPaused(activity: Activity) {
        // No need to track individual activity pause events
    }

    override fun onActivityStopped(activity: Activity) {
        if (activityCount.decrementAndGet() == 0) {
            isAppVisible = false
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    fun isAppVisible(): Boolean {
        return isAppVisible
    }

    fun unregister() {
        app.unregisterActivityLifecycleCallbacks(this)
    }
}