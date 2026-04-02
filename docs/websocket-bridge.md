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

## Security

This server does not implement authentication.

- Prefer `127.0.0.1` unless you specifically need LAN access.
- If you enable `0.0.0.0`, anyone on the same network may connect.
