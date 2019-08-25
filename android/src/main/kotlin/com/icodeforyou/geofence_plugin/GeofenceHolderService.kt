package com.icodeforyou.geofence_plugin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.flutter.view.FlutterNativeView

//import io.flutter.view.FlutterNativeView

class GeofenceHolderService: Service() {
    companion object {
        @JvmStatic
        val ACTION_SHUTDOWN = "SHUTDOWN"
        @JvmStatic
        private val WAKELOCK_TAG = "GeofenceHolderService::WAKE_LOCK"
        @JvmStatic
        private var backgroundFlutterView: FlutterNativeView? = null

        @JvmStatic
        fun setBackgroundFlutterView(view: FlutterNativeView?) {
            backgroundFlutterView = view
        }
    }

    override fun onBind(p0: Intent) : IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "geofencing_plugin_channel"
        val channel = NotificationChannel(channelId,
                "Flutter Geofencing Plugin",
                NotificationManager.IMPORTANCE_LOW)
        val imageId = resources.getIdentifier("ic_launcher", "mipmap", packageName)

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Almost home!")
                .setContentText("Within 1KM of home. Fine location tracking enabled.")
                .setSmallIcon(imageId)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L /*10 minutes*/)
            }
        }
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int) : Int {
        if (intent.action == ACTION_SHUTDOWN) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    if (isHeld) {
                        release()
                    }
                }
            }
            stopForeground(true)
            stopSelf()
        }
        return START_STICKY
    }
}