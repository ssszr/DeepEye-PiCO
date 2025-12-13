package com.chuangjie.deepeyeapp.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * 眼动轨迹显示View - 支持实时显示和回放
 */
public class EyeTrackingView extends View {

    private Bitmap imageBitmap;
    private List<EyePoint> allEyePoints = new ArrayList<>();
    private List<EyePoint> displayPoints = new ArrayList<>();
    private Paint pointPaint;
    private Paint linePaint;
    private Paint imagePaint;
    
    // 回放控制
    private boolean isPlaybackMode = false;
    private int currentPlaybackIndex = 0;

    public static class EyePoint {
        public float x; // 归一化坐标 0-1
        public float y; // 归一化坐标 0-1
        public long timestamp; // 时间戳（可选）
        
        public EyePoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
        
        public EyePoint(float x, float y, long timestamp) {
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }
    }

    public EyeTrackingView(Context context) {
        super(context);
        init();
    }

    public EyeTrackingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        pointPaint = new Paint();
        pointPaint.setColor(Color.RED);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);
        
        linePaint = new Paint();
        linePaint.setColor(Color.argb(100, 255, 0, 0)); // 半透明红色
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2);
        linePaint.setAntiAlias(true);
        
        imagePaint = new Paint();
        imagePaint.setFilterBitmap(true);
    }

    public void setImage(Bitmap bitmap) {
        this.imageBitmap = bitmap;
        invalidate();
    }

    public void setEyePoints(List<EyePoint> points) {
        this.allEyePoints = new ArrayList<>(points);
        this.displayPoints = new ArrayList<>(points);
        this.isPlaybackMode = false;
        invalidate();
    }

    public void addEyePoint(float x, float y) {
        EyePoint point = new EyePoint(x, y);
        allEyePoints.add(point);
        displayPoints.add(point);
        invalidate();
    }

    public void clear() {
        allEyePoints.clear();
        displayPoints.clear();
        currentPlaybackIndex = 0;
        isPlaybackMode = false;
        invalidate();
    }
    
    // ========== 回放控制 ==========
    
    /**
     * 开始回放模式
     */
    public void startPlayback() {
        if (allEyePoints.isEmpty()) return;
        isPlaybackMode = true;
        currentPlaybackIndex = 0;
        displayPoints.clear();
        invalidate();
    }
    
    /**
     * 更新回放进度（0-100）
     */
    public void setPlaybackProgress(int progress) {
        if (!isPlaybackMode || allEyePoints.isEmpty()) return;
        
        int targetIndex = (int) (allEyePoints.size() * progress / 100.0f);
        targetIndex = Math.min(targetIndex, allEyePoints.size());
        
        displayPoints.clear();
        for (int i = 0; i < targetIndex; i++) {
            displayPoints.add(allEyePoints.get(i));
        }
        
        currentPlaybackIndex = targetIndex;
        invalidate();
    }
    
    /**
     * 停止回放，显示所有点
     */
    public void stopPlayback() {
        isPlaybackMode = false;
        displayPoints = new ArrayList<>(allEyePoints);
        invalidate();
    }
    
    public boolean isPlaybackMode() {
        return isPlaybackMode;
    }
    
    public int getTotalPoints() {
        return allEyePoints.size();
    }
    
    public int getCurrentPlaybackIndex() {
        return currentPlaybackIndex;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (imageBitmap != null) {
            // 绘制图片（适应View大小）
            float viewWidth = getWidth();
            float viewHeight = getHeight();
            float imageWidth = imageBitmap.getWidth();
            float imageHeight = imageBitmap.getHeight();

            // 计算缩放比例（保持宽高比）
            float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
            float scaledWidth = imageWidth * scale;
            float scaledHeight = imageHeight * scale;

            // 居中显示
            float left = (viewWidth - scaledWidth) / 2;
            float top = (viewHeight - scaledHeight) / 2;

            canvas.drawBitmap(imageBitmap, 
                null, 
                new android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight),
                imagePaint);

            // 绘制眼动轨迹线（如果在回放模式）
            if (isPlaybackMode && displayPoints.size() > 1) {
                for (int i = 0; i < displayPoints.size() - 1; i++) {
                    EyePoint p1 = displayPoints.get(i);
                    EyePoint p2 = displayPoints.get(i + 1);
                    
                    float x1 = left + p1.x * scaledWidth;
                    float y1 = top + p1.y * scaledHeight;
                    float x2 = left + p2.x * scaledWidth;
                    float y2 = top + p2.y * scaledHeight;
                    
                    canvas.drawLine(x1, y1, x2, y2, linePaint);
                }
            }
            
            // 绘制眼动点（红点）
            for (EyePoint point : displayPoints) {
                // 归一化坐标转换为实际坐标
                float x = left + point.x * scaledWidth;
                float y = top + point.y * scaledHeight;
                
                // 绘制红点（半径5像素）
                canvas.drawCircle(x, y, 5, pointPaint);
            }
            
            // 如果在回放模式，当前点用更大的圆圈标记
            if (isPlaybackMode && currentPlaybackIndex > 0 && currentPlaybackIndex <= displayPoints.size()) {
                EyePoint currentPoint = displayPoints.get(currentPlaybackIndex - 1);
                float x = left + currentPoint.x * scaledWidth;
                float y = top + currentPoint.y * scaledHeight;
                
                // 绘制外圈（黄色）
                Paint highlightPaint = new Paint();
                highlightPaint.setColor(Color.YELLOW);
                highlightPaint.setStyle(Paint.Style.STROKE);
                highlightPaint.setStrokeWidth(3);
                highlightPaint.setAntiAlias(true);
                canvas.drawCircle(x, y, 10, highlightPaint);
            }
        }
    }
}

