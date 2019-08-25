import UIKit
import Flutter

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
    override func application(_ application: UIApplication,
                              didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        if launchOptions?[UIApplication.LaunchOptionsKey.location] != nil {
            if let handle = GeofencePlugin.instance?.getCallbackDispatchHandle() {
                GeofencePlugin.instance!.startFenceService(handle: handle)
            } else {
                assertionFailure("Cannot get handle")
            }
        }
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
