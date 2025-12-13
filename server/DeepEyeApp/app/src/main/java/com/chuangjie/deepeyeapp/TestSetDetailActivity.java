package com.chuangjie.deepeyeapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.chuangjie.deepeyeapp.models.TestSet;
import com.chuangjie.deepeyeapp.storage.DataManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试集详情页面 - 图片管理
 */
public class TestSetDetailActivity extends AppCompatActivity {

    private TextView tvTestSetName, tvImageCount;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private ImageAdapter adapter;
    
    private DataManager dataManager;
    private TestSet testSet;
    private String testSetId;
    private List<String> imageBase64List = new ArrayList<>();
    
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_set_detail);

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
        setupImagePicker();
        loadImages();
    }

    private void initViews() {
        Button btnBack = findViewById(R.id.btnBack);
        Button btnAddImages = findViewById(R.id.btnAddImages);
        tvTestSetName = findViewById(R.id.tvTestSetName);
        tvImageCount = findViewById(R.id.tvImageCount);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);

        tvTestSetName.setText(testSet.getName());

        btnBack.setOnClickListener(v -> finish());
        btnAddImages.setOnClickListener(v -> selectImages());

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ImageAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleImageSelection(result.getData());
                }
            }
        );
    }

    private void selectImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(intent);
    }

    private void handleImageSelection(Intent data) {
        List<String> newImages = new ArrayList<>();
        
        if (data.getClipData() != null) {
            // 多张图片
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri imageUri = data.getClipData().getItemAt(i).getUri();
                String base64 = convertImageToBase64(imageUri);
                if (base64 != null) {
                    newImages.add(base64);
                }
            }
        } else if (data.getData() != null) {
            // 单张图片
            Uri imageUri = data.getData();
            String base64 = convertImageToBase64(imageUri);
            if (base64 != null) {
                newImages.add(base64);
            }
        }

        if (!newImages.isEmpty()) {
            imageBase64List.addAll(newImages);
            saveImages();
            updateImageCount();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "已添加 " + newImages.size() + " 张图片", Toast.LENGTH_SHORT).show();
        }
    }

    private String convertImageToBase64(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            
            // 压缩图片
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void loadImages() {
        imageBase64List.clear();
        imageBase64List.addAll(testSet.getImageBase64List());
        updateImageCount();
        adapter.notifyDataSetChanged();
    }

    private void saveImages() {
        testSet.setImageBase64List(new ArrayList<>(imageBase64List));
        dataManager.saveTestSet(testSet);
    }

    private void updateImageCount() {
        tvImageCount.setText("共 " + imageBase64List.size() + " 张图片");
        
        if (imageBase64List.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void deleteImage(int position) {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这张图片吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                imageBase64List.remove(position);
                saveImages();
                updateImageCount();
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, imageBase64List.size());
            })
            .setNegativeButton("取消", null)
            .show();
    }

    class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image_manage, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String base64 = imageBase64List.get(position);
            
            try {
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                holder.imageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            holder.btnDelete.setOnClickListener(v -> deleteImage(position));
        }

        @Override
        public int getItemCount() {
            return imageBase64List.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView btnDelete;

            ViewHolder(View view) {
                super(view);
                imageView = view.findViewById(R.id.imageView);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }
}
