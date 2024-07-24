package com.example.qr_ai;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import retrofit2.Callback;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageView imageView;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private ExecutorService cameraExecutor;
    private String accessToken = null;
    private boolean qrCodeScanned = false; // Флаг для проверки, был ли QR-код уже считан


    private Button saveButton;
    private Button exitButton;



    private Bitmap generatedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        imageView = findViewById(R.id.imageView);

        saveButton = findViewById(R.id.saveButton);
        exitButton = findViewById(R.id.exitButton);

        saveButton.setOnClickListener(v -> saveImageToGallery());
        exitButton.setOnClickListener(v -> finish());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();

        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            @OptIn(markerClass = ExperimentalGetImage.class)
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            if (imageProxy.getImage() != null && planes.length > 0) {
                if (qrCodeScanned) return;
                InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
                scanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            for (Barcode barcode : barcodes) {
                                qrCodeScanned=true;
                                String rawValue = barcode.getRawValue();
                                if (rawValue != null) {
                                    Toast.makeText(this, rawValue, Toast.LENGTH_SHORT).show();
                                    getAccessTokenAndGenerateImage(rawValue);
                                }

                            }
                        })
                        .addOnFailureListener(e -> Log.e("MainActivity", "Barcode scanning failed", e))
                        .addOnCompleteListener(task -> imageProxy.close());
            } else {
                imageProxy.close();
            }
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void getAccessTokenAndGenerateImage(String query) {
        OkHttpClient client = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        Retrofit authRetrofit = new Retrofit.Builder()
                .baseUrl("https://ngw.devices.sberbank.ru:9443/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        AuthService authService = authRetrofit.create(AuthService.class);

        String basicAuth = "Basic your-basic-key";
        String rquid = UUID.randomUUID().toString();
        String scope = "GIGACHAT_API_PERS";

        authService.getToken(basicAuth, rquid, scope).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    accessToken = response.body().getAccessToken();
                    generateImageFromGigaChat(query);
                } else {
                    Log.e("MainActivity", "Failed to get access token: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Log.e("MainActivity", "Failed to get access token", t);
            }
        });
    }

    private void generateImageFromGigaChat(String query) {
        OkHttpClient client = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://gigachat.devices.sberbank.ru/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GigaChatService service = retrofit.create(GigaChatService.class);

        GenerateImageRequest request = new GenerateImageRequest(query);
        String authHeader = "Bearer " + accessToken;

        service.generateImage(authHeader, request).enqueue(new Callback<GenerateImageResponse>() {
            @Override
            public void onResponse(Call<GenerateImageResponse> call, retrofit2.Response<GenerateImageResponse> response) {
                if (/*response.isSuccessful() && */response.body() != null) {
                    Log.d("TEGG", response.body().toString());
                    String fileId = response.body().getFileId();
                    if (fileId != null) {
                        downloadAndDisplayImage(fileId);
                    }else{
                        Log.e("MainActivity", "File ID is null");
                    }
                } else {
                    Log.e("MainActivity", "Failed to generate image in onResponse: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<GenerateImageResponse> call, Throwable t) {
                Log.e("MainActivity", "Failed to generate image", t);
            }
        });
    }

    private void downloadAndDisplayImage(String fileId) {
        OkHttpClient client = UnsafeOkHttpClient.getUnsafeOkHttpClient();

        String url = "https://gigachat.devices.sberbank.ru/api/v1/files/" + fileId + "/content";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e("MainActivity", "Failed to download image", e);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    InputStream inputStream = response.body().byteStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    generatedBitmap = bitmap;
                    Log.d("TEGG2", "loaded successfully?");
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                } else {
                    Log.e("MainActivity", "Failed to download image: " + response.message());
                }
            }
        });
    }





    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static final int REQUEST_WRITE_STORAGE = 112;

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        }
    }

    private void saveImageToGallery() {
        // Запрос разрешения на запись во внешнее хранилище
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }

        if (generatedBitmap != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "generated_image.jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    generatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to get content resolver URI", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
        }
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение предоставлено, можно сохранять изображение
                saveImageToGallery();
            } else {
                Toast.makeText(this, "Permission denied to write to storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
