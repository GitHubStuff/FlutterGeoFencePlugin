#import "GeofencePlugin.h"
#import <geofence_plugin/geofence_plugin-Swift.h>

@implementation GeofencePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftGeofencePlugin registerWithRegistrar:registrar];
}
@end
