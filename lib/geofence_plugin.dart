import 'dart:async';

import 'package:flutter/services.dart';

export 'package:geofence_plugin/src/geofencer.dart'
    hide geofenceEventToInt, intToGeofenceEvent;
export 'package:geofence_plugin/src/location.dart' hide locationFromList;
export 'package:geofence_plugin/src/platform_settings.dart'
    hide platformSettingsToArgs;

class GeofencePlugin {
  static const MethodChannel _channel = const MethodChannel('geofence_plugin');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
