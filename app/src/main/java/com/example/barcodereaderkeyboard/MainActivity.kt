package com.example.barcodereaderkeyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.codetropic.barcodescanner.BluetoothHidService
import com.codetropic.barcodescanner.ScannerActivity

class MainActivity : AppCompatActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permission result received")
        if (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.BLUETOOTH_ADVERTISE] == true) {
            Log.d(TAG, "All Bluetooth permissions granted. Starting service.")
            startService(Intent(this, BluetoothHidService::class.java))
        } else {
            Log.e(TAG, "Not all Bluetooth permissions were granted.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_start_hid).setOnClickListener {
            Log.d(TAG, "'Start HID Service' button clicked.")
            checkPermissionsAndStartService()
            Log.d(TAG, "checkPermissionsAndStartService():Completed")
        }

        findViewById<Button>(R.id.btn_scan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
        Log.d(TAG, "onCreate():Completed")
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasAdvertise = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

            if (hasConnect && hasScan && hasAdvertise) {
                Log.d(TAG, "All Bluetooth permissions already granted. Starting service.")
                startService(Intent(this, BluetoothHidService::class.java))
                Log.d(TAG, "All Bluetooth permissions already granted. Service Started.")
            } else {
                Log.d(TAG, "Requesting Bluetooth permissions...")
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE))
            }
        } else {
            startService(Intent(this, BluetoothHidService::class.java))
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}