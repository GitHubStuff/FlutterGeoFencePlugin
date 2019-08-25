package com.icodeforyou.geofence_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.flutter.view.FlutterMain

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FlutterMain.ensureInitializationComplete(context, null)
        GeofenceService.enqueueWork(context, intent)
    }
}