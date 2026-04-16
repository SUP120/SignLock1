package com.sup.signlock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sup.signlock.LockScreenActivity
import com.sup.signlock.R
import com.sup.signlock.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var preferencesManager: PreferencesManager
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var lastForegroundApp: String? = null
    private var lockedApps: Set<String> = emptySet()
    private var isReady = false
    private var lastLockScreenTime = 0L

    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHANNEL_ID = "AppMonitorChannel"
        private const val NOTIFICATION_ID = 1
        private const val CHECK_INTERVAL = 500L // Check every 500ms
        private const val LOCK_SCREEN_COOLDOWN = 2000L // Don't re-launch within 2s

        /**
         * Apps that have been successfully unlocked via signature.
         * These won't be re-locked until the user navigates away to a different app.
         */
        val temporarilyUnlockedApps = mutableSetOf<String>()
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // Stop any existing monitoring loop to prevent duplicates
        monitoringRunnable?.let { handler.removeCallbacks(it) }

        // Load locked apps FIRST, then start monitoring — no race condition
        serviceScope.launch {
            lockedApps = preferencesManager.getLockedApps().first()
            isReady = true
            Log.d(TAG, "Loaded ${lockedApps.size} locked apps: $lockedApps")
            handler.post { startMonitoring() }
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        Log.d(TAG, "Starting foreground app monitoring")
        monitoringRunnable = object : Runnable {
            override fun run() {
                if (isReady) {
                    checkForegroundApp()
                }
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
        handler.post(monitoringRunnable!!)
    }

    private fun checkForegroundApp() {
        val currentApp = getForegroundApp() ?: return

        // Skip our own package (lock screen is showing)
        if (currentApp == packageName) return

        // If the user navigated to a DIFFERENT app, clear the unlock status
        // of the previous app so it re-locks next time they open it
        if (currentApp != lastForegroundApp) {
            lastForegroundApp?.let { previousApp ->
                if (temporarilyUnlockedApps.remove(previousApp)) {
                    Log.d(TAG, "Cleared unlock status for: $previousApp")
                }
            }
            lastForegroundApp = currentApp
        }

        // Check if this app is locked AND not temporarily unlocked
        if (lockedApps.contains(currentApp) && !temporarilyUnlockedApps.contains(currentApp)) {
            // Debounce: don't spam lock screens
            val now = System.currentTimeMillis()
            if (now - lastLockScreenTime > LOCK_SCREEN_COOLDOWN) {
                lastLockScreenTime = now
                Log.d(TAG, "Showing lock screen for: $currentApp")
                showLockScreen(currentApp)
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val currentTime = System.currentTimeMillis()

        // Use a 5-second window to ensure we catch events on all devices
        val usageEvents = usageStatsManager.queryEvents(currentTime - 5000, currentTime)
        val event = UsageEvents.Event()
        var foregroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.packageName
            }
        }

        return foregroundApp
    }

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("locked_package", packageName)
        }
        startActivity(intent)
    }

    /**
     * Called to refresh the locked apps list (e.g. after user changes selection).
     */
    fun refreshLockedApps() {
        serviceScope.launch {
            lockedApps = preferencesManager.getLockedApps().first()
            Log.d(TAG, "Refreshed locked apps: $lockedApps")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors locked apps"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SignLock Active")
            .setContentText("Protecting your apps")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "Service destroyed")
    }
}
