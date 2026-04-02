package net.bluetoothviewer.ws

import android.util.Log
import net.bluetoothviewer.BluetoothHub
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
        val payload = parseIncoming(message)
        when (payload.type) {
            IncomingType.SEND -> BluetoothHub.sendAsciiMessage(payload.data)
            IncomingType.PAUSE -> BluetoothHub.setPaused(payload.pause)
            IncomingType.DISCONNECT -> BluetoothHub.disconnect()
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, ex.message, ex)
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket started on ${address.hostString}:${port}")
        broadcast(jsonStatus("started"))
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

    private data class Incoming(
        val type: IncomingType,
        val data: String,
        val pause: Boolean,
    )

    private enum class IncomingType {
        SEND,
        PAUSE,
        DISCONNECT,
    }

    private fun parseIncoming(message: String): Incoming {
        val trimmed = message.trim()
        if (trimmed.startsWith("{")) {
            try {
                val obj = JSONObject(trimmed)
                val type = obj.optString("type", "send")
                return when (type.lowercase()) {
                    "pause" -> Incoming(IncomingType.PAUSE, "", obj.optBoolean("value", false))
                    "disconnect" -> Incoming(IncomingType.DISCONNECT, "", false)
                    else -> Incoming(IncomingType.SEND, obj.optString("data", ""), false)
                }
            } catch (e: Exception) {
                Log.w(TAG, e.message, e)
            }
        }
        return Incoming(IncomingType.SEND, message, false)
    }

    companion object {
        private const val TAG = "BluetoothWS"
    }
}
