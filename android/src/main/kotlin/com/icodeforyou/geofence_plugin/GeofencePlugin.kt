package com.icodeforyou.geofence_plugin

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.json.JSONArray

class GeofencePlugin(context: Context, activity: Activity?) : MethodCallHandler {
  private val context = context
  private val activity = activity
  private val geofencingClient = LocationServices.getGeofencingClient(context)

  companion object {
    @JvmStatic
    private val TAG = "GeofencingPlugin"

    @JvmStatic
    val SHARED_PREFERENCES_KEY = "geofencing_plugin_cache"
    @JvmStatic
    val PERSISTENT_GEOFENCES_KEY = "persistent_geofences"
    @JvmStatic
    val PERSISTENT_GEOFENCES_IDS = "persistent_geofences_ids"
    @JvmStatic
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    @JvmStatic
    private val sGeofenceCacheLock = Object()

    @JvmStatic
    val CALLBACK_HANDLE_KEY = "callback_handle"

    @JvmStatic
    val CALLBACK_DISPATCHER_HANDLE_KEY = "callback_dispatch_handler"

    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val plugin = GeofencePlugin(registrar.context(), registrar.activity())
      val channel = MethodChannel(registrar.messenger(), "geofence_plugin")
      channel.setMethodCallHandler(plugin)
    }

    @JvmStatic
    fun reRegisterAfterReboot(context: Context) {
      synchronized(sGeofenceCacheLock) {
        var p = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        var persistentGeofences = p.getStringSet(PERSISTENT_GEOFENCES_IDS, null)
        if (persistentGeofences == null) {
          return
        }
        for (id in persistentGeofences) {
          val gfJson = p.getString(getPersistentGeofenceKey(id), null)
          if (gfJson == null) {
            continue
          }
          val gfArgs = JSONArray(gfJson)
          val list = ArrayList<Object>()
          for (i in 0 until gfArgs.length()) {
            list.add(gfArgs.get(i) as Object)
          }
          val geoClient = LocationServices.getGeofencingClient(context)
          registerGeofence(context, geoClient, list, null, false)
        }
      }
    }

    @JvmStatic
    private fun registerGeofence(context: Context,
                                 geofencingClient: GeofencingClient,
                                 args: ArrayList<*>?,
                                 result: Result?,
                                 cache: Boolean) {
      val callbackHandle = args!![0] as Long
      val id = args[1] as String
      val lat = args[2] as Double
      val long = args[3] as Double
      val radius = (args[4] as Number).toFloat()
      val fenceTriggers = args[5] as Int
      val initialTriggers = args[6] as Int
      val expirationDuration = (args[7] as Int).toLong()
      val loiteringDelay = args[8] as Int
      val notificationResponsiveness = args[9] as Int
      val geofence = Geofence.Builder()
              .setRequestId(id)
              .setCircularRegion(lat, long, radius)
              .setTransitionTypes(fenceTriggers)
              .setLoiteringDelay(loiteringDelay)
              .setNotificationResponsiveness(notificationResponsiveness)
              .setExpirationDuration(expirationDuration)
              .build()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
              (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                      == PackageManager.PERMISSION_DENIED)) {
        val msg = "'registerGeofence' requires the ACCESS_FINE_LOCATION permission."
        Log.w(TAG, msg)
        result?.error(msg, null, null)
      }
      geofencingClient.addGeofences(getGeofencingRequest(geofence, initialTriggers),
              getGeofencePendingIndent(context, callbackHandle))?.run {
        addOnSuccessListener {
          Log.i(TAG, "Successfully added geofence")
          if (cache) {
            addGeofenceToCache(context, id, args)
          }
          result?.success(true)
        }
        addOnFailureListener {
          Log.e(TAG, "Failed to add geofence: $it")
          result?.error(it.toString(), null, null)
        }
      }
    }

    @JvmStatic
    private fun addGeofenceToCache(context: Context, id: String, args: ArrayList<*>) {
      synchronized(sGeofenceCacheLock) {
        var p = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        var obj = JSONArray(args)
        var persistentGeofences = p.getStringSet(PERSISTENT_GEOFENCES_IDS, null)
        if (persistentGeofences == null) {
          persistentGeofences = HashSet<String>()
        }

        persistentGeofences.add(id)
        context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PERSISTENT_GEOFENCES_IDS, persistentGeofences)
                .putString(getPersistentGeofenceKey(id), obj.toString())
                .apply()
      }
    }

    @JvmStatic
    private fun initializeService(context: Context, args: ArrayList<*>?) {
      Log.d(TAG, "Initializing GeofencingService")
      val callbackHandle = args!![0] as Long
      context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
              .edit()
              .putLong(CALLBACK_DISPATCHER_HANDLE_KEY, callbackHandle)
              .apply()
    }

    @JvmStatic
    private fun getGeofencingRequest(geofence: Geofence, initialTrigger: Int): GeofencingRequest {
      return GeofencingRequest.Builder().apply {
        setInitialTrigger(initialTrigger)
        addGeofence(geofence)
      }.build()
    }

    @JvmStatic
    private fun getPersistentGeofenceKey(id: String): String {
      return "persistent_geofence/" + id
    }
    @JvmStatic
    private fun removeGeofence(context: Context,
                               geofencingClient: GeofencingClient,
                               args: ArrayList<*>?,
                               result: Result) {
      val ids = listOf(args!![0] as String)
      geofencingClient.removeGeofences(ids).run {
        addOnSuccessListener {
          result.success(true)
          for (id in ids) {
            removeGeofenceFromCache(context, id)
          }
        }
        addOnFailureListener {
          result.error(it.toString(), null, null)
        }
      }
    }

    @JvmStatic
    private fun removeGeofenceFromCache(context: Context, id: String) {
      synchronized(sGeofenceCacheLock) {
        var p = context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        var persistentGeofences = p.getStringSet(PERSISTENT_GEOFENCES_IDS, null)
        if (persistentGeofences == null) {
          return
        }
        persistentGeofences.remove(id)
        p.edit()
                .remove(getPersistentGeofenceKey(id))
                .putStringSet(PERSISTENT_GEOFENCES_IDS, persistentGeofences)
                .apply()
      }
    }
    @JvmStatic
    private fun getGeofencePendingIndent(context: Context, callbackHandle: Long): PendingIntent {
      val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
              .putExtra(CALLBACK_HANDLE_KEY, callbackHandle)
      return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }


  }


  override fun onMethodCall(call: MethodCall, result: Result) {
    val args = call.arguments() as? ArrayList<*>
    when(call.method) {
      "GeofencingPlugin.initializeService" -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          activity?.requestPermissions(REQUIRED_PERMISSIONS, 12312)
        }
        initializeService(context, args)
        result.success(true)
      }

      "GeofencingPlugin.registerGeofence" -> registerGeofence(context,
              geofencingClient,
              args,
              result,
              true)

      "GeofencingPlugin.removeGeofence" -> removeGeofence(context,
              geofencingClient,
              args,
              result)

      "getPlatformVersion" -> result.success(
              "Android ${Build.VERSION.RELEASE}")
      else -> result.notImplemented()
    }
  }
}
