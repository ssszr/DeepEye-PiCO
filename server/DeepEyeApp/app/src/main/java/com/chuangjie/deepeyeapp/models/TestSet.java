package com.chuangjie.deepeyeapp.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 测试集模型
 */
public class TestSet implements Serializable {
    private String id;              // 测试集ID
    private String name;            // 测试集名称
    private long createTime;        // 创建时间
    private List<String> imageBase64List;  // 图片Base64列表
    
    public TestSet() {
        this.id = UUID.randomUUID().toString();
        this.createTime = System.currentTimeMillis();
        this.imageBase64List = new ArrayList<>();
    }
    
    public TestSet(String name) {
        this();
        this.name = name;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public List<String> getImageBase64List() {
        return imageBase64List;
    }
    
    public void setImageBase64List(List<String> imageBase64List) {
        this.imageBase64List = imageBase64List;
    }
    
    public int getImageCount() {
        return imageBase64List != null ? imageBase64List.size() : 0;
    }
}

