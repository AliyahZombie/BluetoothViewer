package net.bluetoothviewer.ws

import android.util.Log
import net.bluetoothviewer.BluetoothHub
import net.bluetoothviewer.bluetooth.BluetoothDeviceManager
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress

class BluetoothWebSocketServer(
    address: InetSocketAddress,
) : WebSocketServer(address) {

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        conn.send(jsonStatus("connected"))
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val req = parseRequest(message)
        when (req.type) {
            "send" -> {
                val data = req.obj?.optString("data", "") ?: req.raw
                BluetoothHub.sendAsciiMessage(data)
                sendOk(conn, req.id, JSONObject().put("type", "send"))
            }
            "pause" -> {
                val value = req.obj?.optBoolean("value", false) ?: false
                BluetoothHub.setPaused(value)
                sendOk(conn, req.id, JSONObject().put("paused", value))
            }
            "disconnect" -> {
                BluetoothHub.disconnect()
                sendOk(conn, req.id, JSONObject().put("type", "disconnect"))
            }
            "connection.info" -> {
                val info = JSONObject()
                info.put("status", BluetoothHub.connectionStatus.value.name.lowercase())
                info.put("deviceName", BluetoothHub.connectedDeviceName.value)
                info.put("deviceAddress", BluetoothHub.connectedDeviceAddress.value)
                info.put("paused", BluetoothHub.paused.value)
                sendOk(conn, req.id, info)
            }
            "devices.list" -> {
                val scope = req.obj?.optString("scope", "paired") ?: "paired"
                val result = JSONObject()
                val devices = org.json.JSONArray()
                if (scope == "paired" || scope == "all") {
                    val paired = BluetoothDeviceManager.listPairedDevices()
                    if (paired.isFailure) {
                        sendErr(conn, req.id, "paired devices not available", paired.exceptionOrNull())
                        return
                    }
                    paired.getOrThrow().forEach { d ->
                        devices.put(deviceToJson(d))
                    }
                }
                if (scope == "discovered" || scope == "all") {
                    BluetoothDeviceManager.getDiscoveredDevices().forEach { d ->
                        devices.put(deviceToJson(d))
                    }
                }
                result.put("devices", devices)
                sendOk(conn, req.id, result)
            }
            "scan.start" -> {
                val r = BluetoothDeviceManager.startScan()
                if (r.isFailure) {
                    sendErr(conn, req.id, "scan start failed", r.exceptionOrNull())
                } else {
                    sendOk(conn, req.id, JSONObject().put("scanning", true))
                }
            }
            "scan.stop" -> {
                val r = BluetoothDeviceManager.stopScan()
                if (r.isFailure) {
                    sendErr(conn, req.id, "scan stop failed", r.exceptionOrNull())
                } else {
                    sendOk(conn, req.id, JSONObject().put("scanning", false))
                }
            }
            "connect" -> {
                val address = req.obj?.optString("address", "") ?: ""
                if (address.isBlank()) {
                    sendErr(conn, req.id, "missing address", null)
                    return
                }
                BluetoothHub.connectToAddress(address)
                sendOk(conn, req.id, JSONObject().put("address", address))
            }
            else -> {
                if (req.raw.isNotBlank()) {
                    BluetoothHub.sendAsciiMessage(req.raw)
                }
                sendOk(conn, req.id, JSONObject().put("type", "send"))
            }
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, ex.message, ex)
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket started on ${address.hostString}:${port}")
        broadcast(jsonStatus("started"))
    }

    fun broadcastEvent(name: String, data: JSONObject) {
        val obj = JSONObject()
        obj.put("type", "event")
        obj.put("name", name)
        obj.put("data", data)
        broadcast(obj.toString())
    }

    fun broadcastUiMessage(msg: BluetoothHub.UiMessage) {
        val obj = JSONObject()
        obj.put("type", msg.direction.name.lowercase())
        obj.put("text", msg.text)
        broadcast(obj.toString())
    }

    private fun jsonStatus(status: String): String {
        val obj = JSONObject()
        obj.put("type", "status")
        obj.put("status", status)
        obj.put("port", port)
        return obj.toString()
    }

    private data class Request(
        val id: String?,
        val type: String,
        val obj: JSONObject?,
        val raw: String,
    )

    private fun parseRequest(message: String): Request {
        val trimmed = message.trim()
        if (trimmed.startsWith("{")) {
            try {
                val obj = JSONObject(trimmed)
                val type = obj.optString("type", "send").trim().ifEmpty { "send" }
                val id = obj.optString("id").trim().ifEmpty { null }
                return Request(id = id, type = type.lowercase(), obj = obj, raw = "")
            } catch (e: Exception) {
                Log.w(TAG, e.message, e)
            }
        }
        return Request(id = null, type = "send", obj = null, raw = message)
    }

    private fun sendOk(conn: WebSocket, id: String?, result: JSONObject) {
        val obj = JSONObject()
        obj.put("type", "resp")
        if (id != null) obj.put("id", id)
        obj.put("ok", true)
        obj.put("result", result)
        conn.send(obj.toString())
    }

    private fun sendErr(conn: WebSocket, id: String?, message: String, ex: Throwable?) {
        val obj = JSONObject()
        obj.put("type", "resp")
        if (id != null) obj.put("id", id)
        obj.put("ok", false)
        val err = JSONObject()
        err.put("message", message)
        if (ex != null) {
            err.put("exception", ex.javaClass.simpleName)
            err.put("details", ex.message)
        }
        obj.put("error", err)
        conn.send(obj.toString())
    }

    private fun deviceToJson(d: BluetoothDeviceManager.DeviceInfo): JSONObject {
        val obj = JSONObject()
        obj.put("name", d.name)
        obj.put("address", d.address)
        obj.put("bonded", d.bonded)
        obj.put("rssi", d.rssi)
        obj.put("source", d.source.name.lowercase())
        return obj
    }

    companion object {
        private const val TAG = "BluetoothWS"
    }
}
