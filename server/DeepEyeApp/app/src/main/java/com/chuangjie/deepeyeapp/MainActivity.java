package com.chuangjie.deepeyeapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 主页 - 服务器状态和功能导航
 */
public class MainActivity extends AppCompatActivity {

    private static MainActivity instance;
    
    private TextView tvStatus, tvIP, tvClientStatus, tvLog;
    private Button btnStart, btnStop;
    private Button btnTestSets;
    
    private EyeTrackingServer server;
    private boolean isRunning = false;
    
    // VR消息监听器
    public interface VRMessageListener {
        void onVRMessage(String message);
    }
    private VRMessageListener messageListener;
    
    public static MainActivity getInstance() {
        return instance;
    }
    
    public EyeTrackingServer getServer() {
        return server;
    }
    
    public void setVRMessageListener(VRMessageListener listener) {
        this.messageListener = listener;
    }
    
    public void onVRMessage(String message) {
        if (messageListener != null) {
            messageListener.onVRMessage(message);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instance = this;
        
        initViews();
        initServer();
        setupListeners();
        
        // 自动启动服务器
        startServer();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvIP = findViewById(R.id.tvIP);
        tvClientStatus = findViewById(R.id.tvClientStatus);
        tvLog = findViewById(R.id.tvLog);
        
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnTestSets = findViewById(R.id.btnTestSets);
    }

    private void initServer() {
        server = new EyeTrackingServer(this);
    }

    private void setupListeners() {
        btnStart.setOnClickListener(v -> startServer());
        btnStop.setOnClickListener(v -> stopServer());
        
        btnTestSets.setOnClickListener(v -> {
            startActivity(new Intent(this, TestSetListActivity.class));
        });
    }

    private void startServer() {
        if (!isRunning) {
            try {
                // WebSocket服务器只能启动一次，停止后需要重新创建
                if (server != null) {
                    try {
                        server.stop();
                    } catch (Exception e) {
                        // 忽略停止时的异常
                    }
                }
                
                // 重新创建服务器实例
                server = new EyeTrackingServer(this);
                server.start();
                isRunning = true;
                updateServerStatus(true);
                addLog("服务器已启动");
            } catch (Exception e) {
                addLog("启动失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void stopServer() {
        if (isRunning) {
            try {
                server.stop();
                isRunning = false;
                updateServerStatus(false);
                addLog("服务器已停止");
            } catch (Exception e) {
                addLog("停止失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateServerStatus(boolean running) {
        runOnUiThread(() -> {
            if (running) {
                tvStatus.setText("运行中");
                tvStatus.setTextColor(getColor(R.color.status_running));
                tvIP.setText(server.getServerAddress());
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
            } else {
                tvStatus.setText("已停止");
                tvStatus.setTextColor(getColor(R.color.status_stopped));
                tvIP.setText("-");
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });
    }

    public void addLog(String message) {
        runOnUiThread(() -> {
            String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            String log = tvLog.getText().toString();
            tvLog.setText(String.format("[%s] %s\n%s", time, message, log));
        });
    }
    
    public void onClientConnected() {
        runOnUiThread(() -> {
            tvClientStatus.setText("已连接");
            tvClientStatus.setTextColor(getColor(R.color.status_running));
        });
    }
    
    public void onClientDisconnected() {
        runOnUiThread(() -> {
            tvClientStatus.setText("未连接");
            tvClientStatus.setTextColor(getColor(R.color.status_stopped));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRunning) {
            try {
                server.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
