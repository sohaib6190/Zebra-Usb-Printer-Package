import 'package:flutter/services.dart';

class ZebraUsbPrinter {
  static const MethodChannel _channel = MethodChannel('zebra_usb');

  /// Sends ZPL string to Zebra USB printer
  static Future<String?> printLabel(String zpl) async {
    final result = await _channel.invokeMethod<String>(
      'printLabel',
      {'zpl': zpl},
    );
    return result;
  }

  /// Returns the current status of the Zebra USB printer.
  ///
  /// Possible return values:
  /// - `"READY"` – Printer found and connection succeeded.
  /// - `"NOT_FOUND"` – No Zebra USB printer detected.
  /// - `"PERMISSION_REQUIRED"` – Printer found but USB permission not yet granted.
  /// - `"ERROR: <message>"` – An error occurred while checking the printer.
  static Future<String?> getPrinterStatus() async {
    final result = await _channel.invokeMethod<String>('getPrinterStatus');
    return result;
  }
}