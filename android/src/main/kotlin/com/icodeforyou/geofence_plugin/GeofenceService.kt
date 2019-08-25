package com.icodeforyou.geofence_plugin

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.FlutterCallbackInformation
import io.flutter.view.FlutterMain
import io.flutter.view.FlutterNativeView
import io.flutter.view.FlutterRunArguments
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingEvent.*

class GeofenceService : MethodChannel.MethodCallHandler, JobIntentService() {
    private val queue = ArrayDeque<List<Any>>()
    private lateinit var backgroundChannel: MethodChannel
    private lateinit var context: Context

    companion object {
        @JvmStatic
        private val TAG = "GeofencingService"
        @JvmStatic
        private val JOB_ID = UUID.randomUUID().mostSignificantBits.toInt()
        @JvmStatic
        private var backgroundFlutterView: FlutterNativeView? = null
        @JvmStatic
        private val serviceStarted = AtomicBoolean(false)

        @JvmStatic
        private lateinit var pluginRegistrantCallback: PluginRegistry.PluginRegistrantCallback

        @JvmStatic
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, GeofenceService::class.java, JOB_ID, work)
        }

        @JvmStatic
        fun setPluginRegistrant(callback: PluginRegistry.PluginRegistrantCallback) {
            pluginRegistrantCallback = callback
        }
    }

    private fun startGeofencingService(context: Context) {
        synchronized(serviceStarted) {
            this.context = context
            if (backgroundFlutterView == null) {
                val callbackHandle = context.getSharedPreferences(
                        GeofencePlugin.SHARED_PREFERENCES_KEY,
                        Context.MODE_PRIVATE)
                        .getLong(GeofencePlugin.CALLBACK_DISPATCHER_HANDLE_KEY, 0)

                val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
                if (callbackInfo == null) {
                    Log.e(TAG, "Fatal: failed to find callback")
                    return
                }
                Log.i(TAG, "Starting GeofencingService...")
                backgroundFlutterView = FlutterNativeView(context, true)

                val registry = backgroundFlutterView!!.pluginRegistry
                pluginRegistrantCallback.registerWith(registry)
                val args = FlutterRunArguments()
                args.bundlePath = FlutterMain.findAppBundlePath(context)
                args.entrypoint = callbackInfo.callbackName
                args.libraryPath = callbackInfo.callbackLibraryPath

                backgroundFlutterView!!.runFromBundle(args)
                GeofenceHolderService.setBackgroundFlutterView(backgroundFlutterView)
            }
        }
        backgroundChannel = MethodChannel(backgroundFlutterView,
                "plugins.flutter.io/geofencing_plugin_background")
        backgroundChannel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            //
            "GeofencingService.initialized" -> {
                synchronized(serviceStarted) {
                    while (!queue.isEmpty()) {
                        backgroundChannel.invokeMethod("", queue.remove())
                    }
                    serviceStarted.set(true)
                }
            }
            //
            "GeofencingService.promoteToForeground" -> {
                context.startForegroundService(Intent(context, GeofenceHolderService::class.java))
            }
            //
            "GeofencingService.demoteToBackground" -> {
                val intent = Intent(context, GeofenceHolderService::class.java)
                intent.setAction(GeofenceHolderService.ACTION_SHUTDOWN)
                context.startForegroundService(intent)
            }
            else -> result.notImplemented()
        }
        result.success(null)
    }

    override fun onCreate() {
        super.onCreate()
        startGeofencingService(this)
    }

    override fun onHandleWork(intent: Intent) {
        val callbackHandle = intent.getLongExtra(GeofencePlugin.CALLBACK_HANDLE_KEY, 0)
        val geofencingEvent = fromIntent(intent)
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Get the geofences that were triggered. A single event can trigger
        // multiple geofences.
        val triggeringGeofences = geofencingEvent.triggeringGeofences.map {
            it.requestId
        }

        val location = geofencingEvent.triggeringLocation
        val locationList = listOf(location.latitude,
                location.longitude)
        val geofenceUpdateList = listOf(callbackHandle,
                triggeringGeofences,
                locationList,
                geofenceTransition)

        synchronized(serviceStarted) {
            if (!serviceStarted.get()) {
                // Queue up geofencing events while background isolate is starting
                queue.add(geofenceUpdateList)
            } else {
                // Callback method name is intentionally left blank.
                backgroundChannel.invokeMethod("", geofenceUpdateList)
            }
        }
    }
}