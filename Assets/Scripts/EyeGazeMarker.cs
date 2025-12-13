using System;
using TMPro;
using Unity.XR.PXR;
using UnityEngine;
using UnityEngine.XR;

public class EyeGazeMarker : MonoBehaviour
{
    [Header("UI References")]
    public TMP_Text ui_text;          // UI文本显示
    public GameObject uiPanel;         // UI面板，用于整体隐藏/显示
    
    [Header("Gaze Visualization")]
    public LineRenderer gazeRay;      // 注视射线渲染器
    public float rayLength = 20f;     // 射线长度
    
    [Header("Interaction Settings")]
    public LayerMask interactLayer;   // 可交互层
    public string quadTagName = "Quad"; // Quad标签名
    
    [Header("Experiment Integration")]
    public ExperimentController experimentController;  // 实验控制器
    public NetworkManager networkManager;  // 网络管理器
    
    [Header("Background Settings")]
    public Camera mainCamera;          // 主相机，用于设置背景色
    
    // 私有变量
    private bool trackingSupported = false;
    private bool isTracking = false;
    private EyeTrackingData eyeData;
    private InputDevice rightHandDevice; // 右手柄设备
    private bool isLaserVisible = true; // 激光显示状态
    private bool isUIVisible = true;    // UI显示状态
    private bool aButtonPressedLastFrame = false; // A键上一帧状态
    private bool bButtonPressedLastFrame = false; // B键上一帧状态

    void Start()
    {
        // 初始化眼动数据结构
        eyeData = new EyeTrackingData();
        eyeData.SetVersion(PXR_MotionTracking.PXR_EYE_TRACKING_API_VERSION);

        // 初始化眼动追踪
        InitializeEyeTracking();

        // 初始化射线渲染器
        if (gazeRay != null)
        {
            gazeRay.enabled = isLaserVisible;
            gazeRay.positionCount = 2;
        }

        // 初始化右手柄设备
        rightHandDevice = InputDevices.GetDeviceAtXRNode(XRNode.RightHand);
        
        // 设置相机背景为纯黑色
        if (mainCamera == null)
        {
            mainCamera = Camera.main;
        }
        
        if (mainCamera != null)
        {
            mainCamera.backgroundColor = Color.black;
            mainCamera.clearFlags = CameraClearFlags.SolidColor;
            Debug.Log("已设置相机背景为纯黑色");
        }
        
        // 如果没有指定UI面板，尝试自动获取
        if (uiPanel == null && ui_text != null)
        {
            uiPanel = ui_text.transform.parent?.gameObject;
        }

        // 初始化UI
        UpdateUIText("Initializing...", Vector3.zero);
    }

    void Update()
    {
        if (!trackingSupported) return;

        // 检查A键输入（切换激光）
        CheckAButtonInput();
        
        // 检查B键输入（切换UI）
        CheckBButtonInput();

        // 更新眼动追踪状态
        UpdateTrackingState();

        if (isTracking)
        {
            // 处理眼动数据
            ProcessEyeTrackingData();
        }
        else
        {
            // 隐藏可视化元素
            HideGazeVisuals();
            UpdateUIText("Eye Tracking: Stopped", Vector3.zero);
        }
    }

    /// <summary>
    /// 检查A键输入，切换激光显示
    /// </summary>
    void CheckAButtonInput()
    {
        if (!rightHandDevice.isValid)
        {
            rightHandDevice = InputDevices.GetDeviceAtXRNode(XRNode.RightHand);
            return;
        }

        bool aButtonCurrentState;
        if (rightHandDevice.TryGetFeatureValue(CommonUsages.primaryButton, out aButtonCurrentState))
        {
            if (aButtonCurrentState && !aButtonPressedLastFrame)
            {
                isLaserVisible = !isLaserVisible;
                if (gazeRay != null)
                {
                    gazeRay.enabled = isLaserVisible;
                }
                Debug.Log($"激光显示: {(isLaserVisible ? "开启" : "关闭")}");
            }
            aButtonPressedLastFrame = aButtonCurrentState;
        }
    }
    
    /// <summary>
    /// 检查B键输入，切换UI显示
    /// </summary>
    void CheckBButtonInput()
    {
        if (!rightHandDevice.isValid)
        {
            rightHandDevice = InputDevices.GetDeviceAtXRNode(XRNode.RightHand);
            return;
        }

        bool bButtonCurrentState;
        if (rightHandDevice.TryGetFeatureValue(CommonUsages.secondaryButton, out bButtonCurrentState))
        {
            if (bButtonCurrentState && !bButtonPressedLastFrame)
            {
                // B键按下，切换UI状态
                isUIVisible = !isUIVisible;
                
                if (uiPanel != null)
                {
                    uiPanel.SetActive(isUIVisible);
                }
                else if (ui_text != null)
                {
                    ui_text.gameObject.SetActive(isUIVisible);
                }
                
                Debug.Log($"UI显示: {(isUIVisible ? "开启" : "关闭")}");
            }
            bButtonPressedLastFrame = bButtonCurrentState;
        }
    }

    /// <summary>初始化眼动追踪</summary>
    void InitializeEyeTracking()
    {
        int supportedCount = 0;
        EyeTrackingMode[] modes = new EyeTrackingMode[1];
        trackingSupported = PXR_MotionTracking.GetEyeTrackingSupported(ref trackingSupported, ref supportedCount, ref modes) == 0 && trackingSupported;
        if (trackingSupported)
        {
            EyeTrackingStartInfo startInfo = new EyeTrackingStartInfo();
            startInfo.needCalibration = 1;
            startInfo.mode = EyeTrackingMode.PXR_ETM_BOTH;
            int startResult = PXR_MotionTracking.StartEyeTracking(ref startInfo);
            if (startResult != 0)
            {
                trackingSupported = false;
                UpdateUIText($"Start Failed. Code: {startResult}", Vector3.zero);
            }
        }
        else
        {
            UpdateUIText("Eye Tracking: Not Supported", Vector3.zero);
        }
    }

    /// <summary>更新眼动追踪状态</summary>
    void UpdateTrackingState()
    {
        EyeTrackingState eyeState = new EyeTrackingState();
        int stateResult = PXR_MotionTracking.GetEyeTrackingState(ref isTracking, ref eyeState);
        if (stateResult != 0) isTracking = false;
    }

    /// <summary>处理眼动数据并更新可视化</summary>
    void ProcessEyeTrackingData()
    {
        EyeTrackingDataGetInfo getInfo = new EyeTrackingDataGetInfo();
        getInfo.SetVersion(PXR_MotionTracking.PXR_EYE_TRACKING_API_VERSION);
        getInfo.flags = EyeTrackingDataGetFlags.PXR_EYE_POSITION | EyeTrackingDataGetFlags.PXR_EYE_ORIENTATION;
        int dataResult = PXR_MotionTracking.GetEyeTrackingData(ref getInfo, ref eyeData);
        if (dataResult != 0)
        {
            HideGazeVisuals();
            UpdateUIText("Failed to get eye data", Vector3.zero);
            return;
        }

        var combinedEye = eyeData.eyeDatas[(int)PerEyeUsage.Combined];
        if (combinedEye.isPoseValid != 1)
        {
            HideGazeVisuals();
            UpdateUIText("Eye data invalid", Vector3.zero);
            return;
        }

        Vector3 worldEyePosition = Camera.main.transform.position;
        Quaternion worldEyeRotation = combinedEye.pose.orientation.ToQuat();
        Vector3 worldGazeDirection = worldEyeRotation * Vector3.forward;
        worldGazeDirection = new Vector3(-worldGazeDirection.x, -worldGazeDirection.y, worldGazeDirection.z);

        Debug.DrawRay(worldEyePosition, worldGazeDirection * rayLength, Color.red, 0.02f);
        if (isLaserVisible) UpdateGazeRay(worldEyePosition, worldGazeDirection);

        // 射线检测
        bool hasHit = Physics.Raycast(worldEyePosition, worldGazeDirection, out RaycastHit hit, rayLength, interactLayer);
        Vector3 hitPoint = hasHit ? hit.point : Vector3.zero;
        
        // 更新UI显示
        if (hasHit)
        {
            UpdateUIText(hit.collider.CompareTag(quadTagName) ? $"Hit: {quadTagName}" : $"Hit: {hit.collider.name}", hitPoint);
        }
        else
        {
            UpdateUIText("No hit", Vector3.zero);
        }
        
        // 记录眼动数据
        if (experimentController != null)
        {
            Vector2 screenPos = CalculateScreenPosition(hitPoint);
            experimentController.RecordEyeData(hitPoint, screenPos, hasHit);
        }
    }

    /// <summary>更新注视射线</summary>
    void UpdateGazeRay(Vector3 origin, Vector3 direction)
    {
        if (gazeRay == null) return;
        gazeRay.SetPosition(0, origin);
        gazeRay.SetPosition(1, origin + direction * rayLength);
    }

    /// <summary>隐藏可视化元素</summary>
    void HideGazeVisuals()
    {
        if (gazeRay != null) gazeRay.enabled = false;
    }

    /// <summary>计算屏幕相对坐标</summary>
    Vector2 CalculateScreenPosition(Vector3 hitPosition)
    {
        if (hitPosition == Vector3.zero || hitPosition.z != 10f)
        {
            return new Vector2(-1, -1);  // 无效坐标
        }
        
        // 坐标映射参数
        float left = -8f, right = 8f, top = 4.5f, bottom = -4.5f;
        float targetWidth = 1600f, targetHeight = 900f;
        float relativeX = ((hitPosition.x - left) / (right - left)) * targetWidth;
        float relativeY = ((hitPosition.y - bottom) / (top - bottom)) * targetHeight;
        relativeX = Mathf.Clamp(relativeX, 0f, targetWidth);
        relativeY = Mathf.Clamp(relativeY, 0f, targetHeight);
        
        return new Vector2(relativeX, relativeY);
    }
    
    /// <summary>更新UI文本显示</summary>
    void UpdateUIText(string status, Vector3 hitPosition)
    {
        if (ui_text == null) return;
        string positionText = hitPosition == Vector3.zero ? "X: N/A, Y: N/A, Z: N/A" : $"X: {hitPosition.x:F3}, Y: {hitPosition.y:F3}, Z: {hitPosition.z:F3}";
        string relativeCoordText = "Relative: N/A";
        
        Vector2 screenPos = CalculateScreenPosition(hitPosition);
        if (screenPos.x >= 0 && screenPos.y >= 0)
        {
            relativeCoordText = $"Relative: X: {screenPos.x:F1}, Y: {screenPos.y:F1}";
        }
        
        // 网络连接状态
        string networkStatus = "Network: Not configured";
        if (networkManager != null)
        {
            if (networkManager.isConnected)
            {
                networkStatus = $"Network: Connected ({networkManager.serverIP})";
            }
            else if (networkManager.isScanning)
            {
                networkStatus = $"Network: Scanning {networkManager.currentScanIP}";
            }
            else
            {
                networkStatus = "Network: Disconnected";
            }
        }
        
        ui_text.text = $"Status: {status}\nHit Position: {positionText}\n{relativeCoordText}\n{networkStatus}";
    }

    /// <summary>停止眼动追踪</summary>
    void OnDestroy()
    {
        if (trackingSupported)
        {
            EyeTrackingStopInfo stopInfo = new EyeTrackingStopInfo();
            stopInfo.SetVersion(PXR_MotionTracking.PXR_EYE_TRACKING_API_VERSION);
            PXR_MotionTracking.StopEyeTracking(ref stopInfo);
        }
    }
}
