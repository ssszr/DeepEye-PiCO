package com.chuangjie.deepeyeapp.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据样本模型
 */
public class DataSample implements Serializable {
    private String sampleId;        // 样本ID
    private String testSetId;       // 所属测试集ID
    private String testSetName;     // 测试集名称
    private long startTime;         // 实验开始时间
    private long endTime;           // 实验结束时间
    private List<ImageEyeData> imageDataList;  // 每张图片的眼动数据
    
    public DataSample() {
        this.imageDataList = new ArrayList<>();
    }
    
    public String getSampleId() {
        return sampleId;
    }
    
    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }
    
    public String getTestSetId() {
        return testSetId;
    }
    
    public void setTestSetId(String testSetId) {
        this.testSetId = testSetId;
    }
    
    public String getTestSetName() {
        return testSetName;
    }
    
    public void setTestSetName(String testSetName) {
        this.testSetName = testSetName;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public List<ImageEyeData> getImageDataList() {
        return imageDataList;
    }
    
    public void setImageDataList(List<ImageEyeData> imageDataList) {
        this.imageDataList = imageDataList;
    }
    
    /**
     * 单张图片的眼动数据
     */
    public static class ImageEyeData implements Serializable {
        private String imageId;             // 图像ID
        private String imageEmotionTag;     // 情绪标签
        private int totalViewDuration;      // 总观看时长(毫秒)
        private long viewStartTime;         // 观看开始时间
        private long viewEndTime;           // 观看结束时间
        private String timeSeriesData;      // 时序数据JSON字符串
        
        public String getImageId() {
            return imageId;
        }
        
        public void setImageId(String imageId) {
            this.imageId = imageId;
        }
        
        public String getImageEmotionTag() {
            return imageEmotionTag;
        }
        
        public void setImageEmotionTag(String imageEmotionTag) {
            this.imageEmotionTag = imageEmotionTag;
        }
        
        public int getTotalViewDuration() {
            return totalViewDuration;
        }
        
        public void setTotalViewDuration(int totalViewDuration) {
            this.totalViewDuration = totalViewDuration;
        }
        
        public long getViewStartTime() {
            return viewStartTime;
        }
        
        public void setViewStartTime(long viewStartTime) {
            this.viewStartTime = viewStartTime;
        }
        
        public long getViewEndTime() {
            return viewEndTime;
        }
        
        public void setViewEndTime(long viewEndTime) {
            this.viewEndTime = viewEndTime;
        }
        
        public String getTimeSeriesData() {
            return timeSeriesData;
        }
        
        public void setTimeSeriesData(String timeSeriesData) {
            this.timeSeriesData = timeSeriesData;
        }
    }
}

