package net.bluetoothviewer

import android.content.Intent
import android.content.res.AssetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.Charset
import java.util.concurrent.CopyOnWriteArrayList

object BluetoothHub {

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
    }

    data class UiMessage(
        val direction: Direction,
        val text: String,
    ) {
        enum class Direction {
            IN,
            OUT,
            STATUS,
        }
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    private val _recording = MutableStateFlow("")
    val recording: StateFlow<String> = _recording

    private val broadcasters = CopyOnWriteArrayList<(UiMessage) -> Unit>()

    @Volatile
    var recordingEnabled: Boolean = false

    @Volatile
    var defaultEmail: String = ""

    @Volatile
    var mockDevicesEnabled: Boolean = false

    private var deviceConnector: DeviceConnector = NullDeviceConnector()
    private val recordingBuffer = StringBuilder()

    fun setPaused(value: Boolean) {
        _paused.value = value
        addStatus("paused=${value}")
    }

    fun clearMessages() {
        _messages.value = emptyList()
        recordingBuffer.setLength(0)
        _recording.value = ""
    }

    fun connectFromDeviceListResult(data: Intent, assets: AssetManager) {
        val connector = DeviceListActivity.createDeviceConnector(data, messageHandler, assets)
        if (connector == null) {
            addStatus("error: could not create connector")
            return
        }
        val address = data.getStringExtra("BLUETOOTH_ADDRESS")
        _connectedDeviceAddress.value = address
        deviceConnector.disconnect()
        deviceConnector = connector
        deviceConnector.connect()
    }

    fun connectToAddress(address: String) {
        _connectedDeviceAddress.value = address
        val connector: DeviceConnector = BluetoothDeviceConnector(messageHandler, address)
        deviceConnector.disconnect()
        deviceConnector = connector
        deviceConnector.connect()
    }

    fun disconnect() {
        deviceConnector.disconnect()
    }

    fun sendAsciiMessage(text: String) {
        if (text.isNotEmpty()) {
            deviceConnector.sendAsciiMessage(text)
        }
    }

    fun addBroadcaster(broadcaster: (UiMessage) -> Unit) {
        broadcasters.add(broadcaster)
    }

    fun removeBroadcaster(broadcaster: (UiMessage) -> Unit) {
        broadcasters.remove(broadcaster)
    }

    private fun addMessage(msg: UiMessage) {
        _messages.update { it + msg }
        broadcasters.forEach { it.invoke(msg) }
    }

    private fun addStatus(text: String) {
        addMessage(UiMessage(UiMessage.Direction.STATUS, text))
    }

    private val messageHandler: MessageHandler = object : MessageHandler {
        override fun sendLineRead(line: String) {
            if (_paused.value) return
            addMessage(UiMessage(UiMessage.Direction.IN, line))
            if (recordingEnabled) {
                synchronized(recordingBuffer) {
                    recordingBuffer.append(line).append("\n")
                    _recording.value = recordingBuffer.toString()
                }
            }
        }

        override fun sendBytesWritten(bytes: ByteArray) {
            val written = String(bytes, Charset.defaultCharset())
            addMessage(UiMessage(UiMessage.Direction.OUT, written.trimEnd()))
        }

        override fun sendConnectingTo(deviceName: String) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _connectedDeviceName.value = deviceName
            addStatus("connecting: $deviceName")
        }

        override fun sendConnectedTo(deviceName: String) {
            _connectionStatus.value = ConnectionStatus.CONNECTED
            _connectedDeviceName.value = deviceName
            synchronized(recordingBuffer) {
                recordingBuffer.setLength(0)
                _recording.value = ""
            }
            addStatus("connected: $deviceName")
        }

        override fun sendNotConnected() {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedDeviceName.value = null
            _connectedDeviceAddress.value = null
            addStatus("not connected")
        }

        override fun sendConnectionFailed() {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedDeviceName.value = null
            _connectedDeviceAddress.value = null
            addStatus("connection failed")
        }

        override fun sendConnectionLost() {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedDeviceName.value = null
            _connectedDeviceAddress.value = null
            addStatus("connection lost")
        }
    }
}
