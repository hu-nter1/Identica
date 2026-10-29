package com.example.identica.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class CameraCapture(
    private val activity: ComponentActivity,
    private val targetFile: () -> File,
    private val onCaptured: (File) -> Unit,
    private val onFailed: (String) -> Unit
) {

    private var pendingFile: File? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else showPermissionDenied()
    }

    private val takePictureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null && file.length() > 0L) {
            onCaptured(file)
        } else {
            file?.delete()
            onFailed("Photo capture was cancelled.")
        }
    }

    fun capture() {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        when {
            granted -> launchCamera()
            activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> showRationale()
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val file = targetFile()
        file.parentFile?.mkdirs()
        file.createNewFile()
        pendingFile = file
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    private fun showRationale() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Camera access needed")
            .setMessage("The camera is used only to capture the profile or probe photo for biometric comparison.")
            .setPositiveButton("Continue") { _, _ -> permissionLauncher.launch(Manifest.permission.CAMERA) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDenied() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Camera permission denied")
            .setMessage("Enable the camera permission in system settings to capture photos.")
            .setPositiveButton("Open settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", activity.packageName, null))
                activity.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
