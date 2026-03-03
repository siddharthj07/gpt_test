package com.example.securevaultcamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var passkeyInput: EditText
    private lateinit var captureButton: Button
    private lateinit var recyclerView: RecyclerView

    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private val cryptoManager = CryptoManager()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            showToast("Camera permission is required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        passkeyInput = findViewById(R.id.passkeyInput)
        captureButton = findViewById(R.id.captureButton)
        recyclerView = findViewById(R.id.photoRecyclerView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        photoAdapter = PhotoAdapter { photo ->
            promptPasskeyAndDecrypt(photo)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = photoAdapter

        captureButton.setOnClickListener {
            val passkey = passkeyInput.text.toString().trim()
            if (passkey.length < 6) {
                showToast("Use a passkey with at least 6 characters")
                return@setOnClickListener
            }
            captureAndEncrypt(passkey)
        }

        ensureCameraPermissionAndStart()
        refreshPhotoList()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun ensureCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                showToast("Camera start failed: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndEncrypt(passkey: String) {
        val imageCapture = imageCapture ?: run {
            showToast("Camera not ready")
            return
        }

        val tempFile = File.createTempFile("capture_", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val plainBytes = tempFile.readBytes()
                        val encryptedBytes = cryptoManager.encrypt(plainBytes, passkey)
                        val encryptedFile = nextEncryptedFile()
                        encryptedFile.writeBytes(encryptedBytes)
                    } catch (e: Exception) {
                        runOnUiThread {
                            showToast("Encrypt failed: ${e.message}")
                        }
                    } finally {
                        tempFile.delete()
                    }
                    runOnUiThread {
                        showToast("Photo encrypted and saved")
                        refreshPhotoList()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    showToast("Capture failed: ${exception.message}")
                    tempFile.delete()
                }
            }
        )
    }

    private fun promptPasskeyAndDecrypt(photo: EncryptedPhoto) {
        val input = EditText(this)
        input.hint = "Enter passkey"

        AlertDialog.Builder(this)
            .setTitle("Decrypt ${photo.label}")
            .setView(input)
            .setPositiveButton("View") { _, _ ->
                val passkey = input.text.toString()
                decryptAndShow(photo, passkey)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun decryptAndShow(photo: EncryptedPhoto, passkey: String) {
        cameraExecutor.execute {
            try {
                val encryptedBytes = photo.file.readBytes()
                val plainBytes = cryptoManager.decrypt(encryptedBytes, passkey)
                val bitmap = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
                runOnUiThread {
                    if (bitmap == null) {
                        showToast("Unable to decode photo")
                        return@runOnUiThread
                    }
                    val imageView = ImageView(this)
                    imageView.setImageBitmap(bitmap)
                    AlertDialog.Builder(this)
                        .setTitle(photo.label)
                        .setView(imageView)
                        .setPositiveButton("Close", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Wrong passkey or corrupted file")
                }
            }
        }
    }

    private fun refreshPhotoList() {
        val folder = encryptedFolder()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val photos = folder.listFiles()
            ?.filter { it.isFile && it.extension == "enc" }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                val label = formatter.format(Date(it.lastModified()))
                EncryptedPhoto(it, label)
            }
            ?: emptyList()

        photoAdapter.submitList(photos)
    }

    private fun encryptedFolder(): File {
        return File(filesDir, "encrypted_photos").apply { mkdirs() }
    }

    private fun nextEncryptedFile(): File {
        val time = System.currentTimeMillis()
        return File(encryptedFolder(), "photo_${time}.enc")
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
