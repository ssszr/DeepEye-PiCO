package com.chuangjie.deepeyeapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Enumeration;

public class EyeTrackingServer extends org.java_websocket.server.WebSocketServer {

    private final MainActivity activity;
    private static final int PORT = 8080;

    public EyeTrackingServer(MainActivity activity) {
        super(new InetSocketAddress(PORT));
        this.activity = activity;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        activity.addLog("VR 设备已连接: " + conn.getRemoteSocketAddress());
        activity.onClientConnected();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        activity.addLog("VR 设备已断开");
        activity.onClientDisconnected();
    }
    
    public boolean hasClients() {
        return getConnections().size() > 0;
    }
    
    public void broadcast(String message) {
        for (WebSocket conn : getConnections()) {
            conn.send(message);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        activity.addLog("收到消息: " + message.substring(0, Math.min(100, message.length())));
        // 转发消息给主Activity，由它分发给各个页面
        activity.onVRMessage(message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        activity.addLog("收到二进制数据: " + message.capacity() + " 字节");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        activity.addLog("错误: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        activity.addLog("WebSocket 服务器启动在端口 " + PORT);
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public String getServerAddress() {
        return getLocalIPAddress() + ":" + PORT;
    }

    @SuppressLint("DefaultLocale")
    private String getLocalIPAddress() {
        String ip192 = null;  // 192.168.x.x 优先级最高
        String ip172 = null;  // 172.x.x.x 优先级中等
        String ip10 = null;   // 10.x.x.x 优先级最低
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                
                // 跳过未启用和回环接口
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                
                String name = ni.getName().toLowerCase();
                activity.addLog("网络接口: " + name);
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // 跳过回环地址
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    
                    String ip = addr.getHostAddress();
                    
                    // 只处理 IPv4 地址（不包含冒号）
                    if (ip != null && !ip.contains(":")) {
                        activity.addLog("  发现IP: " + ip);
                        
                        // 按优先级保存
                        if (ip.startsWith("192.168.")) {
                            ip192 = ip;
                            activity.addLog("  -> 192.168网段(热点)");
                        } else if (ip.startsWith("172.")) {
                            if (ip172 == null) {
                                ip172 = ip;
                                activity.addLog("  -> 172网段");
                            }
                        } else if (ip.startsWith("10.")) {
                            if (ip10 == null) {
                                ip10 = ip;
                                activity.addLog("  -> 10网段");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            activity.addLog("获取IP异常: " + e.getMessage());
        }
        
        // 按优先级返回：192.168 > 172 > 10
        if (ip192 != null) {
            activity.addLog("最终使用: " + ip192 + " (192.168网段)");
            return ip192;
        }
        
        if (ip172 != null) {
            activity.addLog("最终使用: " + ip172 + " (172网段)");
            return ip172;
        }
        
        if (ip10 != null) {
            activity.addLog("最终使用: " + ip10 + " (10网段)");
            return ip10;
        }
        
        // 都没有，返回默认
        activity.addLog("未找到IP，使用默认: 192.168.43.1");
        return "192.168.43.1";
    }
}

