using UnityEngine;
using UnityEngine.XR;
using UnityEngine.UI;

public class CanvasImageSwitcher : MonoBehaviour
{
    // 要切换的图片数组（需拖入Sprite类型的图片）
    public Sprite[] images;
    // 显示图片的Image组件
    private Image imageComponent;
    // 当前图片索引
    private int currentIndex = 0;
    // 右手柄设备
    private InputDevice rightHandDevice;
    // B键按下状态标记（防止长按连续触发）
    private bool isBButtonPressed = false;
    // 切换冷却时间（秒）
    public float switchCooldown = 0.3f;
    // 上一次切换时间
    private float lastSwitchTime = 0f;

    void Start()
    {
        // 获取Image组件
        imageComponent = GetComponent<Image>();
        // 初始化显示第一张图
        if (images.Length > 0 && imageComponent != null)
        {
            imageComponent.sprite = images[0];
        }
        // 获取右手柄设备
        rightHandDevice = InputDevices.GetDeviceAtXRNode(XRNode.RightHand);
    }

    void Update()
    {
        // 检测B键输入
        CheckBButtonInput();
    }

    void CheckBButtonInput()
    {
        if (!rightHandDevice.isValid)
        {
            // 尝试重新获取右手柄
            rightHandDevice = InputDevices.GetDeviceAtXRNode(XRNode.RightHand);
            return;
        }

        bool bButtonCurrentState;
        // 检测B键状态（CommonUsages.secondaryButton对应B/Y键）
        if (rightHandDevice.TryGetFeatureValue(CommonUsages.secondaryButton, out bButtonCurrentState))
        {
            // 仅在B键按下的瞬间触发一次
            if (bButtonCurrentState && !isBButtonPressed)
            {
                isBButtonPressed = true;
                // 检查冷却时间
                if (Time.time - lastSwitchTime > switchCooldown)
                {
                    SwitchImage();
                    lastSwitchTime = Time.time;
                }
            }
            // 松开时重置状态
            else if (!bButtonCurrentState && isBButtonPressed)
            {
                isBButtonPressed = false;
            }
        }
    }

    void SwitchImage()
    {
        if (images.Length == 0 || imageComponent == null) return;
        // 循环切换图片
        currentIndex = (currentIndex + 1) % images.Length;
        imageComponent.sprite = images[currentIndex];
    }
}