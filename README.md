# DeepEye-PICO

<p align="center">
  <strong>跨平台 VR 眼动追踪数据采集与分析系统</strong>
</p>

<p align="center">
  <a href="#-核心特性">特性</a> •
  <a href="#-快速开始">快速开始</a> •
  <a href="#-技术架构">架构</a> •
  <a href="#-核心文件">核心文件</a> •
  <a href="#-应用场景">应用场景</a> •
  <a href="#-许可证">许可证</a>
</p>

---

## 📖 项目简介

DeepEye-PICO 是一个基于 Unity VR (PICO 4/Neo 3) 和 Android 移动端的跨平台眼动数据采集与分析系统。该系统专为心理学、人机交互和神经科学研究设计，提供完整的实验流程管理，从刺激呈现到数据可视化分析的一站式解决方案。

## ✨ 核心特性

### 🎯 实时眼动追踪
- 基于 PICO Eye Tracking SDK，采集注视点坐标、方向及置信度
- 支持双眼合并数据输出，60Hz+ 采样率
- 世界坐标与屏幕像素坐标自动映射

### 🌐 智能网络发现
- 自动扫描局域网内的 Android 控制端（Port 8080）
- 无需手动配置 IP，开机即连
- WebSocket 长连接，断线自动重连

### 📱 移动端控制
- Android App 远程控制实验流程
- 实时监控 VR 端画面与实验进度
- 测试集管理：创建、编辑、删除测试集
- 图片库管理：从相册导入、预览、删除

### 🖼️ 动态刺激呈现
- 支持网络下发图片刺激（Base64 编码）
- 16:9 适配显示，纯黑背景减少干扰
- 自定义呈现时长和图片序列

### 💾 标准化输出
- 导出 JSON/TXT 格式数据
- 包含时间戳、注视点坐标、图片索引、有效性标记
- 兼容主流眼动分析工具（如 PyGaze, EyeLink）

### 🎮 VR 交互
- **右手柄 A 键**：显示/隐藏注视点激光射线
- **右手柄 B 键**：显示/隐藏 UI 面板（坐标、网络状态）

## 🔧 技术架构

### 技术栈
- **VR 端**: Unity 2021.3+, PICO Integration SDK, C#, NativeWebSocket
- **控制端**: Android (Java), WebSocket Server, SharedPreferences, Gson
- **通信协议**: 自定义 JSON 消息格式, TCP/WebSocket

### 系统架构
```
┌─────────────────────┐         WebSocket         ┌──────────────────────┐
│   Unity VR Client   │◄────────(Port 8080)──────►│  Android Server      │
│   (PICO 4/Neo 3)    │                            │  (控制端)            │
├─────────────────────┤                            ├──────────────────────┤
│ • Eye Tracking SDK  │   ┌─────────────────┐     │ • 测试集管理         │
│ • NetworkManager    │   │  Message Flow   │     │ • 实验控制           │
│ • ExperimentCtrl    │   ├─────────────────┤     │ • 数据存储           │
│ • CoordinateMapper  │   │ PING/PONG       │     │ • 可视化回放         │
│ • Auto Discovery    │   │ IMAGE (Base64)  │     │ • 文件导出           │
│                     │   │ START/STOP      │     │                      │
│                     │   │ DATA (JSON)     │     │                      │
└─────────────────────┘   └─────────────────┘     └──────────────────────┘
```

### 核心算法

#### 1. 场景设计
- **相机**: 位于世界坐标原点 (0, 0, 0)，纯黑背景
- **虚拟屏幕**: Z=10 平面，物理尺寸 X: [-8, 8], Y: [-4.5, 4.5] (16:9)

#### 2. 眼动数据采集
```csharp
// 伪代码示例
1. 获取 PICO SDK 注视姿态 (orientation)
2. 转换为世界坐标系注视向量: worldGazeDirection = rotation * Vector3.forward
3. 从相机发射物理射线: Physics.Raycast(origin, direction, out hit)
4. 坐标映射: 
   relativeX = ((hit.x - (-8)) / 16) * 1600
   relativeY = ((hit.y - (-4.5)) / 9) * 900
5. 输出: {x, y, timestamp, imageIndex, isValid}
```

#### 3. 数据通信
- **连接**: TCP 端口扫描 → WebSocket 握手
- **图片下发**: Base64 编码 → JSON 封装 → 分包传输
- **数据回传**: 内存批量暂存 → 实验结束统一序列化 → WebSocket 传输

## 📁 核心文件说明

### Unity 客户端关键脚本
| 文件 | 功能 |
|------|------|
| `EyeGazeMarker.cs` | PICO 眼动 SDK 集成与原始数据获取 |
| `ExperimentController.cs` | 实验流程控制、图片呈现、数据记录 |
| `NetworkManager.cs` | WebSocket 客户端、自动发现、消息处理 |

### Android 服务器关键组件
| 文件 | 功能 |
|------|------|
| `EyeTrackingServer.java` | WebSocket 服务器实现（端口 8080） |
| `TestSetListActivity.java` | 测试集列表管理 |
| `TestSetDetailActivity.java` | 测试集内图片管理 |
| `ExperimentActivity.java` | 实验流程控制与监控 |
| `DataViewerActivity.java` | 眼动数据回放可视化 |
| `EyeTrackingView.java` | 自定义 Canvas 绘制轨迹 |
| `DataManager.java` | SharedPreferences 数据持久化 |

## 🚀 快速开始

### 环境要求
- **Unity**: 2021.3+
  - PICO Integration SDK
  - XR Interaction Toolkit
- **Android**:
  - Android Studio (最新版)
  - JDK 11+
  - Android 7.0+ 设备
- **硬件**:
  - PICO 4 / PICO Neo 3 Pro Eye (需支持眼动追踪)

### 部署步骤

#### 1. 部署 Android 控制端
```bash
# 打开项目
cd server/DeepEyeApp
# 使用 Android Studio 打开项目并编译安装
```
1. 连接 Android 手机，编译并安装应用
2. **手机开启个人热点**，确保 PICO 设备连接到该热点
3. 打开应用，授予存储和网络权限

#### 2. 部署 Unity VR 端
1. 使用 Unity 打开项目根目录
2. 确保已配置 PICO 开发环境
3. Build Settings → Android → 连接 PICO 头显 → Build and Run

#### 3. 开始实验
1. **启动控制端**: 打开 Android 应用 → "测试集管理" → 创建测试集 → 添加图片
2. **连接设备**: VR 应用启动后自动搜索并连接手机（查看左下角网络状态）
3. **下发图片**: 选择测试集 → "开始测试" → "发送图片到 VR"
4. **开始采集**: 点击 "开始实验"，VR 端开始显示图片并记录眼动数据
5. **结束保存**: 点击 "停止实验"，数据自动回传 → 选择保存格式（JSON/TXT）

## 📊 数据格式

### JSON 输出示例
```json
{
  "id": "sample_20231213_143022",
  "testSetId": "testset_001",
  "testSetName": "认知负荷实验_图片集A",
  "timestamp": 1702449022000,
  "eyeTrackingData": [
    {
      "x": 960.0,
      "y": 540.0,
      "timestamp": 123456,
      "imageIndex": 0,
      "isValid": true
    },
    {
      "x": 1024.5,
      "y": 512.3,
      "timestamp": 123473,
      "imageIndex": 0,
      "isValid": true
    }
  ]
}
```

### 字段说明
- `x, y`: 屏幕像素坐标（基于 1600x900 映射）
- `timestamp`: 毫秒级时间戳（相对实验开始时间）
- `imageIndex`: 当前显示的图片索引
- `isValid`: 注视点有效性（是否命中虚拟屏幕）

## 🎯 应用场景

- **心理学实验**: 视觉注意、认知负荷评估、情绪识别
- **用户体验测试**: VR 界面评估、广告效果研究、产品设计优化
- **医疗康复**: 注意力训练、神经反馈治疗、视觉康复评估
- **教育研究**: 学习过程中的视觉行为分析、教学材料优化

## ⚠️ 注意事项

- 务必确保手机开启个人热点，且 PICO 4 连接到该热点
- **图片素材建议**: 使用 **16:9 比例**（如 1920x1080）图片以获得最佳显示效果
- PICO 设备需开启开发者模式并允许 USB 调试
- Android 应用在后台可能被系统杀掉，实验过程中请保持前台运行
- 实验前请完成 PICO 眼动校准 (Eye Tracking Calibration)

## 🔗 相关资源

- [PICO 开发者文档](https://developer.pico-interactive.com/)
- [Unity XR Interaction Toolkit](https://docs.unity3d.com/Packages/com.unity.xr.interaction.toolkit@latest)
- [眼动追踪研究指南](https://www.tobiipro.com/learn-and-support/learn/eye-tracking-essentials/)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！请确保：
- 代码符合项目编码规范
- 提交前运行测试并通过
- 更新相关文档

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

