import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kreyol_keyboard_plugin/kreyol_keyboard_plugin_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelKreyolKeyboardPlugin platform = MethodChannelKreyolKeyboardPlugin();
  const MethodChannel channel = MethodChannel('kreyol_keyboard_plugin');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
