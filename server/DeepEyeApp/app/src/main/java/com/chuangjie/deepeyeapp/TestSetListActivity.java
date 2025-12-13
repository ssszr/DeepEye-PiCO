package com.chuangjie.deepeyeapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.chuangjie.deepeyeapp.models.TestSet;
import com.chuangjie.deepeyeapp.storage.DataManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 测试集列表页面
 */
public class TestSetListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private TestSetAdapter adapter;
    private List<TestSet> testSetList = new ArrayList<>();
    private DataManager dataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_set_list);

        dataManager = DataManager.getInstance(this);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnCreateTestSet = findViewById(R.id.btnCreateTestSet);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);

        btnBack.setOnClickListener(v -> finish());
        btnCreateTestSet.setOnClickListener(v -> showCreateDialog());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TestSetAdapter();
        recyclerView.setAdapter(adapter);

        loadTestSets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTestSets();
    }

    private void loadTestSets() {
        testSetList = dataManager.getAllTestSets();
        adapter.notifyDataSetChanged();

        if (testSetList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建测试集");

        EditText input = new EditText(this);
        input.setHint("请输入测试集名称");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(50, 20, 50, 20);
        input.setLayoutParams(lp);

        LinearLayout container = new LinearLayout(this);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                TestSet testSet = new TestSet(name);
                dataManager.saveTestSet(testSet);
                loadTestSets();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    class TestSetAdapter extends RecyclerView.Adapter<TestSetAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_test_set, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TestSet testSet = testSetList.get(position);

            holder.tvName.setText(testSet.getName());
            holder.tvImageCount.setText(String.valueOf(testSet.getImageCount()));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            holder.tvCreateTime.setText(sdf.format(new Date(testSet.getCreateTime())));

            // 管理图片
            holder.btnManageImages.setOnClickListener(v -> {
                Intent intent = new Intent(TestSetListActivity.this, TestSetDetailActivity.class);
                intent.putExtra("test_set_id", testSet.getId());
                startActivity(intent);
            });

            // 开始测试
            holder.btnStartExperiment.setOnClickListener(v -> {
                Intent intent = new Intent(TestSetListActivity.this, ExperimentActivity.class);
                intent.putExtra("test_set_id", testSet.getId());
                startActivity(intent);
            });
            
            // 历史数据
            holder.btnHistory.setOnClickListener(v -> {
                Intent intent = new Intent(TestSetListActivity.this, HistoryListActivity.class);
                intent.putExtra("test_set_id", testSet.getId());
                startActivity(intent);
            });

            // 删除
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(TestSetListActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除测试集「" + testSet.getName() + "」吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            dataManager.deleteTestSet(testSet.getId());
                            loadTestSets();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return testSetList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvImageCount, tvCreateTime, btnDelete;
            Button btnManageImages, btnStartExperiment, btnHistory;

            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.tvTestSetName);
                tvImageCount = view.findViewById(R.id.tvImageCount);
                tvCreateTime = view.findViewById(R.id.tvCreateTime);
                btnManageImages = view.findViewById(R.id.btnManageImages);
                btnStartExperiment = view.findViewById(R.id.btnStartExperiment);
                btnHistory = view.findViewById(R.id.btnHistory);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }
}

