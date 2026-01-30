package com.codetropic.barcodescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.barcodereaderkeyboard.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var lastScanTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)
        previewView = findViewById(R.id.preview_view)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (isCameraPermissionGranted()) {
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            }, ContextCompat.getMainExecutor(this))
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            baseContext, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases(cameraProvider)
                }, ContextCompat.getMainExecutor(this))
            } else {
                // Handle permission denial
                finish()
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()

        val scanner = BarcodeScanning.getClient(options)

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val currentTime = System.currentTimeMillis()
                        if (barcodes.isNotEmpty() && (currentTime - lastScanTime) > 1000) { // 1-second debounce
                            val barcode = barcodes[0]
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                Log.d(TAG, "Barcode scanned: $rawValue")
                                val intent = Intent(this, BluetoothHidService::class.java)
                                intent.putExtra("barcode_data", rawValue)
                                startService(intent)
                                lastScanTime = currentTime
                                finish() // Immediately return after scan
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scanning failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "ScannerActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}