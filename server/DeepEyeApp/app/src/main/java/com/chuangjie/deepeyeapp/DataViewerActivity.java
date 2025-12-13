package com.chuangjie.deepeyeapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.chuangjie.deepeyeapp.models.TestSet;
import com.chuangjie.deepeyeapp.storage.DataManager;
import com.chuangjie.deepeyeapp.views.EyeTrackingView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据查看页面 - 显示图片和眼动轨迹
 */
public class DataViewerActivity extends AppCompatActivity {

    private TextView tvSampleInfo, tvImageIndex, tvPlaybackProgress;
    private EyeTrackingView eyeTrackingView;
    private Button btnPrevImage, btnNextImage, btnSave, btnPlayPause, btnShowDebug;
    private SeekBar seekBarPlayback;
    
    private String sampleName;
    private String testSetId;
    private String dataJson;
    private TestSet testSet;
    
    private List<Bitmap> images = new ArrayList<>();
    private Map<Integer, List<EyeTrackingView.EyePoint>> eyeDataByImage = new HashMap<>();
    private int currentImageIndex = 0;
    
    // 回放控制
    private boolean isPlaying = false;
    private Handler playbackHandler = new Handler(Looper.getMainLooper());
    private Runnable playbackRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_viewer);

        sampleName = getIntent().getStringExtra("sample_name");
        testSetId = getIntent().getStringExtra("test_set_id");
        dataJson = getIntent().getStringExtra("data_json");

        DataManager dataManager = DataManager.getInstance(this);
        testSet = dataManager.getTestSetById(testSetId);

        if (testSet == null) {
            Toast.makeText(this, "测试集不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadImages();
        parseEyeData();
        showCurrentImage();
    }

    private void initViews() {
        Button btnBack = findViewById(R.id.btnBack);
        btnSave = findViewById(R.id.btnSave);
        tvSampleInfo = findViewById(R.id.tvSampleInfo);
        tvImageIndex = findViewById(R.id.tvImageIndex);
        tvPlaybackProgress = findViewById(R.id.tvPlaybackProgress);
        eyeTrackingView = findViewById(R.id.eyeTrackingView);
        btnPrevImage = findViewById(R.id.btnPrevImage);
        btnNextImage = findViewById(R.id.btnNextImage);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        seekBarPlayback = findViewById(R.id.seekBarPlayback);
        btnShowDebug = findViewById(R.id.btnShowDebug);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveSample());
        btnPrevImage.setOnClickListener(v -> previousImage());
        btnNextImage.setOnClickListener(v -> nextImage());
        btnPlayPause.setOnClickListener(v -> togglePlayback());
        btnShowDebug.setOnClickListener(v -> showDebugInfo());
        
        // SeekBar监听
        seekBarPlayback.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    eyeTrackingView.setPlaybackProgress(progress);
                    updatePlaybackProgress();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                pausePlayback();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // 显示样本信息
        tvSampleInfo.setText("测试集：" + testSet.getName() + 
                           "\n样本ID：" + sampleName +
                           "\n图片数：" + testSet.getImageCount());
    }

    private void loadImages() {
        images.clear();
        for (String base64 : testSet.getImageBase64List()) {
            try {
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                images.add(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void parseEyeData() {
        if (dataJson == null || dataJson.isEmpty()) {
            Toast.makeText(this, "数据为空", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.util.Log.d("DataViewer", "原始数据JSON: " + dataJson.substring(0, Math.min(500, dataJson.length())));
            
            JSONObject payload = new JSONObject(dataJson);
            
            // 获取总点数（可选）
            int totalPoints = payload.optInt("totalPoints", 0);
            android.util.Log.d("DataViewer", "声明的总点数: " + totalPoints);
            
            JSONArray dataArray = payload.optJSONArray("data");
            
            if (dataArray == null) {
                Toast.makeText(this, "数据格式错误：没有data数组", Toast.LENGTH_SHORT).show();
                android.util.Log.e("DataViewer", "payload keys: " + payload.toString());
                return;
            }

            android.util.Log.d("DataViewer", "实际数据点数: " + dataArray.length());
            
            // 按图片索引分组眼动数据
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject point = dataArray.getJSONObject(i);
                int imageIndex = point.optInt("imageIndex", 0);
                long timestamp = point.optLong("timestamp", 0);
                boolean isValid = point.optBoolean("isValid", true);
                
                // Unity的Vector2序列化格式：{"x": 0.5, "y": 0.3}
                JSONObject screenPos = point.optJSONObject("screenPos");
                if (screenPos != null) {
                    float x = (float) screenPos.optDouble("x", -1);
                    float y = (float) screenPos.optDouble("y", -1);
                    
                    // 调试日志 - 打印前5个点
                    if (i < 5) {
                        android.util.Log.d("DataViewer", String.format(
                            "点%d: imageIndex=%d, x=%.3f, y=%.3f, timestamp=%d, isValid=%b", 
                            i, imageIndex, x, y, timestamp, isValid));
                    }
                    
                    // 验证坐标有效性（Unity的屏幕坐标通常是0-1范围）
                    if (x >= 0 && x <= 1 && y >= 0 && y <= 1 && isValid) {
                        // 添加到对应图片的数据列表
                        if (!eyeDataByImage.containsKey(imageIndex)) {
                            eyeDataByImage.put(imageIndex, new ArrayList<>());
                        }
                        eyeDataByImage.get(imageIndex).add(
                            new EyeTrackingView.EyePoint(x, y, timestamp));
                    } else {
                        if (i < 10) { // 只警告前10个无效点
                            android.util.Log.w("DataViewer", 
                                String.format("无效/超范围坐标 (索引%d): x=%.3f, y=%.3f, isValid=%b", 
                                i, x, y, isValid));
                        }
                    }
                } else {
                    android.util.Log.w("DataViewer", "点" + i + " 缺少screenPos字段");
                }
            }
            
            // 统计信息
            StringBuilder stats = new StringBuilder();
            stats.append("总数据点: ").append(dataArray.length()).append("\n");
            int validPoints = 0;
            for (int i = 0; i < images.size(); i++) {
                int count = eyeDataByImage.containsKey(i) ? eyeDataByImage.get(i).size() : 0;
                validPoints += count;
                stats.append("图片").append(i + 1).append(": ").append(count).append("点\n");
            }
            stats.append("有效点总数: ").append(validPoints);
            
            android.util.Log.d("DataViewer", stats.toString());
            
            Toast.makeText(this, "已加载 " + validPoints + "/" + dataArray.length() + " 个有效数据点", 
                Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "数据解析失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("DataViewer", "解析错误", e);
            e.printStackTrace();
        }
    }

    private void showCurrentImage() {
        if (images.isEmpty()) {
            return;
        }

        // 停止当前回放
        pausePlayback();
        seekBarPlayback.setProgress(0);

        // 更新索引显示
        tvImageIndex.setText((currentImageIndex + 1) + "/" + images.size());
        
        // 显示图片
        eyeTrackingView.setImage(images.get(currentImageIndex));
        
        // 显示对应的眼动数据
        List<EyeTrackingView.EyePoint> points = eyeDataByImage.get(currentImageIndex);
        if (points != null && !points.isEmpty()) {
            eyeTrackingView.setEyePoints(points);
            updatePlaybackProgress();
        } else {
            eyeTrackingView.setEyePoints(new ArrayList<>());
            tvPlaybackProgress.setText("0/0");
        }
        
        // 更新按钮状态
        btnPrevImage.setEnabled(currentImageIndex > 0);
        btnNextImage.setEnabled(currentImageIndex < images.size() - 1);
    }

    private void previousImage() {
        if (currentImageIndex > 0) {
            currentImageIndex--;
            showCurrentImage();
        }
    }

    private void nextImage() {
        if (currentImageIndex < images.size() - 1) {
            currentImageIndex++;
            showCurrentImage();
        }
    }

    private void saveSample() {
        // TODO: 实现保存到DataManager
        Toast.makeText(this, "样本已保存：" + sampleName, Toast.LENGTH_LONG).show();
        btnSave.setEnabled(false);
        btnSave.setText("已保存");
    }
    
    // ========== 回放控制 ==========
    
    private void togglePlayback() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }
    
    private void startPlayback() {
        isPlaying = true;
        btnPlayPause.setText("暂停");
        
        // 开始回放模式
        eyeTrackingView.startPlayback();
        
        // 创建回放任务
        playbackRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPlaying) return;
                
                int currentProgress = seekBarPlayback.getProgress();
                if (currentProgress < 100) {
                    currentProgress += 1;
                    seekBarPlayback.setProgress(currentProgress);
                    eyeTrackingView.setPlaybackProgress(currentProgress);
                    updatePlaybackProgress();
                    playbackHandler.postDelayed(this, 50); // 50ms更新一次，约2秒播完
                } else {
                    // 播放完成
                    pausePlayback();
                    seekBarPlayback.setProgress(0);
                }
            }
        };
        
        playbackHandler.post(playbackRunnable);
    }
    
    private void pausePlayback() {
        isPlaying = false;
        btnPlayPause.setText("播放");
        if (playbackRunnable != null) {
            playbackHandler.removeCallbacks(playbackRunnable);
        }
    }
    
    private void updatePlaybackProgress() {
        int current = eyeTrackingView.getCurrentPlaybackIndex();
        int total = eyeTrackingView.getTotalPoints();
        tvPlaybackProgress.setText(current + "/" + total);
    }
    
    // ========== 调试功能 ==========
    
    private void showDebugInfo() {
        StringBuilder info = new StringBuilder();
        
        // 基本信息
        info.append("=== 数据概览 ===\n");
        info.append("测试集: ").append(testSet.getName()).append("\n");
        info.append("图片总数: ").append(images.size()).append("\n");
        info.append("当前图片: ").append(currentImageIndex + 1).append("\n\n");
        
        // 每张图片的数据统计
        info.append("=== 各图片数据点 ===\n");
        for (int i = 0; i < images.size(); i++) {
            int count = eyeDataByImage.containsKey(i) ? eyeDataByImage.get(i).size() : 0;
            info.append("图片 ").append(i + 1).append(": ").append(count).append(" 点\n");
        }
        
        // 当前图片的详细数据（显示前10个点）
        if (eyeDataByImage.containsKey(currentImageIndex)) {
            List<EyeTrackingView.EyePoint> points = eyeDataByImage.get(currentImageIndex);
            info.append("\n=== 当前图片数据点 (前10个) ===\n");
            int showCount = Math.min(10, points.size());
            for (int i = 0; i < showCount; i++) {
                EyeTrackingView.EyePoint p = points.get(i);
                info.append(String.format("点%d: x=%.3f, y=%.3f", i + 1, p.x, p.y));
                if (p.timestamp > 0) {
                    info.append(String.format(", t=%d", p.timestamp));
                }
                info.append("\n");
            }
            if (points.size() > 10) {
                info.append("... 还有 ").append(points.size() - 10).append(" 个点\n");
            }
        } else {
            info.append("\n当前图片无数据\n");
        }
        
        // 原始JSON预览
        if (dataJson != null && !dataJson.isEmpty()) {
            info.append("\n=== 原始JSON (前200字符) ===\n");
            info.append(dataJson.substring(0, Math.min(200, dataJson.length())));
            if (dataJson.length() > 200) {
                info.append("...");
            }
        }
        
        // 显示对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("数据详情");
        builder.setMessage(info.toString());
        builder.setPositiveButton("确定", null);
        builder.setNeutralButton("复制", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
                getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("debug_info", info.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // 设置消息可滚动
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextIsSelectable(true);
            messageView.setMaxLines(20);
            messageView.setVerticalScrollBarEnabled(true);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        pausePlayback();
    }
}

