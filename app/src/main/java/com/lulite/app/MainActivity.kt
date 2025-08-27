package com.lulite.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private val vm: StreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.bindService()

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { LuLiteScreen(vm) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.unbindService()
    }
}

@Composable
private fun LuLiteScreen(vm: StreamViewModel) {
    val ctx = LocalContext.current
    val ui by vm.state.observeAsState(StreamViewModel.UiState())
    val clipboard: ClipboardManager = LocalClipboardManager.current

    // Runtime permissions: notifications (33+) + camera (for scan)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) Toast.makeText(ctx, "Camera permission denied", Toast.LENGTH_SHORT).show() }

    // MediaProjection launcher
    val projLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            vm.start(res.resultCode, res.data!!)
        } else {
            Toast.makeText(ctx, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ZXing scanner
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            vm.applyAnswer(result.contents!!)
        }
    }

    var answerInput by remember { mutableStateOf(TextFieldValue("")) }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("LuLite — LAN WebRTC Screen Stream (no audio)", style = MaterialTheme.typography.titleLarge)

        // Presets
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quality:")
            FilterChip(
                selected = ui.width == 1280,
                onClick = { vm.setPreset(false) },
                label = { Text("720p / 30fps") }
            )
            FilterChip(
                selected = ui.width == 1920,
                onClick = { vm.setPreset(true) },
                label = { Text("1080p / 30fps") }
            )
        }

        // Start / Stop
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !ui.running,
                onClick = {
                    // Ask for camera permission only when scanning later; not needed to start streaming.
                    val mpm = ctx.getSystemService<MediaProjectionManager>()!!
                    projLauncher.launch(mpm.createScreenCaptureIntent())
                }
            ) { Text("Start Stream") }

            OutlinedButton(
                enabled = ui.running,
                onClick = { vm.stop() }
            ) { Text("Stop") }
        }

        Text("Status: ${ui.statusText}")

        // Offer QR and copy
        ui.offerB64?.let { b64 ->
            val bmp = remember(b64) { QrUtils.generateQr(b64, 720) }
            Text("Offer (show this QR to the viewer):")
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Offer QR",
                modifier = Modifier.fillMaxWidth().height(280.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(b64))
                    Toast.makeText(ctx, "Offer copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy Offer") }
            }
        }

        Divider()

        // Answer paste + scan
        Text("Paste or Scan Answer from viewer:")
        BasicTextField(
            value = answerInput,
            onValueChange = { answerInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.applyAnswer(answerInput.text) }) { Text("Apply Answer") }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 23) {
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
                val opts = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Scan Answer QR")
                }
                scanLauncher.launch(opts)
            }) { Text("Scan Answer") }
        }
    }
}
