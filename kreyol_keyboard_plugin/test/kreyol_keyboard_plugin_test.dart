import 'package:flutter_test/flutter_test.dart';
import 'package:kreyol_keyboard_plugin/kreyol_keyboard_plugin.dart';
import 'package:kreyol_keyboard_plugin/kreyol_keyboard_plugin_platform_interface.dart';
import 'package:kreyol_keyboard_plugin/kreyol_keyboard_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockKreyolKeyboardPluginPlatform
    with MockPlatformInterfaceMixin
    implements KreyolKeyboardPluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final KreyolKeyboardPluginPlatform initialPlatform = KreyolKeyboardPluginPlatform.instance;

  test('$MethodChannelKreyolKeyboardPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelKreyolKeyboardPlugin>());
  });

  test('getPlatformVersion', () async {
    KreyolKeyboardPlugin kreyolKeyboardPlugin = KreyolKeyboardPlugin();
    MockKreyolKeyboardPluginPlatform fakePlatform = MockKreyolKeyboardPluginPlatform();
    KreyolKeyboardPluginPlatform.instance = fakePlatform;

    expect(await kreyolKeyboardPlugin.getPlatformVersion(), '42');
  });
}
