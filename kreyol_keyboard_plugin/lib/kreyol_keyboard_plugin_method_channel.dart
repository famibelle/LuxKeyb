import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'kreyol_keyboard_plugin_platform_interface.dart';

/// An implementation of [KreyolKeyboardPluginPlatform] that uses method channels.
class MethodChannelKreyolKeyboardPlugin extends KreyolKeyboardPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('kreyol_keyboard_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
