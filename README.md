# zebra_usb_printer

A Flutter plugin for printing ZPL labels to **Zebra USB printers** on Android.  
Supports sending raw ZPL, checking printer status, and handling USB permission requests automatically.

---

## Features

- 🖨️ Print ZPL labels directly to a Zebra USB printer
- 🔍 Check printer status (`READY`, `NOT_FOUND`, `PERMISSION_REQUIRED`, `ERROR`)
- 🔐 Automatic USB permission request flow

---

## Platform Support

| Android |
|---------|
| ✅      |

> iOS is not supported — Zebra USB printing on iOS requires Apple's MFi program.

---

## Getting Started

### 1. Add the dependency

```yaml
dependencies:
  zebra_usb_printer: ^0.0.1
```

### 2. Android permissions

Add the following to your app's `AndroidManifest.xml` inside `<manifest>`:

```xml
<uses-feature android:name="android.hardware.usb.host" />
```

And inside `<application>`, add a USB device filter to your main `<activity>`:

```xml
<intent-filter>
    <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
</intent-filter>
<meta-data
    android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
    android:resource="@xml/usb_device_filter" />
```

Create `res/xml/usb_device_filter.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-device vendor-id="2655" /> <!-- Zebra Technologies vendor ID -->
</resources>
```

---

## Usage

```dart
import 'package:zebra_usb_printer/zebra_usb_printer.dart';

// Check printer status
final status = await ZebraUsbPrinter.getPrinterStatus();
// Returns: "READY" | "NOT_FOUND" | "PERMISSION_REQUIRED" | "ERROR: <message>"

// Print a ZPL label
final result = await ZebraUsbPrinter.printLabel('^XA^FO50,50^ADN,36,20^FDHello World^FS^XZ');
```

### Printer status values

| Value | Meaning |
|---|---|
| `READY` | Printer found and connection succeeded |
| `NOT_FOUND` | No Zebra USB printer detected |
| `PERMISSION_REQUIRED` | Printer found but USB permission not yet granted |
| `ERROR: <message>` | An error occurred while communicating with the printer |

---

## Notes

- Only **Zebra** printers (USB Vendor ID `0x0A5F` / `2655`) are detected.
- USB permission is requested automatically on first use if not already granted.
- The plugin uses Android's `UsbManager` API directly — no third-party SDKs required at runtime.

---

## License

MIT
