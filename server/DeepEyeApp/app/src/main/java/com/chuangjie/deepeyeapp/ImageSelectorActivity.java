package com.chuangjie.deepeyeapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageSelectorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvSelectedCount;
    private Button btnConfirm;
    private ImageAdapter adapter;
    private List<ImageItem> imageList = new ArrayList<>();
    private List<String> selectedBase64List = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_selector);

        recyclerView = findViewById(R.id.recyclerView);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnConfirm = findViewById(R.id.btnConfirm);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ImageAdapter();
        recyclerView.setAdapter(adapter);

        btnConfirm.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putStringArrayListExtra("images", (ArrayList<String>) selectedBase64List);
            setResult(RESULT_OK, result);
            finish();
        });

        checkPermissionAndLoadImages();
    }

    private void checkPermissionAndLoadImages() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadImages();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadImages();
        } else {
            Toast.makeText(this, "需要权限才能读取图片", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadImages() {
        new Thread(() -> {
            String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    imageList.add(new ImageItem(path));
                }
                cursor.close();
            }

            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    private void updateSelectedCount() {
        tvSelectedCount.setText("已选: " + selectedBase64List.size());
    }

    class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ImageItem item = imageList.get(position);
            holder.imageView.setImageBitmap(BitmapFactory.decodeFile(item.path));

            holder.overlay.setVisibility(item.isSelected ? View.VISIBLE : View.GONE);
            holder.checkMark.setVisibility(item.isSelected ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                item.isSelected = !item.isSelected;

                if (item.isSelected) {
                    // 转换为Base64并添加
                    convertToBase64(item.path);
                } else {
                    // 从列表移除（简化处理）
                    selectedBase64List.clear();
                    for (ImageItem img : imageList) {
                        if (img.isSelected) {
                            convertToBase64(img.path);
                        }
                    }
                }

                notifyItemChanged(position);
                updateSelectedCount();
            });
        }

        @Override
        public int getItemCount() {
            return imageList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView, checkMark;
            View overlay;

            ViewHolder(View view) {
                super(view);
                imageView = view.findViewById(R.id.imageView);
                overlay = view.findViewById(R.id.overlay);
                checkMark = view.findViewById(R.id.checkMark);
            }
        }
    }

    private void convertToBase64(String path) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            if (!selectedBase64List.contains(base64)) {
                selectedBase64List.add(base64);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ImageItem {
        String path;
        boolean isSelected;

        ImageItem(String path) {
            this.path = path;
            this.isSelected = false;
        }
    }
}

