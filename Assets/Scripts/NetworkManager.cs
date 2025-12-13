using System;
using System.Collections;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Threading.Tasks;
using UnityEngine;
using NativeWebSocket;

/// <summary>
/// 网络管理器，负责通信和服务器发现
/// </summary>
public class NetworkManager : MonoBehaviour
{
    [Header("服务器配置")]
    public string serverIP = "192.168.1.100";
    public int serverPort = 8080;
    
    [Header("自动发现")]
    public int scanTimeout = 500; // 每个IP超时（毫秒）- 加快扫描
    public bool isScanning = false;
    public string currentScanIP = ""; // 当前正在扫描的IP
    
    [Header("状态")]
    public bool isConnected = false;
    public bool autoReconnect = true;
    private float reconnectDelay = 3f;
    private float timeSinceDisconnect = 0f;
    
    private WebSocket websocket;
    
    // 消息接收事件
    public event Action<string> OnMessageReceived;
    
    async void Start()
    {
        // 启动后自动扫描局域网
        Debug.Log("正在自动搜索服务器...");
        await System.Threading.Tasks.Task.Delay(1000); // 等待1秒初始化
        StartScan();
    }
    
    /// <summary>连接到服务器</summary>
    public async void Connect()
    {
        string url = $"ws://{serverIP}:{serverPort}";
        Debug.Log($"正在连接: {url}");
        
        websocket = new WebSocket(url);
        
        websocket.OnOpen += () =>
        {
            Debug.Log("WebSocket 连接成功!");
            isConnected = true;
        };
        
        websocket.OnError += (e) =>
        {
            Debug.LogError($"WebSocket 错误: {e}");
            isConnected = false;
        };
        
        websocket.OnClose += (e) =>
        {
            Debug.Log("WebSocket 连接关闭");
            isConnected = false;
            timeSinceDisconnect = 0f; // 重置断线计时
        };
        
        websocket.OnMessage += (bytes) =>
        {
            string message = System.Text.Encoding.UTF8.GetString(bytes);
            Debug.Log($"收到消息: {message}");
            OnMessageReceived?.Invoke(message);
        };
        
        await websocket.Connect();
    }
    
    /// <summary>发送消息</summary>
    public async void SendMessage(string message)
    {
        if (websocket != null && websocket.State == WebSocketState.Open)
        {
            await websocket.SendText(message);
            Debug.Log($"发送消息: {message}");
        }
        else
        {
            Debug.LogWarning("WebSocket 未连接");
        }
    }
    
    /// <summary>发送JSON数据</summary>
    public void SendJson(object data)
    {
        string json = JsonUtility.ToJson(data);
        SendMessage(json);
    }
    
    // ========== 网络发现功能 ==========
    
    /// <summary>获取本机IP地址</summary>
    public string GetLocalIPAddress()
    {
        try
        {
            var host = Dns.GetHostEntry(Dns.GetHostName());
            foreach (var ip in host.AddressList)
            {
                if (ip.AddressFamily == AddressFamily.InterNetwork)
                {
                    string ipStr = ip.ToString();
                    if (ipStr.StartsWith("192.168") || ipStr.StartsWith("10.") || ipStr.StartsWith("172."))
                    {
                        Debug.Log($"本机IP: {ipStr}");
                        return ipStr;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Debug.LogError($"获取本机IP失败: {ex.Message}");
        }
        
        return "127.0.0.1";
    }
    
    /// <summary>提取网段前缀</summary>
    string GetNetworkPrefix(string ip)
    {
        var parts = ip.Split('.');
        if (parts.Length >= 3)
        {
            return $"{parts[0]}.{parts[1]}.{parts[2]}";
        }
        return "192.168.1";
    }
    
    /// <summary>开始扫描局域网</summary>
    public void StartScan()
    {
        if (isScanning)
        {
            Debug.LogWarning("正在扫描中");
            return;
        }
        
        StartCoroutine(ScanNetwork());
    }
    
    /// <summary>扫描网络（持续扫描直到找到）</summary>
    IEnumerator ScanNetwork()
    {
        isScanning = true;
        currentScanIP = "";
        
        string localIP = GetLocalIPAddress();
        string networkPrefix = GetNetworkPrefix(localIP);
        
        Debug.Log($"Scanning: {networkPrefix}.1-254");
        
        int batchSize = 50;
        int scanRound = 0;
        
        // 持续扫描直到找到服务器
        while (!isConnected)
        {
            scanRound++;
            Debug.Log($"Scan round {scanRound}");
            
            for (int batch = 0; batch < 254 / batchSize + 1; batch++)
            {
                // 如果已连接，停止扫描
                if (isConnected)
                {
                    isScanning = false;
                    currentScanIP = "";
                    yield break;
                }
                
                int startIP = batch * batchSize + 1;
                int endIP = Mathf.Min(startIP + batchSize - 1, 254);
                
                if (startIP > 254) break;
                
                currentScanIP = $"{networkPrefix}.{startIP}-{endIP}";
                
                Task<string>[] tasks = new Task<string>[endIP - startIP + 1];
                
                for (int i = startIP; i <= endIP; i++)
                {
                    string targetIP = $"{networkPrefix}.{i}";
                    tasks[i - startIP] = TestConnection(targetIP, serverPort);
                }
                
                while (!tasks.All(t => t.IsCompleted))
                {
                    yield return null;
                }
                
                foreach (var task in tasks)
                {
                    if (!string.IsNullOrEmpty(task.Result))
                    {
                        serverIP = task.Result;
                        Debug.Log($"Found server: {serverIP}");
                        currentScanIP = "";
                        Connect();
                        isScanning = false;
                        yield break;
                    }
                }
            }
            
            // 一轮扫描完成，等待2秒后重新扫描
            currentScanIP = "Retry in 2s...";
            yield return new WaitForSeconds(2f);
        }
        
        isScanning = false;
    }
    
    /// <summary>测试端口连接</summary>
    async Task<string> TestConnection(string ip, int port)
    {
        try
        {
            using (var client = new TcpClient())
            {
                var connectTask = client.ConnectAsync(ip, port);
                if (await Task.WhenAny(connectTask, Task.Delay(scanTimeout)) == connectTask)
                {
                    if (client.Connected)
                    {
                        return ip;
                    }
                }
            }
        }
        catch
        {
            // 忽略
        }
        
        return null;
    }
    
    /// <summary>停止扫描</summary>
    public void StopScan()
    {
        StopAllCoroutines();
        isScanning = false;
    }
    
    // ========== Unity 生命周期 ==========
    
    void Update()
    {
        #if !UNITY_WEBGL || UNITY_EDITOR
        if (websocket != null)
        {
            websocket.DispatchMessageQueue();
        }
        #endif
        
        // 自动重连逻辑
        if (autoReconnect && !isConnected && !isScanning)
        {
            timeSinceDisconnect += Time.deltaTime;
            
            if (timeSinceDisconnect >= reconnectDelay)
            {
                Debug.Log("尝试重新连接...");
                timeSinceDisconnect = 0f;
                
                // 如果有已知服务器IP，直接连接
                if (!string.IsNullOrEmpty(serverIP) && serverIP != "192.168.1.100")
                {
                    Connect();
                }
                else
                {
                    // 否则重新扫描
                    StartScan();
                }
            }
        }
    }
    
    async void OnDestroy()
    {
        if (websocket != null)
        {
            await websocket.Close();
        }
    }
}
