package com.example.attributeestimator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 101;
    private String currentPhotoPath;
    private Uri photoURI; // Store the URI for later access

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize buttons
        Button btnSelfie = findViewById(R.id.btn_selfie);
        Button btnObject = findViewById(R.id.btn_object);

        // Request camera permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        // Handle "Take Selfie" button click
        btnSelfie.setOnClickListener(v -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Toast.makeText(this, "Error creating file!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (photoFile != null) {
                    photoURI = FileProvider.getUriForFile(this,
                            "com.example.attributeestimator.fileprovider",
                            photoFile);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
                }
            }
        });

        btnObject.setOnClickListener(v -> {
            // Object capture logic
        });
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (storageDir == null || !storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                throw new IOException("Failed to create directory");
            }
        }

        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                // Check if file exists
                File file = new File(currentPhotoPath);
                if (!file.exists()) {
                    Toast.makeText(this, "File not found!", Toast.LENGTH_LONG).show();
                    return;
                }

                // Decode with downsampling to avoid memory issues
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2; // Reduce resolution by half
                Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, options);

                if (bitmap == null) {
                    Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Rest of pose detection logic
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                PoseDetector detector = PoseDetection.getClient(
                        new PoseDetectorOptions.Builder()
                                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                                .build()
                );

                detector.process(image)
                        .addOnSuccessListener(pose -> {
                            // ====== HEIGHT CALCULATION LOGIC ======
                            List<PoseLandmark> landmarks = pose.getAllPoseLandmarks();
                            if (landmarks.isEmpty()) {
                                Toast.makeText(this, "No person detected!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Get key body points
                            PoseLandmark nose = pose.getPoseLandmark(PoseLandmark.NOSE);
                            PoseLandmark leftHeel = pose.getPoseLandmark(PoseLandmark.LEFT_HEEL);
                            PoseLandmark rightHeel = pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL);

                            // Validate required landmarks
                            if (nose == null || leftHeel == null || rightHeel == null) {
                                Toast.makeText(this, "Key points missing!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Calculate pixel distance
                            float averageHeelY = (leftHeel.getPosition().y + rightHeel.getPosition().y) / 2;
                            float pixelHeight = averageHeelY - nose.getPosition().y;

                            // Convert to real-world height (calibrate this value!)
                            float conversionFactor = 0.1f; // Temporary value
                            float estimatedHeight = pixelHeight * conversionFactor;

                            // Show result
                            Toast.makeText(this,
                                    String.format("Estimated height: %.1f cm", estimatedHeight),
                                    Toast.LENGTH_LONG).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Detection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e("PoseDetection", "Error", e);
                        });

            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "Error processing image", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}