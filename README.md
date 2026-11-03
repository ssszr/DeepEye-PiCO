# DeepEye-PICO

PICO VR 眼动采集与 Android 控制端联调系统。VR 端负责刺激呈现与注视采样，Android 端负责测试集管理、实验控制和数据回放。两端通过局域网 WebSocket 协同。

## 系统组成

```
Unity VR (PICO 4 / Neo 3)                      Android 控制端
-------------------------                      --------------
PICO Motion Tracking SDK                       WebSocket Server :8080
EyeGazeMarker      注视射线 / 平面求交          测试集 / 图片库
ExperimentController  刺激时序 / 采样缓冲       实验控制与进度
NetworkManager     网段扫描 / 断线重连          SharedPreferences 持久化
                                               EyeTrackingView 轨迹回放
```

技术栈：

| 端 | 运行时 | 关键依赖 |
| --- | --- | --- |
| VR | Unity 2022.3.62f2c1，C# | PICO XR SDK（`com.unity.xr.picoxr`）、NativeWebSocket、XR Interaction Toolkit |
| 控制端 | Android 7.0+，Java | Java-WebSocket，Gson，SharedPreferences |

硬件约束：PICO 设备必须支持眼动追踪（PICO 4 / Neo 3 Pro Eye）。实验前需完成头显眼动校准。两端必须在同一二层网络；当前部署方式是手机开热点，头显接入该热点。

## 坐标系与刺激平面

相机置于世界原点，背景固定为纯黑，避免非刺激区域反光干扰注视。

虚拟屏幕是 `Z = 10` 处的 Quad，物理范围：

```
X ∈ [-8, 8],  Y ∈ [-4.5, 4.5]     （宽高比 16:9）
```

注视命中后线性映射到逻辑像素空间 `1600 × 900`：

```
sx = (hit.x - (-8)) / 16 * 1600
sy = (hit.y - (-4.5)) / 9  * 900
```

结果 clamp 到 `[0, 1600] × [0, 900]`。未命中或 `hit.z ≠ 10` 时输出 `(-1, -1)`，并标记 `isValid = false`。

刺激图建议使用 16:9（例如 1920×1080）。非该比例仍会贴到同一 Quad，但注视坐标与原图像素不再一一对应。

## 眼动采集管线

实现见 `Assets/Scripts/EyeGazeMarker.cs`。每帧流程：

1. `GetEyeTrackingSupported` / `StartEyeTracking`：双眼模式（`PXR_ETM_BOTH`），要求校准。
2. `GetEyeTrackingState` 确认 tracking 已启动。
3. `GetEyeTrackingData` 取 Combined 眼的 position + orientation。`isPoseValid ≠ 1` 则本帧丢弃。
4. 将 SDK 姿态转为世界空间注视方向：

```
worldGazeDirection = orientation * Vector3.forward
worldGazeDirection = (-x, -y, z)      // PICO 与 Unity 轴向差，必须翻转
```

5. 从相机位置沿该方向做 `Physics.Raycast`，长度 20，仅打 `interactLayer`。
6. 命中点经上一节公式映射为屏幕坐标，写入 `ExperimentController.RecordEyeData`。

采样率跟随 Unity 帧率，不做硬件时钟对齐。时间戳为 UTC Unix 毫秒。有效性只表示射线是否打到刺激平面，不是 SDK 置信度分数。

右手柄：A 切换注视射线，B 切换调试 UI（命中坐标、网络状态）。

`PicoEyeTracker.cs` 是早期 SDK 联调脚本，正式采集路径以 `EyeGazeMarker` 为准。

## 实验状态机

`ExperimentController` 状态：`Idle → Ready → Running → Finished`。

| 事件 | 行为 |
| --- | --- |
| 收到 `image` | Base64 解码为 `Texture2D` 入列；首包 `CLEAR_START:` 先清空旧图。回 `image_received`。 |
| `start` | 要求已有图片。清空采样缓冲，按 `imageDuration`（默认 5s）顺序呈现。 |
| 呈现中 | 每切一张图向控制端发 `progress`。仅 `Running` 时写入采样点。 |
| 序列结束 | 发 `finished`，再把内存中的全部采样点一次序列化上传（`type=data`）。 |
| `stop` | 立即回到 `Idle`，隐藏刺激，不上传。 |

图片在实验开始前整包下发，运行期不再走网络读图。采集数据驻留 VR 内存，实验结束才回传，避免采样路径上的序列化开销。

控制端发送间隔 500ms/张，防止 WebSocket 背压把后续控制消息挤掉。

## 网络发现与连接

控制端 `EyeTrackingServer` 监听 `0.0.0.0:8080`。选本机展示 IP 时按网段优先级：`192.168.*`（热点）> `172.*` > `10.*`。

VR 端 `NetworkManager` 启动后自动发现，不手填 IP：

1. 取本机 IPv4，截 `/24` 前缀。
2. 对 `.1–.254` 分批 TCP 探测 8080（每批 50，单 IP 超时 500ms）。
3. 首个连通地址作为 `serverIP`，升级为 `ws://ip:8080`。
4. 本轮失败则等待 2s 再扫，直到连上。
5. 断线后 3s 重连：已有有效 IP 则直连，否则重新扫描。

这是端口可达性探测，不是服务指纹。同网段其他进程占用 8080 会被误判。

## 通信协议

消息均为 JSON 文本，字段 `type` 区分用途。

控制端 → VR

| type | 载荷 | 说明 |
| --- | --- | --- |
| `image` | `data`: Base64；首张前缀 `CLEAR_START:` | 下发刺激图 |
| `start` | 无 | 开始呈现与采样 |
| `stop` | 无 | 中止，不上传 |
| `config` | `data` | 预留，VR 端尚未落地 |

VR → 控制端

| type | 载荷 | 说明 |
| --- | --- | --- |
| `image_received` | `count` | 当前已缓存图片数 |
| `progress` | `current`, `total` | 当前刺激序号（1-based） |
| `finished` | 无 | 序列播放完毕 |
| `data` | `payload` | 见下一节 |

## 数据模型

VR 回传 payload：

```json
{
  "totalPoints": 2,
  "data": [
    {
      "timestamp": 1702449022123,
      "imageIndex": 0,
      "gazeWorldPos": { "x": 0.12, "y": -0.04, "z": 10.0 },
      "screenPos": { "x": 960.0, "y": 540.0 },
      "isValid": true
    }
  ]
}
```

| 字段 | 含义 |
| --- | --- |
| `timestamp` | UTC Unix 毫秒 |
| `imageIndex` | 当前刺激下标，从 0 计 |
| `gazeWorldPos` | 射线命中世界坐标；未命中为零向量 |
| `screenPos` | 1600×900 逻辑像素；无效为 `(-1, -1)` |
| `isValid` | 是否命中刺激平面 |

控制端可将样本存 JSON / TXT，或写入 `DataSample`（按图片切分时序）。本地存储走 SharedPreferences + Gson，适合联调，不适合大规模样本。

回放（`EyeTrackingView`）把 `screenPos` 归一化到 `[0, 1]`，叠加在刺激图上画点和轨迹，支持按进度拖动。

## 关键文件

VR（`Assets/Scripts/`）

| 文件 | 职责 |
| --- | --- |
| `EyeGazeMarker.cs` | SDK 接入、轴向校正、平面求交、坐标映射 |
| `ExperimentController.cs` | 状态机、刺激时序、采样缓冲、回传 |
| `NetworkManager.cs` | 网段扫描、WebSocket、断线重连 |
| `PicoEyeTracker.cs` | SDK 联调用，非正式采集路径 |

Android（`server/DeepEyeApp/app/src/main/java/com/chuangjie/deepeyeapp/`）

| 文件 | 职责 |
| --- | --- |
| `EyeTrackingServer.java` | WebSocket 服务，端口 8080 |
| `ExperimentActivity.java` | 下发图片、启停、收数据 |
| `TestSetListActivity.java` / `TestSetDetailActivity.java` | 测试集与图片 |
| `DataViewerActivity.java` | 按图回放轨迹 |
| `views/EyeTrackingView.java` | Canvas 叠加绘制 |
| `storage/DataManager.java` | SharedPreferences 持久化 |
| `models/DataSample.java` / `models/TestSet.java` | 样本与测试集结构 |

## 部署

环境：Unity 2022.3.62f2c1 + PICO XR SDK；Android Studio + JDK 11；支持眼动的 PICO 头显；Android 7.0+ 控制机。

1. 控制端：Android Studio 打开 `server/DeepEyeApp`，安装到手机。开个人热点，头显加入该热点。授予存储与网络权限，保持应用前台（后台可能被杀）。
2. VR 端：Unity 打开仓库根目录，确认 PICO 开发环境与 USB 调试。Build Settings → Android → Build and Run。启动后左下角应出现扫描/已连接状态。
3. 实验：控制端建测试集并导入图片 → 发送到 VR → 开始实验 → 结束后查看回放或导出 JSON/TXT。

头显需先完成系统眼动校准。图片按 16:9 准备。

## 已知工程限制

- 注视方向的 XY 翻转是针对当前 PICO SDK 约定写死的，升级 SDK 后需重新验证。
- 逻辑分辨率固定 1600×900，与刺激原图像素无关。
- `isValid` 不是 fixation / saccade 分类，下游分析需自行做。
- 发现算法假定 /24，跨网段或隔离客户端不可达。
- 图片与样本存在 SharedPreferences，体积大时会触及系统配额。
- `config` 消息未实现；单次呈现时长目前只能改 VR 端 `imageDuration`。
