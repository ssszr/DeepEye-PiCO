using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

/// <summary>
/// 实验控制器，管理实验流程
/// </summary>
public class ExperimentController : MonoBehaviour
{
    [Header("引用")]
    public NetworkManager networkManager;
    public Image displayImage;  // 用于显示图片的 UI Image
    
    [Header("实验设置")]
    public float imageDuration = 5f;  // 每张图片显示时长（秒）
    
    [Header("状态")]
    public ExperimentState currentState = ExperimentState.Idle;
    
    // 实验数据
    private List<Texture2D> imageList = new List<Texture2D>();
    private int currentImageIndex = 0;
    
    // 眼动数据采集
    private List<EyeDataPoint> collectedData = new List<EyeDataPoint>();
    
    public enum ExperimentState
    {
        Idle,       // 待机
        Ready,      // 准备就绪（已加载图片）
        Running,    // 实验进行中
        Finished    // 实验结束
    }
    
    void Start()
    {
        // 监听网络消息
        if (networkManager != null)
        {
            networkManager.OnMessageReceived += HandleNetworkMessage;
        }
        
        // 隐藏图片显示
        if (displayImage != null)
        {
            displayImage.gameObject.SetActive(false);
        }
    }
    
    /// <summary>处理网络消息</summary>
    void HandleNetworkMessage(string message)
    {
        Debug.Log($"实验控制器收到消息: {message}");
        
        // 解析 JSON 消息
        try
        {
            var msg = JsonUtility.FromJson<NetworkMessage>(message);
            
            switch (msg.type)
            {
                case "start":
                    StartExperiment();
                    break;
                    
                case "stop":
                    StopExperiment();
                    break;
                    
                case "image":
                    ReceiveImage(msg.data);
                    break;
                    
                case "config":
                    ApplyConfig(msg.data);
                    break;
            }
        }
        catch (Exception e)
        {
            Debug.LogError($"解析消息失败: {e.Message}");
        }
    }
    
    /// <summary>接收Base64图片数据</summary>
    void ReceiveImage(string base64Data)
    {
        try
        {
            // 如果是第一张图片，清空之前的图片列表
            if (base64Data.StartsWith("CLEAR_START:"))
            {
                ClearImages();
                base64Data = base64Data.Substring(12); // 移除标记
                Debug.Log("清空图片列表，开始接收新图片");
            }
            
            byte[] imageBytes = Convert.FromBase64String(base64Data);
            Texture2D tex = new Texture2D(2, 2);
            tex.LoadImage(imageBytes);
            imageList.Add(tex);
            
            Debug.Log($"收到图片，当前共 {imageList.Count} 张");
            
            if (currentState == ExperimentState.Idle)
            {
                currentState = ExperimentState.Ready;
            }
            
            // 通知手机图片接收成功
            networkManager.SendMessage($"{{\"type\":\"image_received\",\"count\":{imageList.Count}}}");
        }
        catch (Exception e)
        {
            Debug.LogError($"加载图片失败: {e.Message}");
        }
    }
    
    /// <summary>应用实验配置</summary>
    void ApplyConfig(string configJson)
    {
        Debug.Log($"应用配置: {configJson}");
    }
    
    /// <summary>开始实验</summary>
    public void StartExperiment()
    {
        if (currentState != ExperimentState.Ready && currentState != ExperimentState.Idle)
        {
            Debug.LogWarning("实验状态不对，无法开始");
            return;
        }
        
        if (imageList.Count == 0)
        {
            Debug.LogWarning("没有图片，无法开始实验");
            return;
        }
        
        Debug.Log("开始实验!");
        currentState = ExperimentState.Running;
        currentImageIndex = 0;
        collectedData.Clear();
        
        StartCoroutine(RunExperimentLoop());
    }
    
    /// <summary>实验主循环</summary>
    IEnumerator RunExperimentLoop()
    {
        for (int i = 0; i < imageList.Count; i++)
        {
            currentImageIndex = i;
            ShowImage(i);
            
            float elapsed = 0;
            while (elapsed < imageDuration && currentState == ExperimentState.Running)
            {
                elapsed += Time.deltaTime;
                yield return null;
            }
            
            if (currentState != ExperimentState.Running)
                break;
        }
        
        FinishExperiment();
    }
    
    /// <summary>显示图片</summary>
    void ShowImage(int index)
    {
        if (displayImage != null && index < imageList.Count)
        {
            Sprite sprite = Sprite.Create(
                imageList[index],
                new Rect(0, 0, imageList[index].width, imageList[index].height),
                new Vector2(0.5f, 0.5f)
            );
            displayImage.sprite = sprite;
            displayImage.gameObject.SetActive(true);
            
            Debug.Log($"显示图片 {index + 1}/{imageList.Count}");
            
            networkManager.SendMessage($"{{\"type\":\"progress\",\"current\":{index + 1},\"total\":{imageList.Count}}}");
        }
    }
    
    /// <summary>停止实验</summary>
    public void StopExperiment()
    {
        Debug.Log("停止实验");
        currentState = ExperimentState.Idle;
        
        if (displayImage != null)
        {
            displayImage.gameObject.SetActive(false);
        }
    }
    
    /// <summary>完成实验</summary>
    void FinishExperiment()
    {
        Debug.Log("实验完成!");
        currentState = ExperimentState.Finished;
        
        if (displayImage != null)
        {
            displayImage.gameObject.SetActive(false);
        }
        
        networkManager.SendMessage("{\"type\":\"finished\"}");
        UploadData();
    }
    
    /// <summary>记录眼动数据点</summary>
    public void RecordEyeData(Vector3 gazePosition, Vector2 screenPosition, bool isValid)
    {
        if (currentState == ExperimentState.Running)
        {
            var dataPoint = new EyeDataPoint
            {
                timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                imageIndex = currentImageIndex,
                gazeWorldPos = gazePosition,
                screenPos = screenPosition,
                isValid = isValid
            };
            
            collectedData.Add(dataPoint);
        }
    }
    
    /// <summary>上传数据</summary>
    void UploadData()
    {
        if (collectedData.Count == 0)
        {
            Debug.LogWarning("没有数据可上传");
            return;
        }
        
        // 转换为 JSON
        var dataPackage = new DataPackage
        {
            totalPoints = collectedData.Count,
            data = collectedData
        };
        
        string json = JsonUtility.ToJson(dataPackage);
        networkManager.SendMessage($"{{\"type\":\"data\",\"payload\":{json}}}");
        
        Debug.Log($"上传了 {collectedData.Count} 条眼动数据");
    }
    
    /// <summary>清空图片列表</summary>
    public void ClearImages()
    {
        imageList.Clear();
        currentState = ExperimentState.Idle;
        Debug.Log("图片列表已清空");
    }
}

// 数据结构
[Serializable]
public class NetworkMessage
{
    public string type;
    public string data;
}

[Serializable]
public class EyeDataPoint
{
    public long timestamp;
    public int imageIndex;
    public Vector3 gazeWorldPos;
    public Vector2 screenPos;
    public bool isValid;
}

[Serializable]
public class DataPackage
{
    public int totalPoints;
    public List<EyeDataPoint> data;
}
