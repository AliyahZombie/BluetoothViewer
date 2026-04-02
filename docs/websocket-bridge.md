# WebSocket bridge

BluetoothViewer can run an in-app WebSocket **server** and mirror Bluetooth I/O.

## Quick start

1. Open **Settings**
2. Enable **WebSocket bridge**
3. (Optional) Enable **Listen on all interfaces (0.0.0.0)**
4. Set **WebSocket port** (default: `8765`)

The server starts automatically at app startup (when enabled).

## Endpoint

The server listens on:

```
ws://<host>:<port>/
```

There is no HTTP routing and no path handling. Clients should connect to the root path.

Default host:

- `127.0.0.1` (loopback) when **Listen on all interfaces** is disabled
- `0.0.0.0` when **Listen on all interfaces** is enabled

## Outbound messages (BluetoothViewer -> client)

Messages sent to connected WebSocket clients are JSON strings.

### Status

On connect, the server sends:

```json
{"type":"status","status":"connected","port":8765}
```

When the server starts, it broadcasts:

```json
{"type":"status","status":"started","port":8765}
```

### Bluetooth I/O + app events

For each UI message (Bluetooth inbound/outbound + connection state), the server broadcasts:

```json
{"type":"in","text":"..."}
```

```json
{"type":"out","text":">>> ..."}
```

Note: the server does not add a prefix for outbound writes. The `out.text` value is the decoded bytes written to the Bluetooth socket.
In other words, expect:

```json
{"type":"out","text":"..."}
```

```json
{"type":"status","text":"connected: <device>"}
```

`type` is one of:

- `in`     : inbound Bluetooth line
- `out`    : outbound Bluetooth bytes (decoded as text)
- `status` : connection state / other status

## Inbound commands (client -> BluetoothViewer)

The server accepts either plain text or JSON.

### 1) Send data to the connected Bluetooth device

Plain text:

```
hello
```

JSON:

```json
{"type":"send","data":"hello"}
```

Notes:

- The app sends ASCII bytes and appends a newline (`\n`) on write.
- If there is no active Bluetooth connection, the send request is ignored.

### 1b) Send binary payload with metadata (binary WebSocket frame)

The server also accepts **binary WebSocket frames** to send raw bytes to the connected Bluetooth socket.

Binary frame format:

- First 4 bytes: `metaLen` (big-endian int)
- Next `metaLen` bytes: UTF-8 JSON metadata object
- Remaining bytes: payload

Metadata fields:

- `type`: must be `"send.bin"`
- `id` (optional): correlation id for the `resp`
- `appendNewline` (optional, default `false`): whether to append `0x0A` to payload

Response is a normal text JSON `resp`.

### 2) Pause / resume inbound display

```json
{"type":"pause","value":true}
```

```json
{"type":"pause","value":false}
```

When paused, inbound lines are not appended to the UI message stream.

### 3) Disconnect

```json
{"type":"disconnect"}
```

## Request/response API

In addition to the legacy commands above, the server supports JSON requests with a correlated response.

### Response envelope

Success:

```json
{"type":"resp","id":"<id>","ok":true,"result":{}}
```

Error:

```json
{"type":"resp","id":"<id>","ok":false,"error":{"message":"...","exception":"...","details":"..."}}
```

### 1) List Bluetooth devices

Request:

```json
{"id":"1","type":"devices.list","scope":"paired"}
```

`scope` can be:

- `paired` (default)
- `discovered`
- `all`

Response example:

```json
{"type":"resp","id":"1","ok":true,"result":{"devices":[{"name":"HC-05","address":"00:11:22:33:44:55","bonded":true,"rssi":null,"source":"paired"}]}}
```

### 2) Start/stop scanning (classic discovery)

Start scan:

```json
{"id":"2","type":"scan.start"}
```

Stop scan:

```json
{"id":"3","type":"scan.stop"}
```

#### Scan events (broadcast)

While scanning, the server broadcasts events:

```json
{"type":"event","name":"scan.started","data":{}}
```

```json
{"type":"event","name":"scan.deviceFound","data":{"device":{"name":"...","address":"...","bonded":false,"rssi":-71,"source":"discovered"}}}
```

```json
{"type":"event","name":"scan.finished","data":{}}
```

### 3) Connect by MAC address

Request:

```json
{"id":"4","type":"connect","address":"AA:BB:CC:DD:EE:FF"}
```

The connection state transitions are still broadcast via `{"type":"status","text":"..."}` from the Bluetooth hub.

### 4) Get current connection info

Request:

```json
{"id":"5","type":"connection.info"}
```

Response example:

```json
{"type":"resp","id":"5","ok":true,"result":{"status":"connected","deviceName":"MyDevice","deviceAddress":"AA:BB:CC:DD:EE:FF","paused":false}}
```

## Permissions

Device listing, scanning, and connecting require runtime Bluetooth permissions depending on Android version.

- Android 12+ (API 31+): `BLUETOOTH_SCAN` for scanning, `BLUETOOTH_CONNECT` for paired devices/connecting
- Android 6-11: `ACCESS_FINE_LOCATION` for scanning

## Security

This server does not implement authentication.

- Prefer `127.0.0.1` unless you specifically need LAN access.
- If you enable `0.0.0.0`, anyone on the same network may connect.
