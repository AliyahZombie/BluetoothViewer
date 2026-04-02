package net.bluetoothviewer.ws

import android.content.Context
import androidx.preference.PreferenceManager
import net.bluetoothviewer.BluetoothHub
import net.bluetoothviewer.library.R
import java.net.InetSocketAddress
import java.util.concurrent.Executors

object WebSocketController {

    private var server: BluetoothWebSocketServer? = null
    private var broadcaster: ((BluetoothHub.UiMessage) -> Unit)? = null
    private var lastConfig: Config? = null

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BluetoothWS").apply {
            priority = Thread.NORM_PRIORITY
        }
    }

    data class Config(
        val enabled: Boolean,
        val host: String,
        val port: Int,
    )

    @Synchronized
    fun start(context: Context) {
        val cfg = readConfig(context.applicationContext)
        executor.execute {
            startInternal(cfg)
        }
    }

    @Synchronized
    fun stop() {
        executor.execute {
            stopInternal()
        }
    }

    @Synchronized
    fun restart(context: Context) {
        val cfg = readConfig(context.applicationContext)
        executor.execute {
            stopInternal()
            startInternal(cfg)
        }
    }

    private fun startInternal(cfg: Config) {
        if (cfg == lastConfig && server != null) {
            return
        }
        stopInternal()
        lastConfig = cfg
        if (!cfg.enabled) {
            return
        }

        val address = InetSocketAddress(cfg.host, cfg.port)
        val ws = BluetoothWebSocketServer(address)
        val bc: (BluetoothHub.UiMessage) -> Unit = { msg ->
            ws.broadcastUiMessage(msg)
        }
        broadcaster = bc
        BluetoothHub.addBroadcaster(bc)
        server = ws
        ws.start()
    }

    private fun stopInternal() {
        val ws = server
        if (ws != null) {
            try {
                ws.stop(500)
            } catch (e: Exception) {
                android.util.Log.w(TAG, e.message, e)
            }
        }
        server = null

        val bc = broadcaster
        if (bc != null) {
            BluetoothHub.removeBroadcaster(bc)
        }
        broadcaster = null
    }

    private fun readConfig(context: Context): Config {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabledKey = context.getString(R.string.pref_ws_enabled)
        val bindAllKey = context.getString(R.string.pref_ws_bind_all)
        val portKey = context.getString(R.string.pref_ws_port)

        val enabled = prefs.getBoolean(enabledKey, true)
        val bindAll = prefs.getBoolean(bindAllKey, false)
        val portStr = prefs.getString(portKey, "8765") ?: "8765"
        val port = portStr.toIntOrNull()?.coerceIn(1, 65535) ?: 8765

        val host = if (bindAll) "0.0.0.0" else "127.0.0.1"
        return Config(enabled, host, port)
    }

    private const val TAG = "BluetoothWS"
}
