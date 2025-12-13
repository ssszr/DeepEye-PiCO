using System;
using TMPro;
using Unity.XR.PXR;
using UnityEngine;

namespace XXX
{
    public class EyeTracker : MonoBehaviour
    {
        public TMP_Text ui_text;

        private bool trackingSupported = false;
        private bool tracking = false;
        private EyeTrackingData eyeTrackingData;
        private float updateInterval = 0.1f; // Control UI update frequency
        private float lastUpdateTime = 0;

        void Start()
        {
            // Initialize eye tracking data with correct API version
            eyeTrackingData = new EyeTrackingData();
            eyeTrackingData.SetVersion(PXR_MotionTracking.PXR_EYE_TRACKING_API_VERSION);

            // Check device support for eye tracking
            EyeTrackingMode[] supportedModes = new EyeTrackingMode[1];
            int supportedModesCount = 0;
            int supportState = PXR_MotionTracking.GetEyeTrackingSupported(
                ref trackingSupported,
                ref supportedModesCount,
                ref supportedModes
            );

            if (supportState == 0 && trackingSupported)
            {
                ui_text.text = "Eye Tracking Supported. Starting...";

                // Start eye tracking (dual-eye mode, no extra calibration)
                EyeTrackingStartInfo startInfo = new EyeTrackingStartInfo();
                startInfo.needCalibration = 1;
                startInfo.mode = EyeTrackingMode.PXR_ETM_BOTH;
                int startState = PXR_MotionTracking.StartEyeTracking(ref startInfo);

                ui_text.text = startState == 0 ? "Eye Tracking Started" : "Start Failed. Code: " + startState;
            }
            else
            {
                ui_text.text = "Eye Tracking Not Supported";
            }
        }

        void Update()
        {
            // Limit update frequency to avoid UI flicker
            if (!trackingSupported || Time.time - lastUpdateTime < updateInterval)
                return;

            lastUpdateTime = Time.time;

            // Get eye tracking state
            EyeTrackingState eyeState = new EyeTrackingState();
            eyeState.SetVersion(PXR_MotionTracking.PXR_EYE_TRACKING_API_VERSION);
            int stateCode = PXR_MotionTracking.GetEyeTrackingState(ref tracking, ref eyeState);

            if (tracking && stateCode == 0)
            {
                // Request eye tracking data (position + orientation)
                EyeTrackingDataGetInfo getInfo = new EyeTrackingDataGetInfo();
                getInfo.SetVersion(PXR_MotionTracking.PXR_EYE_TRACKING_API_VERSION);
                getInfo.displayTime = 0;
                getInfo.flags = EyeTrackingDataGetFlags.PXR_EYE_POSITION | EyeTrackingDataGetFlags.PXR_EYE_ORIENTATION;

                int dataState = PXR_MotionTracking.GetEyeTrackingData(ref getInfo, ref eyeTrackingData);
                if (dataState == 0)
                {
                    PrintEyeData();
                }
                else
                {
                    ui_text.text = "Get Data Failed. Code: " + dataState;
                }
            }
            else
            {
                ui_text.text = "Not Tracking. State Code: " + stateCode;
            }
        }

        // Print combined eye data (valid for non-Enterprise devices)
        void PrintEyeData()
        {
            var combinedEye = eyeTrackingData.eyeDatas[(int)PerEyeUsage.Combined];

            if (combinedEye.isPoseValid == 1)
            {
                Vector3 position = combinedEye.pose.position.ToVector3();
                Quaternion rotation = combinedEye.pose.orientation.ToQuat();
                Vector3 eulerRot = rotation.eulerAngles;

                // Format data to 3 decimal places for readability
                string dataText =
                    $"Pose Valid: Yes\n" +
                    $"Position (X,Y,Z):\n" +
                    $"  {position.x:F3}, {position.y:F3}, {position.z:F3}\n" +
                    $"Rotation (Euler X,Y,Z):\n" +
                    $"  {eulerRot.x:F2}°, {eulerRot.y:F2}°, {eulerRot.z:F2}°\n" +
                    $"Eye Openness: {combinedEye.openness:F2} (Valid: {combinedEye.isOpennessValid == 1})";

                ui_text.text = dataText;
            }
            else
            {
                ui_text.text = "Combined Eye Data Invalid\nWait for calibration...";
            }
        }

        // Stop eye tracking when script is destroyed
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
}