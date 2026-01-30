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
import androidx.core.content.ContextCompat

@SuppressLint("MissingPermission")
class BluetoothHidService : Service() {

    private var hidDevice: BluetoothHidDevice? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hostDevice: BluetoothDevice? = null
    private var lastSentBarcode: String? = null
    private var lastSendTime: Long = 0

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            Log.d(TAG, "[CALLBACK] onServiceConnected() - profile: $profile, thread: ${Thread.currentThread().name}")
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "[CALLBACK] Profile is HID_DEVICE! Casting proxy...")
                hidDevice = proxy as BluetoothHidDevice
                Log.d(TAG, "[CALLBACK] HID_DEVICE profile connected. hidDevice = $hidDevice")
                Log.d(TAG, "[CALLBACK] Registering HID app...")
                val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                    SDP_NAME,
                    SDP_DESCRIPTION,
                    SDP_PROVIDER,
                    BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                    HID_REPORT_DESCRIPTOR
                )
                hidDevice?.registerApp(sdpSettings, null, null, ContextCompat.getMainExecutor(applicationContext), appCallback)
                Log.d(TAG, "[CALLBACK] registerApp() called successfully")
            } else {
                Log.w(TAG, "[CALLBACK] Profile is NOT HID_DEVICE, it's $profile")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "[CALLBACK] onServiceDisconnected() - profile: $profile, thread: ${Thread.currentThread().name}")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                Log.w(TAG, "[CALLBACK] HID_DEVICE profile disconnected")
            }
        }
    }

    private val appCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(TAG, "onAppStatusChanged() - registered: $registered, device: ${pluggedDevice?.address}")
            if (registered) {
                Log.d(TAG, "HID App registered successfully")
            } else {
                Log.d(TAG, "HID App unregistered")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.d(TAG, "onConnectionStateChanged() - device: ${device.address}, state: $state (1=CONNECTED, 0=DISCONNECTED)")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                hostDevice = device
                Log.d(TAG, "Host device connected: ${device.name} (${device.address})")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                hostDevice = null
                Log.d(TAG, "Host device disconnected")
            }
        }
    }

    override fun onCreate() {
        Log.d(TAG, "BluetoothHidService onCreate() Start")
        super.onCreate()
        Log.d(TAG, "BluetoothHidService onCreate() called")
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "FATAL: BluetoothAdapter is null - device does not support Bluetooth HID")
            stopSelf()
            return
        }
        
        Log.d(TAG, "BluetoothAdapter found. Requesting HID_DEVICE profile proxy...")
        val success = bluetoothAdapter.getProfileProxy(applicationContext, profileListener, BluetoothProfile.HID_DEVICE)
        Log.d(TAG, "getProfileProxy() returned: $success")
        
        // Schedule a delayed initialization check
        // In case the callback doesn't fire immediately, we'll initialize after a short delay
        Thread {
            Thread.sleep(2000) // Wait 2 seconds for callback
            Log.d(TAG, "[DELAYED CHECK] Checking if hidDevice was initialized...")
            
            if (hidDevice == null) {
                Log.w(TAG, "[DELAYED CHECK] onServiceConnected() callback didn't fire. Attempting fallback initialization...")
                try {
                    // The callback may not fire on some devices even though getProfileProxy succeeds
                    // Try to manually initialize the HID app
                    Log.d(TAG, "[DELAYED CHECK] Creating SDP settings for manual initialization...")
                    val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                        SDP_NAME,
                        SDP_DESCRIPTION,
                        SDP_PROVIDER,
                        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                        HID_REPORT_DESCRIPTOR
                    )
                    Log.e(TAG, "[DELAYED CHECK] HID_DEVICE profile callback not working. Device may not fully support HID profile.")
                    Log.d(TAG, "[DELAYED CHECK] This is a known Android Bluetooth limitation on some devices.")
                } catch (e: Exception) {
                    Log.e(TAG, "[DELAYED CHECK] Exception: ", e)
                }
            } else {
                Log.d(TAG, "[DELAYED CHECK] SUCCESS - hidDevice was initialized by callback")
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() called")
        val barcode = intent?.getStringExtra("barcode_data")
        Log.d(TAG, "Barcode data: '$barcode'")
        
        if (!barcode.isNullOrEmpty()) {
            val currentTime = System.currentTimeMillis()
            
            // Prevent sending the same barcode within 2 seconds (duplicate prevention)
            if (barcode == lastSentBarcode && (currentTime - lastSendTime) < 2000) {
                Log.w(TAG, "Ignoring duplicate barcode '$barcode' (sent ${currentTime - lastSendTime}ms ago)")
                return START_NOT_STICKY
            }
            
            lastSentBarcode = barcode
            lastSendTime = currentTime
            sendKeyboardReport(barcode)
        } else {
            Log.w(TAG, "No barcode data provided in intent")
        }
        return START_NOT_STICKY
    }

    private fun sendKeyboardReport(text: String) {
        Log.d(TAG, "sendKeyboardReport() called with text: '$text'")
        
        if (hidDevice == null) {
            Log.e(TAG, "FATAL: hidDevice is null - HID profile not connected")
            return
        }
        
        if (hostDevice == null) {
            Log.e(TAG, "No host device connected. Cannot send report.")
            return
        }
        
        // Send reports on background thread to avoid blocking main thread
        Thread {
            try {
                Log.d(TAG, "Sending keyboard report to device: ${hostDevice?.address}")
                
                text.forEach { char ->
                    val key = US_KEYBOARD_MAP[char]
                    if (key != null) {
                        val modifier = key.modifier.toByte()
                        val code = key.code.toByte()
                        // Press key
                        hidDevice?.sendReport(hostDevice, 0, byteArrayOf(modifier, 0, code, 0, 0, 0, 0, 0))
                        Thread.sleep(20) // Delay for key press
                        // Release key
                        hidDevice?.sendReport(hostDevice, 0, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
                        Thread.sleep(20) // Delay between characters
                    }
                }
                
                // Only send ENTER if we actually sent some text
                if (text.isNotEmpty()) {
                    Log.d(TAG, "Sending ENTER key")
                    // Press Enter
                    hidDevice?.sendReport(hostDevice, 0, byteArrayOf(0, 0, 0x28, 0, 0, 0, 0, 0))
                    Thread.sleep(50) // Longer delay for ENTER press
                    // Release Enter
                    hidDevice?.sendReport(hostDevice, 0, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
                    Thread.sleep(50) // Wait after release to ensure Windows processes it
                    Log.d(TAG, "ENTER key sent and released")
                }
                
                Log.d(TAG, "Keyboard report sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending keyboard report: ", e)
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "MainActivity"
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
            ']' to HidKey(0x30), '\\' to HidKey(0x31), ';' to HidKey(0x33), '\'' to HidKey(0x34),
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