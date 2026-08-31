import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hotkey_manager/hotkey_manager.dart';
import 'package:path/path.dart' as p;
import 'package:screen_capturer/screen_capturer.dart';
import 'package:tray_manager/tray_manager.dart';
import 'package:window_manager/window_manager.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await windowManager.ensureInitialized();
  await hotKeyManager.unregisterAll();

  const options = WindowOptions(
    size: Size(320, 160),
    center: true,
    backgroundColor: Colors.transparent,
    skipTaskbar: true,
    titleBarStyle: TitleBarStyle.hidden,
    title: 'KADR',
  );

  windowManager.waitUntilReadyToShow(options, () async {
    await windowManager.setAsFrameless();
    await windowManager.setSkipTaskbar(true);
    await windowManager.hide();
  });

  runApp(const KadrMacApp());
}

class KadrMacApp extends StatefulWidget {
  const KadrMacApp({super.key});

  @override
  State<KadrMacApp> createState() => _KadrMacAppState();
}

class _KadrMacAppState extends State<KadrMacApp> with TrayListener {
  HotKey? _captureHotKey;
  bool _capturing = false;

  @override
  void initState() {
    super.initState();
    trayManager.addListener(this);
    unawaited(_bootstrap());
  }

  Future<void> _bootstrap() async {
    final exeDir = File(Platform.resolvedExecutable).parent.path;
    final iconPath = p.normalize(p.join(
      exeDir,
      '..',
      'Frameworks',
      'App.framework',
      'Resources',
      'flutter_assets',
      'assets',
      'menu_icon.png',
    ));

    await trayManager.setIcon(iconPath, isTemplate: true);
    await trayManager.setToolTip('KADR');
    await trayManager.setContextMenu(Menu(items: [
      MenuItem(key: 'capture', label: 'Capture Region    ⌥⇧2'),
      MenuItem.separator(),
      MenuItem(key: 'folder', label: 'Open KADR Screenshots'),
      MenuItem.separator(),
      MenuItem(key: 'quit', label: 'Quit KADR'),
    ]));

    _captureHotKey = HotKey(
      key: PhysicalKeyboardKey.digit2,
      modifiers: [HotKeyModifier.alt, HotKeyModifier.shift],
      scope: HotKeyScope.system,
    );
    await hotKeyManager.register(
      _captureHotKey!,
      keyDownHandler: (_) => unawaited(_captureRegion()),
    );
  }

  Future<Directory> _captureDirectory() async {
    final home = Platform.environment['HOME'] ?? Directory.current.path;
    final dir = Directory(p.join(home, 'Pictures', 'KADR', 'Screenshots'));
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  String _stamp(DateTime now) =>
      '${now.year.toString().padLeft(4, '0')}${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}-'
      '${now.hour.toString().padLeft(2, '0')}${now.minute.toString().padLeft(2, '0')}${now.second.toString().padLeft(2, '0')}';

  Future<void> _captureRegion() async {
    if (_capturing) return;
    _capturing = true;
    try {
      await windowManager.hide();
      final dir = await _captureDirectory();
      final imagePath = p.join(dir.path, 'KADR-${_stamp(DateTime.now())}.png');
      await screenCapturer.capture(
        mode: CaptureMode.region,
        imagePath: imagePath,
        copyToClipboard: true,
      );
    } catch (error, stack) {
      debugPrint('KADR macOS capture failed: $error\n$stack');
    } finally {
      _capturing = false;
    }
  }

  Future<void> _openFolder() async {
    final dir = await _captureDirectory();
    await Process.run('/usr/bin/open', [dir.path]);
  }

  @override
  void onTrayIconMouseDown() => unawaited(trayManager.popUpContextMenu());

  @override
  void onTrayMenuItemClick(MenuItem item) {
    switch (item.key) {
      case 'capture':
        unawaited(_captureRegion());
        break;
      case 'folder':
        unawaited(_openFolder());
        break;
      case 'quit':
        unawaited(_quit());
        break;
    }
  }

  Future<void> _quit() async {
    await hotKeyManager.unregisterAll();
    await trayManager.destroy();
    exit(0);
  }

  @override
  void dispose() {
    trayManager.removeListener(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => const MaterialApp(
        debugShowCheckedModeBanner: false,
        home: SizedBox.shrink(),
      );
}
