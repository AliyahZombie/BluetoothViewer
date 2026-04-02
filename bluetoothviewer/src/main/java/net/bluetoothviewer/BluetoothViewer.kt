package net.bluetoothviewer

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import android.app.Application
import net.bluetoothviewer.util.ApplicationUtils
import net.bluetoothviewer.util.EmailUtils
import net.bluetoothviewer.library.R

class BluetoothViewer : AppCompatActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantedMap ->
        val granted = grantedMap.values.all { it }
        if (granted) {
            requestEnableBluetooth()
        } else {
            Toast.makeText(this, R.string.btstatus_not_connected, Toast.LENGTH_LONG).show()
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
    }

    private val deviceListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                BluetoothHub.connectFromDeviceListResult(data, assets)
            } else {
                Toast.makeText(this, R.string.error_could_not_create_device, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateParamsFromSettings()

        setContentView(
            ComposeView(this).apply {
                setContent {
                    BluetoothViewerTheme {
                        BluetoothViewerScreen(
                            onConnect = { startDeviceListActivity() },
                            onDisconnect = { BluetoothHub.disconnect() },
                            onOpenSettings = { startActivity(Intent(this@BluetoothViewer, SettingsActivity::class.java)) },
                            onEmailRecorded = { emailRecordedData() },
                        )
                    }
                }
            },
        )

        if (!BluetoothHub.mockDevicesEnabled) {
            ensureBluetoothPermissionsAndEnableIfNeeded()
        }
    }

    private fun updateParamsFromSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        BluetoothHub.recordingEnabled = prefs.getBoolean(getString(R.string.pref_record), false)
        BluetoothHub.defaultEmail = prefs.getString(getString(R.string.pref_default_email), "") ?: ""
        BluetoothHub.mockDevicesEnabled = prefs.getBoolean(getString(R.string.pref_enable_mock_devices), false)
    }

    private fun ensureBluetoothPermissionsAndEnableIfNeeded() {
        val perms = requiredRuntimePermissions()
        if (perms.isEmpty()) {
            requestEnableBluetooth()
            return
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
            return
        }
        requestEnableBluetooth()
    }

    private fun requiredRuntimePermissions(): List<String> {
        return when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                listOf(
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                )
            }
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> emptyList()
        }
    }

    private fun requestEnableBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null && !adapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun startDeviceListActivity() {
        val intent = Intent(this, DeviceListActivity::class.java)
        intent.putExtra(DeviceListActivity.EXTRA_MOCK_DEVICES_ENABLED, BluetoothHub.mockDevicesEnabled)
        deviceListLauncher.launch(intent)
    }

    private fun emailRecordedData() {
        val recording = BluetoothHub.recording.value
        if (recording.isEmpty()) {
            if (BluetoothHub.recordingEnabled) {
                Toast.makeText(this, R.string.msg_nothing_recorded, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.msg_nothing_recorded_recording_disabled, Toast.LENGTH_LONG).show()
            }
            return
        }
        val deviceName = BluetoothHub.connectedDeviceName.value ?: ""
        val intent = EmailUtils.prepareDeviceRecording(this, BluetoothHub.defaultEmail, deviceName, recording)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.email_client_chooser)))
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_email_client), Toast.LENGTH_SHORT).show()
        }
    }

}

@Composable
private fun BluetoothViewerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BluetoothViewerScreen(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onEmailRecorded: () -> Unit,
) {
    val ctx = LocalContext.current
    val status by BluetoothHub.connectionStatus.collectAsState()
    val paused by BluetoothHub.paused.collectAsState()
    val deviceName by BluetoothHub.connectedDeviceName.collectAsState()
    val messages by BluetoothHub.messages.collectAsState()

    var outgoing by remember { mutableStateOf("") }

    val canConnect = status != BluetoothHub.ConnectionStatus.CONNECTED
    val canDisconnect = status == BluetoothHub.ConnectionStatus.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (status) {
                        BluetoothHub.ConnectionStatus.CONNECTED -> {
                            val name = deviceName ?: ""
                            ctx.getString(R.string.btstatus_connected_to_fmt, name)
                        }
                        BluetoothHub.ConnectionStatus.CONNECTING -> {
                            val name = deviceName ?: ""
                            ctx.getString(R.string.btstatus_connecting_to_fmt, name)
                        }
                        BluetoothHub.ConnectionStatus.DISCONNECTED -> ctx.getString(R.string.btstatus_not_connected)
                    }
                    Text(title)
                },
                actions = {
                    IconButton(onClick = onConnect, enabled = canConnect) {
                        Icon(Icons.Default.Usb, contentDescription = null)
                    }
                    IconButton(onClick = onDisconnect, enabled = canDisconnect) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                    }
                    IconButton(onClick = { BluetoothHub.setPaused(!paused) }, enabled = canDisconnect) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = onEmailRecorded) {
                        Icon(Icons.Default.Email, contentDescription = null)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }

                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages) { msg ->
                    val prefix = when (msg.direction) {
                        BluetoothHub.UiMessage.Direction.IN -> ""
                        BluetoothHub.UiMessage.Direction.OUT -> ">>> "
                        BluetoothHub.UiMessage.Direction.STATUS -> "* "
                    }
                    Text(
                        text = prefix + msg.text,
                        fontFamily = if (msg.direction == BluetoothHub.UiMessage.Direction.IN || msg.direction == BluetoothHub.UiMessage.Direction.OUT) FontFamily.Monospace else FontFamily.Default,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (canDisconnect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = outgoing,
                        onValueChange = { outgoing = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(ctx.getString(R.string.send)) },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            BluetoothHub.sendAsciiMessage(outgoing)
                            outgoing = ""
                        },
                        enabled = outgoing.isNotEmpty(),
                    ) {
                        Text(ctx.getString(R.string.send))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                WelcomeText()
            }
        }
    }
}

@Composable
private fun WelcomeText() {
    val context = LocalContext.current
    val isLite = ApplicationUtils.isLiteVersion(context.applicationContext as Application)
    val html = context.getString(if (isLite) R.string.welcome_lite else R.string.welcome_full)
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(0, 8, 0, 8)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
