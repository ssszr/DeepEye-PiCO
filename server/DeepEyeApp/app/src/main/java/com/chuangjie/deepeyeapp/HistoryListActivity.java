package com.chuangjie.deepeyeapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.chuangjie.deepeyeapp.models.TestSet;
import com.chuangjie.deepeyeapp.storage.DataManager;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史数据列表页面
 */
public class HistoryListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private TextView tvTitle;
    private HistoryAdapter adapter;
    private List<File> historyFiles = new ArrayList<>();
    private String testSetId;
    private TestSet testSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_list);

        testSetId = getIntent().getStringExtra("test_set_id");
        if (testSetId != null) {
            DataManager dataManager = DataManager.getInstance(this);
            testSet = dataManager.getTestSetById(testSetId);
        }

        initViews();
        loadHistoryFiles();
    }

    private void initViews() {
        Button btnBack = findViewById(R.id.btnBack);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        tvTitle = findViewById(R.id.tvTitle);

        if (testSet != null) {
            tvTitle.setText(testSet.getName() + " - 历史数据");
        }

        btnBack.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void loadHistoryFiles() {
        historyFiles.clear();
        
        // 查找两个目录：Documents 和 App专属目录
        List<File> searchDirs = new ArrayList<>();
        
        // 1. Documents 目录
        File docsDir = new File(android.os.Environment.getExternalStorageDirectory(), "Documents");
        if (docsDir.exists()) searchDirs.add(docsDir);
        
        // 2. App专属目录
        File appDir = getExternalFilesDir(null);
        if (appDir != null) searchDirs.add(appDir);
        
        for (File dir : searchDirs) {
            File[] files = dir.listFiles((d, name) -> {
                // 过滤条件：以测试集名称开头，且是.json或.txt
                boolean nameMatch = testSet == null || name.startsWith(testSet.getName());
                boolean typeMatch = name.endsWith(".json") || name.endsWith(".txt");
                return nameMatch && typeMatch;
            });
            
            if (files != null) {
                historyFiles.addAll(Arrays.asList(files));
            }
        }
        
        // 按最后修改时间排序（倒序）
        Collections.sort(historyFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        adapter.notifyDataSetChanged();
        
        if (historyFiles.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = historyFiles.get(position);
            
            holder.tvName.setText(file.getName());
            
            // 显示文件大小
            long sizeKb = file.length() / 1024;
            holder.tvSize.setText(sizeKb + " KB");
            
            // 显示修改时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            holder.tvDate.setText(sdf.format(new Date(file.lastModified())));
            
            // 标记文件类型
            if (file.getName().endsWith(".json")) {
                holder.tvFormat.setText("JSON");
                holder.tvFormat.setBackgroundResource(R.drawable.bg_tag);
                holder.btnView.setVisibility(View.VISIBLE); // 只有JSON可以在应用内查看
            } else {
                holder.tvFormat.setText("TXT");
                // 可以给TXT不同的样式
                holder.btnView.setVisibility(View.GONE); // TXT只能外部打开
            }
            
            // 删除功能
            holder.btnDelete.setOnClickListener(v -> deleteFile(file, position));
            
            // 分享功能
            holder.btnShare.setOnClickListener(v -> shareFile(file));
            
            // 查看功能（如果是JSON）
            holder.btnView.setOnClickListener(v -> viewFile(file));
            
            // 点击整个条目打开文件
            holder.itemView.setOnClickListener(v -> openFile(file));
        }

        @Override
        public int getItemCount() {
            return historyFiles.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDate, tvSize, tvFormat;
            Button btnShare, btnView, btnDelete;

            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tvSampleName);
                tvDate = view.findViewById(R.id.tvDate);
                tvSize = view.findViewById(R.id.tvSize);
                tvFormat = view.findViewById(R.id.tvFormat);
                btnShare = view.findViewById(R.id.btnShare);
                btnView = view.findViewById(R.id.btnView);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }
    
    private void deleteFile(File file, int position) {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除文件 " + file.getName() + " 吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                if (file.delete()) {
                    historyFiles.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, historyFiles.size());
                    
                    if (historyFiles.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    
                    Toast.makeText(this, "文件已删除", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void shareFile(File file) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            Uri uri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".fileprovider",
                file
            );
            
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "分享文件"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openFile(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".fileprovider",
                file
            );
            
            String mimeType = file.getName().endsWith(".json") ? "application/json" : "text/plain";
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(intent, "打开文件"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void viewFile(File file) {
        // 读取文件内容并跳转到 DataViewerActivity
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            
            String jsonContent = new String(data, "UTF-8");
            
            // 解析JSON以获取必要的字段
            org.json.JSONObject json = new org.json.JSONObject(jsonContent);
            String sampleName = json.optString("sample_name", file.getName());
            String tSetId = json.optString("test_set_id", testSetId);
            org.json.JSONObject eyeData = json.optJSONObject("eye_tracking_data");
            
            Intent intent = new Intent(this, DataViewerActivity.class);
            intent.putExtra("sample_name", sampleName);
            intent.putExtra("test_set_id", tSetId);
            intent.putExtra("data_json", eyeData != null ? eyeData.toString() : "");
            startActivity(intent);
            
        } catch (Exception e) {
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}

