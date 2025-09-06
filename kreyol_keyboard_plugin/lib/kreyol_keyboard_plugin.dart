
import 'kreyol_keyboard_plugin_platform_interface.dart';

class KreyolKeyboardPlugin {
  Future<String?> getPlatformVersion() {
    return KreyolKeyboardPluginPlatform.instance.getPlatformVersion();
  }
}
