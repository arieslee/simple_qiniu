import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:simple_qiniu/simple_qiniu.dart';

void main() {
  const MethodChannel channel = MethodChannel('simple_qiniu');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await SimpleQiniu.platformVersion, '42');
  });
}
