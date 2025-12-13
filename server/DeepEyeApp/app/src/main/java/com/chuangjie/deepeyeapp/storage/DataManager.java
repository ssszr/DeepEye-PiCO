package com.chuangjie.deepeyeapp.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.chuangjie.deepeyeapp.models.DataSample;
import com.chuangjie.deepeyeapp.models.TestSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据管理器 - 使用SharedPreferences进行简单存储
 */
public class DataManager {
    private static final String PREF_NAME = "DeepEyeData";
    private static final String KEY_TEST_SETS = "test_sets";
    private static final String KEY_DATA_SAMPLES = "data_samples";
    
    private static DataManager instance;
    private SharedPreferences prefs;
    private Gson gson;
    
    private DataManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }
    
    // ========== 测试集管理 ==========
    
    public List<TestSet> getAllTestSets() {
        String json = prefs.getString(KEY_TEST_SETS, "[]");
        Type type = new TypeToken<List<TestSet>>(){}.getType();
        return gson.fromJson(json, type);
    }
    
    public void saveTestSet(TestSet testSet) {
        List<TestSet> list = getAllTestSets();
        
        // 检查是否已存在，更新或新增
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(testSet.getId())) {
                list.set(i, testSet);
                found = true;
                break;
            }
        }
        
        if (!found) {
            list.add(testSet);
        }
        
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_TEST_SETS, json).apply();
    }
    
    public void deleteTestSet(String testSetId) {
        List<TestSet> list = getAllTestSets();
        list.removeIf(ts -> ts.getId().equals(testSetId));
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_TEST_SETS, json).apply();
    }
    
    public TestSet getTestSetById(String testSetId) {
        List<TestSet> list = getAllTestSets();
        for (TestSet ts : list) {
            if (ts.getId().equals(testSetId)) {
                return ts;
            }
        }
        return null;
    }
    
    // ========== 数据样本管理 ==========
    
    public List<DataSample> getAllDataSamples() {
        String json = prefs.getString(KEY_DATA_SAMPLES, "[]");
        Type type = new TypeToken<List<DataSample>>(){}.getType();
        return gson.fromJson(json, type);
    }
    
    public void saveDataSample(DataSample sample) {
        List<DataSample> list = getAllDataSamples();
        list.add(sample);
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_DATA_SAMPLES, json).apply();
    }
    
    public List<DataSample> getDataSamplesByTestSetId(String testSetId) {
        List<DataSample> all = getAllDataSamples();
        List<DataSample> result = new ArrayList<>();
        for (DataSample sample : all) {
            if (sample.getTestSetId().equals(testSetId)) {
                result.add(sample);
            }
        }
        return result;
    }
    
    public void deleteDataSample(String sampleId) {
        List<DataSample> list = getAllDataSamples();
        list.removeIf(s -> s.getSampleId().equals(sampleId));
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_DATA_SAMPLES, json).apply();
    }
}

