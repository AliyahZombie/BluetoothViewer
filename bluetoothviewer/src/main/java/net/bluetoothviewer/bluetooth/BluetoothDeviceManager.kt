package net.bluetoothviewer.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

object BluetoothDeviceManager {

    data class DeviceInfo(
        val name: String?,
        val address: String,
        val bonded: Boolean,
        val rssi: Int?,
        val source: Source,
    ) {
        enum class Source {
            PAIRED,
            DISCOVERED,
        }
    }

    sealed class Event {
        data object ScanStarted : Event()
        data object ScanFinished : Event()
        data class DeviceFound(val device: DeviceInfo) : Event()
    }

    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()

    private val _scanActive = MutableStateFlow(false)
    val scanActive: StateFlow<Boolean> = _scanActive

    private val _discovered = MutableStateFlow<Map<String, DeviceInfo>>(emptyMap())
    val discovered: StateFlow<Map<String, DeviceInfo>> = _discovered

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var receiverRegistered: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _scanActive.value = true
                    emit(Event.ScanStarted)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _scanActive.value = false
                    emit(Event.ScanFinished)
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val info = DeviceInfo(
                            name = safeDeviceName(device),
                            address = device.address,
                            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
                            rssi = if (rssi == Short.MIN_VALUE) null else rssi.toInt(),
                            source = DeviceInfo.Source.DISCOVERED,
                        )
                        _discovered.update { it + (info.address to info) }
                        emit(Event.DeviceFound(info))
                    }
                }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    @Synchronized
    fun shutdown() {
        val ctx = appContext
        if (ctx != null && receiverRegistered) {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, e.message, e)
            }
        }
        receiverRegistered = false
        appContext = null
        listeners.clear()
        _scanActive.value = false
        _discovered.value = emptyMap()
    }

    fun addListener(listener: (Event) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Event) -> Unit) {
        listeners.remove(listener)
    }

    fun listPairedDevices(): Result<List<DeviceInfo>> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("not initialized"))
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return Result.success(emptyList())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)) {
            return Result.failure(SecurityException("missing BLUETOOTH_CONNECT"))
        }

        return try {
            val list = adapter.bondedDevices
                .orEmpty()
                .map { dev ->
                    DeviceInfo(
                        name = safeDeviceName(dev),
                        address = dev.address,
                        bonded = dev.bondState == BluetoothDevice.BOND_BONDED,
                        rssi = null,
                        source = DeviceInfo.Source.PAIRED,
                    )
                }
                .sortedBy { it.name ?: it.address }
            Result.success(list)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    fun getDiscoveredDevices(): List<DeviceInfo> {
        return _discovered.value.values.sortedBy { it.name ?: it.address }
    }

    fun startScan(): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("not initialized"))
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return Result.failure(IllegalStateException("no bluetooth adapter"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(ctx, Manifest.permission.BLUETOOTH_SCAN)) {
            return Result.failure(SecurityException("missing BLUETOOTH_SCAN"))
        }

        registerReceiverIfNeeded(ctx)

        return try {
            _discovered.value = emptyMap()
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val ok = adapter.startDiscovery()
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("startDiscovery failed"))
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    fun stopScan(): Result<Unit> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return Result.success(Unit)
        return try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    private fun emit(event: Event) {
        listeners.forEach { it.invoke(event) }
    }

    private fun registerReceiverIfNeeded(ctx: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun hasPermission(ctx: Context, perm: String): Boolean {
        return ContextCompat.checkSelfPermission(ctx, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun safeDeviceName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (_: SecurityException) {
            null
        }
    }

    private const val TAG = "BluetoothDevices"
}
