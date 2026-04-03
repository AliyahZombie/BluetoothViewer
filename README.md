# BluetoothViewer - Enhanced

[English] | [中文](README_zh.md)

A lightweight tool for Bluetooth connection debugging and data synchronization. Base on the original BluetoothViewer project with enhanced features.

## Key Features

1. **Bluetooth Debugging**: 
   - Connect to classic Bluetooth devices (Server mode).
   - Display incoming raw data in real-time.
   - Send raw data back to the device.
2. **WebSocket Bridge**: 
   - **Internal WebSocket Server**: This app runs an in-app WebSocket server to mirror Bluetooth I/O.
   - **Remote Control**: You can monitor and interact with the Bluetooth device from any WebSocket client (PC, Web browser, etc.) over the local network.
   - See: [docs/websocket-bridge.md](docs/websocket-bridge.md)


## Technical Requirements

The app works with Bluetooth devices that meet the following criteria:
* Operates in **Server Mode** (accepts incoming connections).
* Listens on **Channel 1**.
* Pairs and connects without requiring a specific custom UUID.

## How it works (WebSocket Bridge)

Once the WebSocket bridge is enabled:
* Incoming Bluetooth data is broadcasted to all connected WebSocket clients.
* Data sent to the WebSocket server is forwarded to the connected Bluetooth device.

## Disclaimer & License

- This project is a modified version of the original https://github.com/janosgyerik/bluetoothviewer , which was itself based on the Android SDK BluetoothChat sample.
- Licensed under the **Apache License 2.0**. See the `LICENSE` file for details.
