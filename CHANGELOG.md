## 0.0.1

* Initial release.
* Support for printing ZPL labels to Zebra USB printers on Android.
* `printLabel(String zpl)` – sends a ZPL string to the connected Zebra USB printer.
* `getPrinterStatus()` – returns the current printer status (`READY`, `NOT_FOUND`, `PERMISSION_REQUIRED`, or `ERROR: <message>`).
* Automatic USB permission request flow when permission has not yet been granted.
