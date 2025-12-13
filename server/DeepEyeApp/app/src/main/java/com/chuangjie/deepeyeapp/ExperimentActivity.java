package com.chuangjie.deepeyeapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.chuangjie.deepeyeapp.models.DataSample;
import com.chuangjie.deepeyeapp.models.TestSet;
import com.chuangjie.deepeyeapp.storage.DataManager;
import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 实验控制页面
 */
public class ExperimentActivity extends AppCompatActivity implements MainActivity.VRMessageListener {

    private TextView tvTestSetName, tvImageCount, tvVRStatus, tvImageStatus, tvProgress;
    private Button btnSendImages, btnStartExperiment, btnStopExperiment;
    private CardView cardProgress;
    private ImageView ivCurrentImage;
    
    private DataManager dataManager;
    private TestSet testSet;
    private String testSetId;
    
    private EyeTrackingServer server;
    private boolean isVRConnected = false;
    private boolean imagesSent = false;
    private int currentProgress = 0;
    private int totalImages = 0;
    
    private DataSample currentSample; // 当前采集的数据样本
    private String receivedDataJson; // 接收到的眼动数据JSON
    private List<Bitmap> imageBitmaps = new ArrayList<>(); // 缓存图片Bitmap

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiment);

        dataManager = DataManager.getInstance(this);
        testSetId = getIntent().getStringExtra("test_set_id");

        if (testSetId == null) {
            Toast.makeText(this, "测试集ID错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        testSet = dataManager.getTestSetById(testSetId);
        if (testSet == null) {
            Toast.makeText(this, "测试集不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        getServerInstance();
        checkVRStatus();
        
        // 注册消息监听
        if (MainActivity.getInstance() != null) {
            MainActivity.getInstance().setVRMessageListener(this);
        }
    }

    private void initViews() {
        Button btnBack = findViewById(R.id.btnBack);
        tvTestSetName = findViewById(R.id.tvTestSetName);
        tvImageCount = findViewById(R.id.tvImageCount);
        tvVRStatus = findViewById(R.id.tvVRStatus);
        tvImageStatus = findViewById(R.id.tvImageStatus);
        tvProgress = findViewById(R.id.tvProgress);
        cardProgress = findViewById(R.id.cardProgress);
        ivCurrentImage = findViewById(R.id.ivCurrentImage);
        
        btnSendImages = findViewById(R.id.btnSendImages);
        btnStartExperiment = findViewById(R.id.btnStartExperiment);
        btnStopExperiment = findViewById(R.id.btnStopExperiment);

        tvTestSetName.setText(testSet.getName());
        tvImageCount.setText(String.valueOf(testSet.getImageCount()));
        totalImages = testSet.getImageCount();

        btnBack.setOnClickListener(v -> finish());
        btnSendImages.setOnClickListener(v -> sendImagesToVR());
        btnStartExperiment.setOnClickListener(v -> startExperiment());
        btnStopExperiment.setOnClickListener(v -> stopExperiment());
        
        // 预加载图片
        loadImageBitmaps();
    }
    
    private void loadImageBitmaps() {
        imageBitmaps.clear();
        for (String base64 : testSet.getImageBase64List()) {
            try {
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                imageBitmaps.add(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void getServerInstance() {
        // 从MainActivity获取服务器实例
        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null) {
            server = mainActivity.getServer();
        }
    }

    private void checkVRStatus() {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable checkTask = new Runnable() {
            @Override
            public void run() {
                if (server != null && server.hasClients()) {
                    if (!isVRConnected) {
                        isVRConnected = true;
                        updateVRStatus(true);
                    }
                } else {
                    if (isVRConnected) {
                        isVRConnected = false;
                        updateVRStatus(false);
                    }
                }
                handler.postDelayed(this, 1000); // 每秒检查一次
            }
        };
        handler.post(checkTask);
    }

    private void updateVRStatus(boolean connected) {
        runOnUiThread(() -> {
            if (connected) {
                tvVRStatus.setText("已连接");
                tvVRStatus.setTextColor(getColor(R.color.status_running));
                btnSendImages.setEnabled(true);
            } else {
                tvVRStatus.setText("未连接");
                tvVRStatus.setTextColor(getColor(R.color.status_stopped));
                btnSendImages.setEnabled(false);
                btnStartExperiment.setEnabled(false);
                btnStopExperiment.setEnabled(false);
                imagesSent = false;
                updateImageStatus("未传输");
            }
        });
    }

    private void updateImageStatus(String status) {
        runOnUiThread(() -> {
            tvImageStatus.setText(status);
            if (status.contains("完成")) {
                tvImageStatus.setTextColor(getColor(R.color.status_running));
            } else {
                tvImageStatus.setTextColor(getColor(R.color.status_stopped));
            }
        });
    }

    private void sendImagesToVR() {
        if (testSet.getImageCount() == 0) {
            Toast.makeText(this, "测试集中没有图片", Toast.LENGTH_SHORT).show();
            return;
        }

        if (server == null || !server.hasClients()) {
            Toast.makeText(this, "VR设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        updateImageStatus("正在传输...");
        btnSendImages.setEnabled(false);

        // 在后台线程发送图片
        new Thread(() -> {
            try {
                for (int i = 0; i < testSet.getImageBase64List().size(); i++) {
                    String base64 = testSet.getImageBase64List().get(i);
                    
                    // 第一张图片加上清空标记
                    if (i == 0) {
                        base64 = "CLEAR_START:" + base64;
                    }
                    
                    JSONObject message = new JSONObject();
                    message.put("type", "image");
                    message.put("data", base64);
                    
                    server.broadcast(message.toString());
                    
                    int finalI = i;
                    runOnUiThread(() -> {
                        updateImageStatus("传输中 " + (finalI + 1) + "/" + testSet.getImageCount());
                    });
                    
                    // 避免发送过快
                    Thread.sleep(500);
                }
                
                runOnUiThread(() -> {
                    imagesSent = true;
                    updateImageStatus("传输完成");
                    btnSendImages.setEnabled(true);
                    btnStartExperiment.setEnabled(true);
                    Toast.makeText(this, "图片已全部发送到VR", Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updateImageStatus("传输失败");
                    btnSendImages.setEnabled(true);
                    Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void startExperiment() {
        if (!imagesSent) {
            Toast.makeText(this, "请先发送图片到VR", Toast.LENGTH_SHORT).show();
            return;
        }

        if (server == null || !server.hasClients()) {
            Toast.makeText(this, "VR设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.put("type", "start");
            server.broadcast(message.toString());
            
            btnStartExperiment.setEnabled(false);
            btnStopExperiment.setEnabled(true);
            btnSendImages.setEnabled(false);
            cardProgress.setVisibility(android.view.View.VISIBLE);
            
            Toast.makeText(this, "实验已开始", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopExperiment() {
        if (server == null || !server.hasClients()) {
            Toast.makeText(this, "VR设备未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.put("type", "stop");
            server.broadcast(message.toString());
            
            btnStartExperiment.setEnabled(true);
            btnStopExperiment.setEnabled(false);
            btnSendImages.setEnabled(true);
            cardProgress.setVisibility(android.view.View.GONE);
            
            Toast.makeText(this, "实验已停止", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "停止失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void updateProgress(int current, int total) {
        runOnUiThread(() -> {
            currentProgress = current;
            tvProgress.setText(current + "/" + total);
            
            // 显示当前图片
            if (current > 0 && current <= imageBitmaps.size()) {
                ivCurrentImage.setImageBitmap(imageBitmaps.get(current - 1));
            }
        });
    }

    // VR消息处理
    @Override
    public void onVRMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type");
            
            switch (type) {
                case "progress":
                    // 进度更新
                    int current = json.optInt("current", 0);
                    int total = json.optInt("total", 0);
                    updateProgress(current, total);
                    break;
                    
                case "finished":
                    // 实验完成
                    runOnUiThread(() -> onExperimentFinished());
                    break;
                    
                case "data":
                    // 接收眼动数据
                    receivedDataJson = json.optString("payload");
                    runOnUiThread(() -> onDataReceived());
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void onExperimentFinished() {
        Toast.makeText(this, "实验已完成，正在接收数据...", Toast.LENGTH_SHORT).show();
        btnStartExperiment.setEnabled(true);
        btnStopExperiment.setEnabled(false);
        btnSendImages.setEnabled(true);
    }
    
    private void onDataReceived() {
        if (receivedDataJson == null || receivedDataJson.isEmpty()) {
            Toast.makeText(this, "未收到数据", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 生成样本名称
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String sampleName = testSet.getName() + "_" + sdf.format(new Date());
        
        cardProgress.setVisibility(android.view.View.GONE);
        
        // 弹出对话框
        new AlertDialog.Builder(this)
            .setTitle("数据采集完成")
            .setMessage("样本名称：" + sampleName + "\n\n请选择操作：")
            .setPositiveButton("打开查看", (dialog, which) -> {
                // 跳转到数据查看页面
                Intent intent = new Intent(this, DataViewerActivity.class);
                intent.putExtra("sample_name", sampleName);
                intent.putExtra("test_set_id", testSetId);
                intent.putExtra("data_json", receivedDataJson);
                startActivity(intent);
            })
            .setNegativeButton("保存TXT", (dialog, which) -> {
                saveDataAsTxt(sampleName);
            })
            .setNeutralButton("保存JSON", (dialog, which) -> {
                saveDataAsJson(sampleName);
            })
            .show();
    }
    
    private void saveDataAsTxt(String sampleName) {
        try {
            // 解析数据并生成可读的TXT格式
            JSONObject payload = new JSONObject(receivedDataJson);
            int totalPoints = payload.optInt("totalPoints", 0);
            JSONArray dataArray = payload.optJSONArray("data");
            
            StringBuilder txtContent = new StringBuilder();
            txtContent.append("=================================\n");
            txtContent.append("眼动数据采集报告\n");
            txtContent.append("=================================\n\n");
            txtContent.append("样本名称: ").append(sampleName).append("\n");
            txtContent.append("测试集: ").append(testSet.getName()).append("\n");
            txtContent.append("图片数量: ").append(testSet.getImageCount()).append("\n");
            txtContent.append("数据点总数: ").append(totalPoints).append("\n");
            txtContent.append("采集时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date())).append("\n\n");
            
            if (dataArray != null) {
                // 按图片分组统计
                Map<Integer, Integer> pointsByImage = new HashMap<>();
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject point = dataArray.getJSONObject(i);
                    int imageIndex = point.optInt("imageIndex", 0);
                    pointsByImage.put(imageIndex, pointsByImage.getOrDefault(imageIndex, 0) + 1);
                }
                
                txtContent.append("各图片数据点统计:\n");
                for (int i = 0; i < testSet.getImageCount(); i++) {
                    int count = pointsByImage.getOrDefault(i, 0);
                    txtContent.append("  图片").append(i + 1).append(": ").append(count).append("点\n");
                }
                
                txtContent.append("\n=================================\n");
                txtContent.append("详细数据点 (格式: 图片索引, X坐标, Y坐标, 时间戳)\n");
                txtContent.append("=================================\n\n");
                
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject point = dataArray.getJSONObject(i);
                    int imageIndex = point.optInt("imageIndex", 0);
                    long timestamp = point.optLong("timestamp", 0);
                    JSONObject screenPos = point.optJSONObject("screenPos");
                    
                    if (screenPos != null) {
                        double x = screenPos.optDouble("x", 0);
                        double y = screenPos.optDouble("y", 0);
                        txtContent.append(String.format("%d, %.4f, %.4f, %d\n", 
                            imageIndex + 1, x, y, timestamp));
                    }
                }
            }
            
            // 保存到Documents目录
            String fileName = sampleName + ".txt";
            java.io.File documentsDir = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(), "Documents");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }
            
            java.io.File file = new java.io.File(documentsDir, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(txtContent.toString());
            writer.close();
            
            String filePath = file.getAbsolutePath();
            new AlertDialog.Builder(this)
                .setTitle("保存成功")
                .setMessage("TXT文件已保存到：\n" + filePath + "\n\n文件大小：" + (file.length() / 1024) + " KB")
                .setPositiveButton("分享文件", (dialog, which) -> {
                    shareFile(file);
                })
                .setNeutralButton("复制路径", (dialog, which) -> {
                    copyToClipboard(filePath);
                })
                .setNegativeButton("确定", null)
                .show();
            
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void saveDataAsJson(String sampleName) {
        try {
            // 构建完整的数据JSON
            JSONObject fullData = new JSONObject();
            fullData.put("sample_name", sampleName);
            fullData.put("test_set_name", testSet.getName());
            fullData.put("test_set_id", testSetId);
            fullData.put("image_count", testSet.getImageCount());
            fullData.put("timestamp", System.currentTimeMillis());
            fullData.put("eye_tracking_data", new JSONObject(receivedDataJson));
            
            // 保存到Documents目录
            String fileName = sampleName + ".json";
            java.io.File documentsDir = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(), "Documents");
            if (!documentsDir.exists()) {
                documentsDir.mkdirs();
            }
            
            java.io.File file = new java.io.File(documentsDir, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(fullData.toString(2));
            writer.close();
            
            String filePath = file.getAbsolutePath();
            new AlertDialog.Builder(this)
                .setTitle("保存成功")
                .setMessage("JSON文件已保存到：\n" + filePath + "\n\n文件大小：" + (file.length() / 1024) + " KB")
                .setPositiveButton("分享文件", (dialog, which) -> {
                    shareFile(file);
                })
                .setNeutralButton("复制路径", (dialog, which) -> {
                    copyToClipboard(filePath);
                })
                .setNegativeButton("确定", null)
                .show();
            
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void shareFile(java.io.File file) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            android.net.Uri uri;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    file
                );
            } else {
                uri = android.net.Uri.fromFile(file);
            }
            
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "分享文件"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) 
            getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("file_path", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "路径已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
    
    private void openFileLocation(java.io.File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            android.net.Uri uri;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // Android 7.0+ 使用 FileProvider
                uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    file.getParentFile() // 打开父目录
                );
            } else {
                uri = android.net.Uri.fromFile(file.getParentFile());
            }
            
            intent.setDataAndType(uri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // 如果没有文件管理器，尝试用Intent.ACTION_GET_CONTENT
                Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fallbackIntent.setType("*/*");
                fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivity(Intent.createChooser(fallbackIntent, "选择文件管理器"));
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件夹，请手动前往下载目录查看", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消监听
        if (MainActivity.getInstance() != null) {
            MainActivity.getInstance().setVRMessageListener(null);
        }
    }
}
