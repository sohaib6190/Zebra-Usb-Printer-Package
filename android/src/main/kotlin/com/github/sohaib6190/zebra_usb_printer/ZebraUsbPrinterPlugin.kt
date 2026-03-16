package com.github.sohaib6190.zebra_usb_printer

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class ZebraUsbPrinterPlugin: FlutterPlugin, MethodChannel.MethodCallHandler {

    private lateinit var context: Context
    private lateinit var channel: MethodChannel

    companion object {
        private const val CHANNEL = "zebra_usb"
        private const val ACTION_USB_PERMISSION = "com.github.sohaib6190.zebra_usb_printer.USB_PERMISSION"
        private const val TAG = "ZebraPrint"
        // Zebra Technologies USB Vendor ID (decimal 2655 = 0x0A5F)
        private const val ZEBRA_VENDOR_ID = 2655
    }

    private var pendingResult: MethodChannel.Result? = null
    private var pendingZpl: String = ""
    private var receiverRegistered = false

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        safeUnregisterReceiver()
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "printLabel" -> {
                val zpl = call.argument<String>("zpl") ?: ""
                startPrint(zpl, result)
            }
            "getPrinterStatus" -> checkPrinterStatus(result)
            else -> result.notImplemented()
        }
    }

    private fun checkPrinterStatus(result: MethodChannel.Result) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val deviceList = usbManager.deviceList ?: run {
            Log.w(TAG, "getPrinterStatus: deviceList is null")
            result.success("NOT_FOUND")
            return
        }

        val zebraDevice: UsbDevice? = deviceList.values
            .firstOrNull { it.vendorId == ZEBRA_VENDOR_ID }

        if (zebraDevice == null) {
            Log.w(TAG, "getPrinterStatus: No Zebra USB device found")
            result.success("NOT_FOUND")
            return
        }

        if (!usbManager.hasPermission(zebraDevice)) {
            Log.d(TAG, "getPrinterStatus: Permission not granted for ${zebraDevice.deviceName}")
            result.success("PERMISSION_REQUIRED")
            return
        }

        // Try opening a raw connection to verify the device is reachable
        val conn = usbManager.openDevice(zebraDevice)
        if (conn == null) {
            Log.w(TAG, "getPrinterStatus: Could not open device")
            result.success("ERROR: Could not open device")
            return
        }
        conn.close()
        Log.d(TAG, "getPrinterStatus: Printer is READY")
        result.success("READY")
    }

    private fun startPrint(zpl: String, result: MethodChannel.Result) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        // Null-safe device list — on some Android versions deviceList can return null
        val deviceList = usbManager.deviceList ?: run {
            Log.w(TAG, "startPrint: deviceList is null")
            result.error("NO_PRINTER", "No Zebra USB printer found", null)
            return
        }

        val zebraDevice: UsbDevice? = deviceList.values
            .firstOrNull { it.vendorId == ZEBRA_VENDOR_ID }

        if (zebraDevice == null) {
            Log.w(TAG, "No Zebra USB device found. Connected devices: ${deviceList.values.map { "VID=${it.vendorId} PID=${it.productId} name=${it.deviceName}" }}")
            result.error("NO_PRINTER", "No Zebra USB printer found", null)
            return
        }

        Log.d(TAG, "Found Zebra device: ${zebraDevice.deviceName} VID=${zebraDevice.vendorId}")

        if (!usbManager.hasPermission(zebraDevice)) {
            Log.d(TAG, "Requesting USB permission for device: ${zebraDevice.deviceName}")
            pendingResult = result
            pendingZpl = zpl
            requestUsbPermission(usbManager, zebraDevice)
        } else {
            Log.d(TAG, "Already have USB permission, printing directly")
            printZpl(zebraDevice, zpl, result)
        }
    }

    private fun requestUsbPermission(usbManager: UsbManager, device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION).apply { setPackage(context.packageName) },
            flags
        )

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbPermissionReceiver, filter)
            }
            receiverRegistered = true
        }

        usbManager.requestPermission(device, permissionIntent)
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION != intent.action) return

            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            safeUnregisterReceiver()

            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                Log.d(TAG, "USB permission granted")
                device?.let {
                    printZpl(it, pendingZpl, pendingResult!!)
                } ?: pendingResult?.error("NO_PRINTER", "Device not found after permission grant", null)
            } else {
                Log.w(TAG, "USB permission denied by user")
                pendingResult?.error("PERMISSION_DENIED", "USB permission denied by user", null)
            }
            pendingResult = null
            pendingZpl = ""
        }
    }

  private fun printZpl(device: UsbDevice, zpl: String, result: MethodChannel.Result) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    Thread {
        var connection: UsbDeviceConnection? = null
        var targetInterface: UsbInterface? = null
        try {
            connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "printZpl: openDevice returned null")
                result.error("PRINT_ERROR", "Could not open USB device", null)
                return@Thread
            }

            // Find the bulk-OUT endpoint across all interfaces
            var bulkOutEndpoint: UsbEndpoint? = null

            outer@ for (i in 0 until device.interfaceCount) {
    val usbInterface = device.getInterface(i)
    Log.d(TAG, "Interface $i class=${usbInterface.interfaceClass}")
    
    if (usbInterface.interfaceClass != UsbConstants.USB_CLASS_PRINTER) continue

    for (j in 0 until usbInterface.endpointCount) {
        val ep = usbInterface.getEndpoint(j)
        Log.d(TAG, "Endpoint $j type=${ep.type} dir=${ep.direction}")
        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
            ep.direction == UsbConstants.USB_DIR_OUT
        ) {
            bulkOutEndpoint = ep
            targetInterface = usbInterface
            break@outer
        }
    }
}

            if (bulkOutEndpoint == null || targetInterface == null) {
                Log.e(TAG, "printZpl: No bulk-OUT endpoint found")
                result.error("PRINT_ERROR", "No bulk-OUT endpoint found on printer", null)
                return@Thread
            }

            // KEY FIX 1: force=true detaches any kernel driver holding the interface
            val claimed = connection.claimInterface(targetInterface, true)
            if (!claimed) {
                Log.e(TAG, "printZpl: Could not claim interface")
                result.error("PRINT_ERROR", "Could not claim USB interface", null)
                return@Thread
            }

            // KEY FIX 2: small delay after claiming so the device is ready
            Thread.sleep(500)

           val data = zpl.toByteArray(Charsets.US_ASCII)
            

            // KEY FIX 3: chunk large ZPL into 16 KB pieces — Zebra printers
            // often reject a single large bulk transfer
            val chunkSize = 16384
            var offset = 0
            var allOk = true

            while (offset < data.size) {
                val length = minOf(chunkSize, data.size - offset)
                val transferred = connection.bulkTransfer(
                    bulkOutEndpoint, data, offset, length, 5000
                )
                if (transferred < 0) {
                    Log.e(TAG, "printZpl: bulkTransfer failed at offset $offset, returned $transferred")
                    allOk = false
                    break
                }
                Log.d(TAG, "printZpl: Sent $transferred bytes (offset=$offset)")
                offset += transferred
                // small inter-chunk delay to avoid overwhelming the printer buffer
                if (offset < data.size) Thread.sleep(20)
            }

            connection.releaseInterface(targetInterface)
            targetInterface = null  // mark as released so finally doesn't double-release

            if (allOk) {
                Log.d(TAG, "printZpl: All ${data.size} bytes sent successfully")
                result.success("Printed Successfully")
            } else {
                result.error("PRINT_ERROR", "USB bulk transfer failed", null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "printZpl: Exception: ${e.message}")
            result.error("PRINT_ERROR", e.message, null)
        } finally {
            try {
                targetInterface?.let { connection?.releaseInterface(it) }
            } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
        }
    }.start()
}

    private fun safeUnregisterReceiver() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(usbPermissionReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }
}
