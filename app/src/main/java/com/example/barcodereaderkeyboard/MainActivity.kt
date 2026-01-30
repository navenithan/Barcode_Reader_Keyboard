package com.example.barcodereaderkeyboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.codetropic.barcodescanner.BluetoothHidService
import com.codetropic.barcodescanner.ScannerActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_start_hid).setOnClickListener {
            startService(Intent(this, BluetoothHidService::class.java))
        }

        findViewById<Button>(R.id.btn_scan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
    }
}