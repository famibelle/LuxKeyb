import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'kreyol_keyboard_plugin_method_channel.dart';

abstract class KreyolKeyboardPluginPlatform extends PlatformInterface {
  /// Constructs a KreyolKeyboardPluginPlatform.
  KreyolKeyboardPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static KreyolKeyboardPluginPlatform _instance = MethodChannelKreyolKeyboardPlugin();

  /// The default instance of [KreyolKeyboardPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelKreyolKeyboardPlugin].
  static KreyolKeyboardPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [KreyolKeyboardPluginPlatform] when
  /// they register themselves.
  static set instance(KreyolKeyboardPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
