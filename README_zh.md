# BluetoothViewer - Enhanced

[English](README.md) | [中文]

一款轻量级的蓝牙连接调试与数据转发工具。在原开源项目基础上进行了精简，并新增了 WebSocket 桥接功能。

## 核心功能

1. **蓝牙调试**：
   - 连接至蓝牙设备（服务端模式）。
   - 实时显示接收到的原始数据。
   - 向蓝牙设备发送原始指令。
2. **WebSocket 桥接（二开新增）**：
   - **内置服务器**：App 内部运行一个 WebSocket 服务端，镜像所有蓝牙 I/O 数据。
   - **远程同步**：你可以通过局域网内的任何 WebSocket 客户端（如 PC 浏览器、脚本）实时监控和控制蓝牙设备。
协议可见：[docs/websocket-bridge.md](docs/websocket-bridge.md)


## 技术要求

本工具适用于满足以下条件的蓝牙设备：
* 支持 **服务端模式 (Server Mode)**，即接受外部连接。
* 在 **Channel 1** 上进行监听。
* 无需特定的自定义 UUID 即可完成配对连接。

## WebSocket 桥接说明

开启桥接功能后：
* 蓝牙接收到的任何数据都会同步广播给所有已连接的 WebSocket 客户端。
* 任何通过 WebSocket 发送给 App 的数据都会被转发给蓝牙设备。

## 免责声明与协议

- 本项目是基于 https://github.com/janosgyerik/bluetoothviewer 的二次开发版本。
- 本项目遵循 **Apache License 2.0** 协议，详情请参阅 `LICENSE` 文件。
