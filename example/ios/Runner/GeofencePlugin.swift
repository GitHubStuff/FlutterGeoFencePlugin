//
//  GeofencePlugin.swift
//  Runner
//
//  Created by Steven Smith
//

import Foundation
import CoreLocation
import Flutter


enum EventType:Int {
    case enterFence = 1
    case exitFence = 2
}

class GeofencePlugin: NSObject {
    private let ChannelNameCallback = "plugins.flutter.io/geofencing_plugin_background"
    private let ChannelNameMain = "plugins.flutter.io/geofencing_plugin"
    private static let GeofenceIsolate = "GeoFenceIsolate"
    private let InitalizeService = "GeofencingPlugin.initializeService"
    private let InitalizedTheService = "GeofencingPlugin.initialized"
    private let KeyEventType = "event_type"
    private let KeyRegionKey = "region"
    private let RegisterGeoFence = "GeofencingPlugin.registerGeofence"
    private let RemoveGeoFence = "GeofencingPlugin.removeGeofence"
    private let UserDefaultKeyCallbackHandle = "callback_handle"
    private let UserDefaultKeyCallbackMap = "callback_mapping_for_geofence_region"
    
    static var instance: GeofencePlugin? = nil
    private static var plugins: FlutterPluginRegistrantCallback? = nil
    private static var initalized = false;
    
    private let callbackChannel: FlutterMethodChannel
    private var eventQueue = [Dictionary<String, Any>]()
    private let headlessRunner = FlutterEngine(name: GeofenceIsolate, project: nil, allowHeadlessExecution: true)
    private let locationManager = CLLocationManager()
    private let mainChannel: FlutterMethodChannel
    private let registrar: FlutterPluginRegistrar
    
    static func register(with registrar: FlutterPluginRegistrar) {
        if instance == nil {
            instance = GeofencePlugin(registrar: registrar)
            registrar.addApplicationDelegate(instance!)
        }
    }
    
    static func setPluginRegistrantCallback(_ callback: @escaping FlutterPluginRegistrantCallback) {
        plugins = callback
    }
    
    init(registrar: FlutterPluginRegistrar) {
        locationManager.requestAlwaysAuthorization()
        locationManager.allowsBackgroundLocationUpdates = true
        self.registrar = registrar
        mainChannel = FlutterMethodChannel(name: ChannelNameMain,
                                           binaryMessenger: registrar.messenger())
        callbackChannel = FlutterMethodChannel(name:ChannelNameCallback,
                                               binaryMessenger: headlessRunner!)
        super.init()
        locationManager.delegate = self;
        self.registrar.addMethodCallDelegate(self, channel: mainChannel)
    }
}

extension GeofencePlugin {
    // MARK: handle
    internal func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let arguements:[Any] = (call.arguments as? [Any])!
        switch call.method {
        //
        case InitalizeService:
            assert(arguements.count == 1, "Invalid argument count for geofencing")
            guard let handle = arguements[0] as? Int64 else {
                assertionFailure("Could not cast arguement \(arguements[0]) to Int64")
                result(FlutterMethodNotImplemented)
                return
            }
            startFenceService(handle: handle)
            result(true)
            
        //
        case InitalizedTheService:
            GeofencePlugin.initalized = true
            // Send any backlogged events if the app was backgrounded
            while (eventQueue.count > 0) {
                guard let event = eventQueue.first else {
                    assertionFailure("Cannot get eventQueue item")
                    result(FlutterMethodNotImplemented)
                    return
                }
                eventQueue.remove(at: 0)
                guard let region = event[KeyRegionKey] as? CLRegion else {
                    assertionFailure("Cannot get region from \(event)")
                    result(FlutterMethodNotImplemented)
                    return
                }
                guard let eventType = event[KeyEventType] as? EventType else {
                    assertionFailure("Cannot get eventyType from \(event)")
                    result(FlutterMethodNotImplemented)
                    return
                }
                sendLocationEvent(region: region, eventType: eventType)
            }
            result(nil)
            
        //
        case RegisterGeoFence:
            registerGeoFence(arguements: arguements)
            result(true)
            
        //
        case RemoveGeoFence:
            let removeStatus = removeGeoFence(arguement: arguements)
            result(removeStatus)
            
        //
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
}

// MARK: Location Manager
extension GeofencePlugin: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        if GeofencePlugin.initalized {  //Send the event
            sendLocationEvent(region: region, eventType: .enterFence)
        } else {  //Queue event until the service is initialized
            eventQueue.append([KeyRegionKey: region, KeyEventType: EventType.enterFence])
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        if GeofencePlugin.initalized { //Send the event
            sendLocationEvent(region: region, eventType: EventType.exitFence)
        } else {
            eventQueue.append([KeyRegionKey: region, KeyEventType: EventType.exitFence])
        }
    }
    
    func locationManager(_ manager: CLLocationManager,
                         monitoringDidFailFor region: CLRegion?,
                         withError error: Error) {
        print("Monitoring failed: \(error)")
    }
}

extension GeofencePlugin {
    
    // Region Mapping
    private func getRegionCallbackMapping() -> [String:Any] {
        let callbackDictionary = UserDefaults.standard.dictionary(forKey: UserDefaultKeyCallbackMap)
        return callbackDictionary ?? [:]
    }
    
    private func setRegionCallbackMapping(map: [String:Any]) {
        UserDefaults.standard.set(map, forKey: UserDefaultKeyCallbackMap)
    }
    
    // Handle number
    func getCallbackDispatchHandle() -> Int64 {
        let handle = UserDefaults.standard.integer(forKey: UserDefaultKeyCallbackHandle)
        return Int64(handle)
    }
    
    private func setCallbackDispatchHandle(handle: Int64) {
        UserDefaults.standard.set(handle, forKey: UserDefaultKeyCallbackHandle)
    }
    
    // Handle Id
    private func getCallbackDispatchHandleFor(regionId: String) -> Int64 {
        let map = getRegionCallbackMapping()
        return map[regionId] as? Int64 ?? 0
    }
    
    private func setCallbackDispatchHandler(_ handle: Int64, forRegionId regionId: String) {
        var map = getRegionCallbackMapping()
        map[regionId] = handle
        setRegionCallbackMapping(map: map)
    }
    
    private func removeCallbackHandlerFor(regionId: String) {
        var map = getRegionCallbackMapping()
        map.removeValue(forKey: regionId)
        setRegionCallbackMapping(map: map)
    }
    
    private func removeGeoFence(arguement: [Any]) -> Bool {
        guard let identifer = arguement[0] as? String else {
            assertionFailure("Cannot get identifier from \(arguement)")
            return false;
        }
        for region in locationManager.monitoredRegions where (region.identifier == identifer)  {
            locationManager.stopMonitoring(for: region)
            removeCallbackHandlerFor(regionId: identifer)
            return true
        }
        return false
    }
}

extension GeofencePlugin: FlutterPlugin {
    
    private func registerGeoFence(arguements:[Any]) {
        guard let handler = arguements[0] as? Int64 else {
            assertionFailure("Cannot convert \(arguements)[0] to int64")
            return
        }
        guard let identifier = arguements[1] as? String else {
            assertionFailure("Cannot covert arguement[1] to string")
            return
        }
        guard let latitude = arguements[2] as? Double else {
            assertionFailure("Cannot get latitude")
            return
        }
        guard let longitude = arguements[3] as? Double else {
            assertionFailure("Cannot get longitude")
            return
        }
        guard let radius = arguements[4] as? Double else {
            assertionFailure("Cannot get radius")
            return
        }
        guard let triggerMask = arguements[5] as? Int64 else {
            assertionFailure("Cannot get trigger mask")
            return
        }
        
        let region = CLCircularRegion(center: CLLocationCoordinate2DMake(latitude, longitude),
                                      radius: radius,
                                      identifier: identifier)
        region.notifyOnEntry = ((triggerMask & 0x1) != 0)
        region.notifyOnExit = ((triggerMask & 0x2) != 0)
        
        setCallbackDispatchHandler(handler, forRegionId: region.identifier)
        locationManager.startMonitoring(for: region)
    }
    
    func startFenceService(handle: Int64) {
        setCallbackDispatchHandle(handle: handle)
        let info: FlutterCallbackInformation? = FlutterCallbackCache.lookupCallbackInformation(handle)
        guard let callbackInformation = info else {
            assertionFailure("lookup returned nil")
            return
        }
        let entryPoint = callbackInformation.callbackName
        let uri = callbackInformation.callbackLibraryPath
        guard let runner = headlessRunner else {
            assertionFailure("headlessRunner is nil")
            return
        }
        runner.run(withEntrypoint: entryPoint, libraryURI: uri)
        guard let plugins = GeofencePlugin.plugins else {
            assertionFailure("Failed to set plugins!!")
            return
        }
        plugins(runner)
        registrar.addMethodCallDelegate(self, channel: callbackChannel)
    }
    
    private func sendLocationEvent(region: CLRegion, eventType: EventType) {
        assert(region.isKind(of: CLCircularRegion.self), "Region must be CLCircularRegion")
        if let center = (region as? CLCircularRegion)?.center {
            let handle = getCallbackDispatchHandleFor(regionId: region.identifier)
            callbackChannel.invokeMethod("", arguments: [handle, region.identifier, center.latitude, center.longitude, eventType.rawValue])
        }
    }
    
    private func getCallbackHandleForRegion(_ region: String) -> Int {
        let map = getRegionCallbackMapping()
        if let handle = map[region] as? Int {
            return handle
        }
        return 0
    }
}
