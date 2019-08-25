import 'package:geofence_plugin/src/geofencer.dart';

// Internal.
List<dynamic> platformSettingsToArgs(AndroidGeofencingSettings s) =>
    s._toArgs();

class AndroidGeofencingSettings {
  List<GeofenceEvent> initialTrigger;
  int expirationDuration;
  int loiteringDelay;
  int notificationResponsiveness;

  AndroidGeofencingSettings(
      {this.initialTrigger = const <GeofenceEvent>[GeofenceEvent.enter],
      this.loiteringDelay = 0,
      this.expirationDuration = -1,
      this.notificationResponsiveness = 0});

  List<dynamic> _toArgs() {
    final int initTriggerMask = initialTrigger.fold(
        0, (int trigger, GeofenceEvent e) => (geofenceEventToInt(e) | trigger));
    return <dynamic>[
      initTriggerMask,
      expirationDuration,
      loiteringDelay,
      notificationResponsiveness
    ];
  }
}
