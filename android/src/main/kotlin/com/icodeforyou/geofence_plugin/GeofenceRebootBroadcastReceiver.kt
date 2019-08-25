package com.icodeforyou.geofence_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GeofencingRebootBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            GeofencePlugin.reRegisterAfterReboot(context)
        }
    }
}