package com.ultron.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import com.ultron.app.server.LocalServerService
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val voskModelPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) VoskModelManager.importZip(applicationContext, uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AssistantController.init(applicationContext)
        AssistantController.setLocationPermissionRequest {
            val wanted = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            permissionLauncher.launch(wanted)
        }
        setContent {
            UltronHud(
                onRequestMicrophonePermission = {
                    val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
                    permissionLauncher.launch(wanted.toTypedArray())
                },
                onPickVoskModel = {
                    voskModelPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*"))
                },
                onRequestLocationPermission = {
                    val wanted = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                    permissionLauncher.launch(wanted)
                },
                onOpenAccessibilitySettings = {
                    runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        .onFailure { Toast.makeText(this, "Could not open Accessibility settings.", Toast.LENGTH_SHORT).show() }
                },
                onOpenSessionFolder = {
                    runCatching { startActivity(StorageManager.accessibleFolderIntent()) }
                        .onFailure { Toast.makeText(this, "No file picker is available on this device.", Toast.LENGTH_SHORT).show() }
                },
                onSetLocalServer = { enabled ->
                    val intent = Intent(this, LocalServerService::class.java)
                    if (enabled) runCatching { androidx.core.content.ContextCompat.startForegroundService(this, intent) }
                    else runCatching { stopService(intent) }
                }
            )
        }
    }
}
