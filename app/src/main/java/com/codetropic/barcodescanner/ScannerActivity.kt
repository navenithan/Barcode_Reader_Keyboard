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
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var lastScanTime = 0L
    private var isScanning = true // Flag to prevent multiple scans

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ScannerActivity.onCreate() - START")
        try {
            setContentView(R.layout.activity_scanner)
            Log.d(TAG, "setContentView() completed")
            previewView = findViewById(R.id.preview_view)
            Log.d(TAG, "previewView = $previewView")

            cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            Log.d(TAG, "ProcessCameraProvider.getInstance() called")
            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d(TAG, "cameraExecutor created")

            if (isCameraPermissionGranted()) {
                Log.d(TAG, "Camera permission already granted. Adding listener for cameraProviderFuture...")
                cameraProviderFuture.addListener({
                    try {
                        Log.d(TAG, "cameraProviderFuture listener triggered")
                        val cameraProvider = cameraProviderFuture.get()
                        Log.d(TAG, "ProcessCameraProvider obtained: $cameraProvider")
                        bindCameraUseCases(cameraProvider)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in cameraProviderFuture listener: ", e)
                    }
                }, ContextCompat.getMainExecutor(this))
            } else {
                Log.d(TAG, "Camera permission NOT granted. Requesting...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
            }
            Log.d(TAG, "ScannerActivity.onCreate() - END")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in onCreate(): ", e)
            throw e
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        val result = ContextCompat.checkSelfPermission(
            baseContext, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "isCameraPermissionGranted() = $result")
        return result
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult() - requestCode: $requestCode")
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            Log.d(TAG, "CAMERA_PERMISSION_REQUEST_CODE received. grantResults: ${grantResults.contentToString()}")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission GRANTED. Adding listener for cameraProviderFuture...")
                cameraProviderFuture.addListener({
                    try {
                        Log.d(TAG, "cameraProviderFuture listener triggered (from onRequestPermissionsResult)")
                        val cameraProvider = cameraProviderFuture.get()
                        Log.d(TAG, "ProcessCameraProvider obtained: $cameraProvider")
                        bindCameraUseCases(cameraProvider)
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in cameraProviderFuture listener: ", e)
                    }
                }, ContextCompat.getMainExecutor(this))
            } else {
                Log.e(TAG, "Camera permission DENIED. Finishing activity.")
                finish()
            }
        } else {
            Log.w(TAG, "Unknown requestCode: $requestCode")
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        Log.d(TAG, "bindCameraUseCases() - START")
        this.cameraProvider = cameraProvider // Store for later unbinding
        try {
            val preview = Preview.Builder().build()
            Log.d(TAG, "Preview built")
            preview.setSurfaceProvider(previewView.surfaceProvider)
            Log.d(TAG, "Preview surface provider set")

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            Log.d(TAG, "CameraSelector built")

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            Log.d(TAG, "ImageAnalysis built")

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_QR_CODE
                )
                .build()
            Log.d(TAG, "BarcodeScannerOptions built")

            val scanner = BarcodeScanning.getClient(options)
            Log.d(TAG, "BarcodeScanning client created")

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && isScanning) { // Check if still scanning
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (!isScanning) {
                                    Log.d(TAG, "Scan already completed, ignoring results")
                                    return@addOnSuccessListener
                                }
                                Log.d(TAG, "Barcode scanner success: ${barcodes.size} barcodes detected")
                                val currentTime = System.currentTimeMillis()
                                if (barcodes.isNotEmpty() && (currentTime - lastScanTime) > 1000) { // 1-second debounce
                                    val barcode = barcodes[0]
                                    val rawValue = barcode.rawValue
                                    if (rawValue != null) {
                                        Log.d(TAG, "Barcode scanned (debounce passed): $rawValue")
                                        isScanning = false // Stop further scanning
                                        
                                        // Unbind camera immediately to stop processing
                                        cameraProvider?.unbindAll()
                                        Log.d(TAG, "Camera unbound")
                                        
                                        val intent = Intent(this@ScannerActivity, BluetoothHidService::class.java)
                                        intent.putExtra("barcode_data", rawValue)
                                        Log.d(TAG, "Starting BluetoothHidService with barcode: $rawValue")
                                        startService(intent)
                                        lastScanTime = currentTime
                                        Log.d(TAG, "Finishing ScannerActivity")
                                        finish() // Immediately return after scan
                                    } else {
                                        Log.w(TAG, "Barcode detected but rawValue is null")
                                    }
                                } else {
                                    Log.d(TAG, "Barcode detected but debounce active or empty: barcodes=${barcodes.size}, timeDiff=${System.currentTimeMillis() - lastScanTime}ms")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Barcode scanning failed: ", e)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        Log.w(TAG, "mediaImage is null")
                        imageProxy.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in image analyzer: ", e)
                    imageProxy.close()
                }
            }
            Log.d(TAG, "Image analyzer set")

            Log.d(TAG, "Binding camera use cases...")
            cameraProvider.unbindAll()
            Log.d(TAG, "cameraProvider.unbindAll() completed")
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            Log.d(TAG, "cameraProvider.bindToLifecycle() completed successfully")
            Log.d(TAG, "bindCameraUseCases() - END")
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed: ", e)
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() - unbinding camera and shutting down executor")
        try {
            isScanning = false // Stop any ongoing scans
            cameraProvider?.unbindAll()
            Log.d(TAG, "Camera unbound in onDestroy()")
            cameraExecutor.shutdown()
            Log.d(TAG, "cameraExecutor.shutdown() completed")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in onDestroy(): ", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}