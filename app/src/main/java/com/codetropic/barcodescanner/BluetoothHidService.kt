package com.codetropic.barcodescanner

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.IBinder
import android.util.Log

@SuppressLint("MissingPermission")
class BluetoothHidService : Service() {

    private var hidDevice: BluetoothHidDevice? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hostDevice: BluetoothDevice? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                    SDP_NAME,
                    SDP_DESCRIPTION,
                    SDP_PROVIDER,
                    BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                    HID_REPORT_DESCRIPTOR
                )
                hidDevice?.registerApp(sdpSettings, null, null, mainExecutor, appCallback)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
            }
        }
    }

    private val appCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            if (registered) {
                Log.d(TAG, "HID App registered")
            } else {
                Log.d(TAG, "HID App unregistered")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.d(TAG, "Connection state changed to $state")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                hostDevice = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter?.getProfileProxy(applicationContext, profileListener, BluetoothProfile.HID_DEVICE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val barcode = intent?.getStringExtra("barcode_data")
        if (!barcode.isNullOrEmpty()) {
            sendKeyboardReport(barcode)
        }
        return START_NOT_STICKY
    }

    private fun sendKeyboardReport(text: String) {
        if (hostDevice == null) return

        text.forEach { char ->
            val key = US_KEYBOARD_MAP[char]
            if (key != null) {
                // Press key
                hidDevice?.sendReport(hostDevice, 0, byteArrayOf(key.modifier.toByte(), 0, key.code.toByte(), 0, 0, 0, 0, 0))
                // Release key
                hidDevice?.sendReport(hostDevice, 0, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
            }
        }
        // Press Enter
        hidDevice?.sendReport(hostDevice, 0, byteArrayOf(0, 0, 0x28, 0, 0, 0, 0, 0))
        // Release Enter
        hidDevice?.sendReport(hostDevice, 0, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "BluetoothHidService"
        private const val SDP_NAME = "Barcode Scanner"
        private const val SDP_DESCRIPTION = "Scans barcodes and sends them as keyboard input"
        private const val SDP_PROVIDER = "codetropic.com"

        private data class HidKey(val code: Int, val modifier: Int = 0)

        private val US_KEYBOARD_MAP = mapOf(
            'a' to HidKey(0x04), 'b' to HidKey(0x05), 'c' to HidKey(0x06), 'd' to HidKey(0x07),
            'e' to HidKey(0x08), 'f' to HidKey(0x09), 'g' to HidKey(0x0A), 'h' to HidKey(0x0B),
            'i' to HidKey(0x0C), 'j' to HidKey(0x0D), 'k' to HidKey(0x0E), 'l' to HidKey(0x0F),
            'm' to HidKey(0x10), 'n' to HidKey(0x11), 'o' to HidKey(0x12), 'p' to HidKey(0x13),
            'q' to HidKey(0x14), 'r' to HidKey(0x15), 's' to HidKey(0x16), 't' to HidKey(0x17),
            'u' to HidKey(0x18), 'v' to HidKey(0x19), 'w' to HidKey(0x1A), 'x' to HidKey(0x1B),
            'y' to HidKey(0x1C), 'z' to HidKey(0x1D),
            'A' to HidKey(0x04, 2), 'B' to HidKey(0x05, 2), 'C' to HidKey(0x06, 2), 'D' to HidKey(0x07, 2),
            'E' to HidKey(0x08, 2), 'F' to HidKey(0x09, 2), 'G' to HidKey(0x0A, 2), 'H' to HidKey(0x0B, 2),
            'I' to HidKey(0x0C, 2), 'J' to HidKey(0x0D, 2), 'K' to HidKey(0x0E, 2), 'L' to HidKey(0x0F, 2),
            'M' to HidKey(0x10, 2), 'N' to HidKey(0x11, 2), 'O' to HidKey(0x12, 2), 'P' to HidKey(0x13, 2),
            'Q' to HidKey(0x14, 2), 'R' to HidKey(0x15, 2), 'S' to HidKey(0x16, 2), 'T' to HidKey(0x17, 2),
            'U' to HidKey(0x18, 2), 'V' to HidKey(0x19, 2), 'W' to HidKey(0x1A, 2), 'X' to HidKey(0x1B, 2),
            'Y' to HidKey(0x1C, 2), 'Z' to HidKey(0x1D, 2),
            '1' to HidKey(0x1E), '2' to HidKey(0x1F), '3' to HidKey(0x20), '4' to HidKey(0x21),
            '5' to HidKey(0x22), '6' to HidKey(0x23), '7' to HidKey(0x24), '8' to HidKey(0x25),
            '9' to HidKey(0x26), '0' to HidKey(0x27),
            ' ' to HidKey(0x2C), '-' to HidKey(0x2D), '=' to HidKey(0x2E), '[' to HidKey(0x2F),
            ']' to HidKey(0x30), '\' to HidKey(0x31), ';' to HidKey(0x33), '\'' to HidKey(0x34),
            '`' to HidKey(0x35), ',' to HidKey(0x36), '.' to HidKey(0x37), '/' to HidKey(0x38),
            '!' to HidKey(0x1E, 2), '@' to HidKey(0x1F, 2), '#' to HidKey(0x20, 2), '$' to HidKey(0x21, 2),
            '%' to HidKey(0x22, 2), '^' to HidKey(0x23, 2), '&' to HidKey(0x24, 2), '*' to HidKey(0x25, 2),
            '(' to HidKey(0x26, 2), ')' to HidKey(0x27, 2), '_' to HidKey(0x2D, 2), '+' to HidKey(0x2E, 2),
            '{' to HidKey(0x2F, 2), '}' to HidKey(0x30, 2), '|' to HidKey(0x31, 2), ':' to HidKey(0x33, 2),
            '"' to HidKey(0x34, 2), '~' to HidKey(0x35, 2), '<' to HidKey(0x36, 2), '>' to HidKey(0x37, 2),
            '?' to HidKey(0x38, 2)
        )

        // The HID Report Descriptor - this describes our device as a standard 104-key keyboard
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
            0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (Keyboard LeftControl)
            0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (Keyboard Right GUI)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x95.toByte(), 0x08.toByte(), //   Report Count (8)
            0x81.toByte(), 0x02.toByte(), //   Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x81.toByte(), 0x01.toByte(), //   Input (Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
            0x95.toByte(), 0x05.toByte(), //   Report Count (5)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x05.toByte(), 0x08.toByte(), //   Usage Page (LEDs)
            0x19.toByte(), 0x01.toByte(), //   Usage Minimum (Num Lock)
            0x29.toByte(), 0x05.toByte(), //   Usage Maximum (Kana)
            0x91.toByte(), 0x02.toByte(), //   Output (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x75.toByte(), 0x03.toByte(), //   Report Size (3)
            0x91.toByte(), 0x01.toByte(), //   Output (Const,Array,Abs,No Wrap,Linear,Preferred State,No Null Position,Non-volatile)
            0x95.toByte(), 0x06.toByte(), //   Report Count (6)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
            0x19.toByte(), 0x00.toByte(), //   Usage Minimum (Reserved (no event indicated))
            0x29.toByte(), 0x65.toByte(), //   Usage Maximum (Keyboard Application)
            0x81.toByte(), 0x00.toByte(), //   Input (Data,Array,Abs,No Wrap,Linear,Preferred State,No Null Position)
            0xC0.toByte()                // End Collection
        )
    }
}