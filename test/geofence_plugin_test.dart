import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geofence_plugin/geofence_plugin.dart';

void main() {
  const MethodChannel channel = MethodChannel('geofence_plugin');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await GeofencePlugin.platformVersion, '42');
  });
}
