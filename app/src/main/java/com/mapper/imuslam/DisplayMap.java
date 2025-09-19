package com.mapper.imuslam;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
//import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.ux.ArFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DisplayMap extends AppCompatActivity implements ZoomPanLayout.OnMapTappedListener, ZoomPanLayout.OnZoomPanListener {

    private static final String TAG = "DisplayMap";
    private static final int REQUEST_CODE_ACTIVITY_RECOGNITION = 1001;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1002;
    private static int STEPS = 0;
    TextView path_calibration_subtitle;
    boolean crtptAdded=false;
    private LinearLayout sensorLayout, pathLayout, menuLayout;

    // Progress for screen 1
    private ProgressBar circleProgress;
    private TextView timerText;
    boolean mapClickable = false;

    // Progress for screen 2
    private ProgressBar stepProgress;
    private TextView stepCountText;

    // Timer
    private CountDownTimer sensorTimer;
    private boolean applyPathCorrectionMode = false;
    private String mapHeightStr, mapWidthStr, imageUri, startXStr, startYStr, mapFolderPath;
    private float mapHeight, mapWidth, startX, startY;

    private ZoomPanLayout rootLayout;
    private ImageView mapView;
    //    private TextView coordinatesText;
//    private TextView stepsDistanceTextView;
    private TextView CoordinatesTXT, stepsTXT, distanceTXT;
    private View pointerView;
    private float correctionAngle = 0f;     // radians
    private float correctionTransX = 0f;    // map units
    private float correctionTransY = 0f;    // map units
    private boolean correctionActive = false;
    private int pointerSize;
    private float showx, showy;
    private static final int POINTER_SIZE_DP = 20;

    private ArFragment arFragment;
    private float lastMapX, lastMapY,lastCORDx,lastCORDy;

    public boolean trailing;
    private int correction_pointer=0;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private File mapFolder;
    private TrailingLineView trailingLineView;

    private CardView resetZoomButton;
    private boolean isZoomed = false;

    Settings settings;

    private StepCounterManager stepCounterManager;
    private final Handler metricsHandler = new Handler();
    private final Runnable metricsRunnable = new Runnable() {
        @Override
        public void run() {
            updateMetricsUI();
            metricsHandler.postDelayed(this, 1000);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_map);
        settings = Settings.getInstance();
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        View decorView = getWindow().getDecorView();
        mapClickable = false;
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQUEST_CODE_ACTIVITY_RECOGNITION);
            }
        }
        sensorLayout = findViewById(R.id.calinrating_Sensors);
        pathLayout = findViewById(R.id.path_Calibration);
        menuLayout = findViewById(R.id.main_menu);

        circleProgress = findViewById(R.id.circleProgress);
        timerText = findViewById(R.id.timerText);

        stepProgress = findViewById(R.id.stepProgress);
        stepCountText = findViewById(R.id.stepCountText);

        Toolbar appBar = findViewById(R.id.appbar);
        setSupportActionBar(appBar);
        trailing = settings.getTrailingFlag();
        stepCounterManager = StepCounterManager.getInstance(this);
        stepCounterManager.resetSessionSteps();
        sensorLayout.setVisibility(View.VISIBLE);
        pathLayout.setVisibility(View.GONE);
        menuLayout.setVisibility(View.GONE);
        Intent intent = getIntent();


        if (intent != null) {
            imageUri = intent.getStringExtra("imageUri");
            mapHeightStr = intent.getStringExtra("mapHeight");
            mapWidthStr = intent.getStringExtra("mapWidth");
            startXStr = intent.getStringExtra("startX");
            startYStr = intent.getStringExtra("startY");
            mapFolderPath = intent.getStringExtra("mapFolderPath");
            try {
                mapHeight = Float.parseFloat(mapHeightStr);
                mapWidth = Float.parseFloat(mapWidthStr);
                startX = Float.parseFloat(startXStr);
                startY = Float.parseFloat(startYStr);
                Log.d("KR1", "mapHeight : " + mapHeight + ", mapWidth : " + mapWidth + ", startX : " + startX + ", startY : " + startY);
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.Invalid_map_parameters), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
        lastMapX = startX;
        lastMapY = startY;

        File parent = new File(getExternalFilesDir(null), "SLAM_MAPS");
        if (!parent.exists()) {
            parent.mkdirs();
        }
        String folderName = "SLAM_Map_" +
                new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date());
        mapFolder = new File(parent, folderName);
        if (!mapFolder.exists()) {
            mapFolder.mkdirs();
        }

        int displayWidth = getResources().getDisplayMetrics().widthPixels - 10;
        int displayHeight = getResources().getDisplayMetrics().heightPixels - 10;
        boolean isLandscape = displayWidth > displayHeight;
        int a = (int) (60 * this.getResources().getDisplayMetrics().density);
        if (isLandscape) {
            displayHeight = displayHeight - a;
        }
        int layoutWidth, layoutHeight;
        if (isLandscape) {
            layoutWidth = (int) ((displayHeight * mapWidth) / mapHeight);
            layoutHeight = displayHeight;
        } else {
            layoutWidth = displayWidth;
            layoutHeight = (int) ((displayWidth * mapHeight) / mapWidth);
        }
        resetZoomButton = findViewById(R.id.resetZoom_card);

        rootLayout = findViewById(R.id.root_layout);
        rootLayout.setOnMapTappedListener(this);
        rootLayout.setOnZoomPanListener(this);
        mapView = findViewById(R.id.mapView);
//        coordinatesText = findViewById(R.id.coordinates);
//        stepsDistanceTextView = findViewById(R.id.stepsDistanceTextView);
        stepsTXT = findViewById(R.id.steps_TXT);
        distanceTXT = findViewById(R.id.distanceTXT);
        CoordinatesTXT = findViewById(R.id.CoordinatesTXT);
        path_calibration_subtitle=findViewById(R.id.subtitleText2);
        ConstraintLayout.LayoutParams clp = (ConstraintLayout.LayoutParams) rootLayout.getLayoutParams();
        clp.width = layoutWidth;
        clp.height = layoutHeight;
        rootLayout.setLayoutParams(clp);

        if (imageUri != null) {
            mapView.setImageURI(Uri.parse(imageUri));
            mapView.setVisibility(VISIBLE);
        }

        pointerSize = (int) (POINTER_SIZE_DP * getResources().getDisplayMetrics().density + 0.5f);
        pointerView = new View(this);
        FrameLayout.LayoutParams pointerLp = new FrameLayout.LayoutParams(pointerSize, pointerSize);
        pointerView.setLayoutParams(pointerLp);
        pointerView.setBackgroundResource(R.drawable.pointer);
        pointerView.setVisibility(View.VISIBLE);
        rootLayout.addView(pointerView);
        Log.d("KR1", "onCreate: " + String.valueOf(findViewById(R.id.ar_fragment_container) != null));
        if (findViewById(R.id.ar_fragment_container) != null) {
            arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment_container);
            if (arFragment == null) {
                arFragment = new ArFragment();
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.ar_fragment_container, arFragment)
                        .commitAllowingStateLoss();
            }
            registerArUpdateListener();
        }

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleCameraResult(result.getData());
                    }
                });
        rootLayout.setZoomEnabled(false);
        resetZoomButton.setOnClickListener(v -> {
            rootLayout.resetZoom();
            isZoomed = false;
            resetZoomButton.setVisibility(GONE);
        });
//        ApplyPathCorrection.setOnClickListener(v -> {
        rootLayout.resetZoom();
        isZoomed = false;
        resetZoomButton.setVisibility(GONE);
        applyPathCorrectionMode = false;
//           Toast.makeText(this, getString(R.string.click_on_map_for_path_correction), Toast.LENGTH_SHORT).show();
//            ApplyPathCorrection.setVisibility(GONE);
//        });
        CardView clickPhotoButton = findViewById(R.id.AddPhoto_card);
        clickPhotoButton.setOnClickListener(v -> {
            addStaticMarker();
            openCamera();
        });

        CardView endMapButton = findViewById(R.id.endMap_card);
        endMapButton.setOnClickListener(v -> endMapSession());

        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int width = mapView.getWidth();
                int height = mapView.getHeight();

                trailingLineView = new TrailingLineView(DisplayMap.this);
                FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(width, height);
                trailingLineView.setLayoutParams(overlayLp);
                trailingLineView.setElevation(1f);
                rootLayout.addView(trailingLineView);
                pointerView.bringToFront();
            }
        });
        startSensorCalibration();
    }

    private float[] rotatePoint(float x, float y, float cx, float cy, float angle,float lastx,float lasty) {
        Log.d("Kr", "Correction Angle : " + Math.toDegrees(angle));
        float dx = x - cx;
        float dy = y - cy;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float rx = dx * cos - dy * sin + cx;
        float ry = dx * sin + dy * cos + cy;
        return new float[]{rx, ry};
    }

    @Override
    public void onMapTapped(float touchX, float touchY) {
        if(mapClickable){
        if (isZoomed) {
            Toast.makeText(this, getString(R.string.Reset_zoom_before_correction), Toast.LENGTH_SHORT).show();
            return;
        }
        if (applyPathCorrectionMode && isZoomed) {
            Toast.makeText(this, getString(R.string.Reset_zoom_before_correction), Toast.LENGTH_SHORT).show();
            rootLayout.resetZoom();
            isZoomed = false;
            resetZoomButton.setVisibility(GONE);
            return;
        }

        int containerWidth = mapView.getWidth();
        int containerHeight = mapView.getHeight();

        float correctedMapX = (touchX / containerWidth) * mapWidth;
        float correctedMapY = mapHeight - ((touchY / containerHeight) * mapHeight);

        correctedMapX = Math.max(0, Math.min(correctedMapX, mapWidth));
        correctedMapY = Math.max(0, Math.min(correctedMapY, mapHeight));

        final float finalCorrectedMapX = correctedMapX;
        final float finalCorrectedMapY = correctedMapY;

        if (applyPathCorrectionMode)
        {

            // PATH (rotation) CORRECTION
            new AlertDialog.Builder(DisplayMap.this)
                    .setTitle(getString(R.string.apply_path_correction))
                    .setMessage(String.format(Locale.getDefault(),
                            getString(R.string.apply_path_calibration_prompt), finalCorrectedMapX, finalCorrectedMapY))
                    .setPositiveButton(getString(R.string.Yes), (dialog, which) -> {

                        // Prefer raw current map point from AR frame (so we compute deltaAngle from raw values)
                        float currentMapX;
                        float currentMapY;
                        com.google.ar.core.Frame frame = null;
                        if (arFragment != null && arFragment.getArSceneView() != null) {
                            frame = arFragment.getArSceneView().getArFrame();
                        }
                        if (frame != null) {
                            Pose cameraPose = frame.getCamera().getPose();
                            float displacementX = cameraPose.tx();
                            float displacementY = cameraPose.tz();
                            currentMapX = startX + displacementX;
                            currentMapY = startY - displacementY;
                        } else {
                            // fallback to lastMap if AR frame not available
                            currentMapX = lastMapX;
                            currentMapY = lastMapY;
                        }

                        // compute delta angle between A->C (old) and A->B (new tapped)
                        float angleOld = (float) Math.atan2(currentMapY - startY, currentMapX - startX);
                        float angleNew = (float) Math.atan2(finalCorrectedMapY - startY, finalCorrectedMapX - startX);
                        float deltaAngle = angleNew - angleOld;

                        // rotate current around A by deltaAngle
                        float[] rotatedC = rotatePoint(currentMapX, currentMapY, startX, startY, deltaAngle,lastCORDx,lastCORDy);

                        // translation so rotatedC lands on B
                        float deltaTransX = finalCorrectedMapX - rotatedC[0];
                        float deltaTransY = finalCorrectedMapY - rotatedC[1];

                        // store transform for future points
                        correctionAngle = deltaAngle;
                        correctionTransX = deltaTransX;
                        correctionTransY = deltaTransY;
                        correctionActive = true;

                        // set lastMap to corrected B
                        lastMapX = finalCorrectedMapX;
                        lastMapY = finalCorrectedMapY;

                        // update pointer on map (pixel conversion)
                        float pixelX = (finalCorrectedMapX / mapWidth) * containerWidth;
                        float pixelY = containerHeight - ((finalCorrectedMapY / mapHeight) * containerHeight);
                        float adjustedX = pixelX - (pointerSize / 2f);
                        float adjustedY = pixelY - (pointerSize / 2f);
                        float clampedX = Math.max(0, Math.min(adjustedX, containerWidth - pointerSize));
                        float clampedY = Math.max(0, Math.min(adjustedY, containerHeight - pointerSize));
                        pointerView.setX(clampedX);
                        pointerView.setY(clampedY);
                        pointerView.setVisibility(VISIBLE);
//                        coordinatesText.setText(String.format(Locale.getDefault(),
//                                "X: %.2f, Y: %.2f", finalCorrectedMapX, finalCorrectedMapY));
                        CoordinatesTXT.setText(String.format(Locale.getDefault(),
                                "%.1f,%.1f", finalCorrectedMapX, finalCorrectedMapY));
                        // Clear old trail but keep start; then add A->B segment (pixel coords)
                        if (trailing && trailingLineView != null) {
                            trailingLineView.clearExceptStart();
                            float centerX = clampedX + (pointerSize / 2f);
                            float centerY = clampedY + (pointerSize / 2f);
                            trailingLineView.addPoint(centerX, centerY);
                            lastCORDx=centerX;lastCORDy=centerY;
                            Log.d("KR2","Point Added : "+centerX+","+centerY);
                        }

                        // exit correction mode
                        applyPathCorrectionMode = false;

                        showMenuLayout();
//                        ApplyPathCorrection.setVisibility(VISIBLE);

                        Toast.makeText(DisplayMap.this, "Callibration Success", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(getString(R.string.Cancel), (d, w) -> {
//                        applyPathCorrectionMode = false;
//                        ApplyPathCorrection.setVisibility(VISIBLE);
                    })
                    .show();
        }
        else
        {
            // POSITION CORRECTION (your old behaviour), but also reset any rotation-transform
            new AlertDialog.Builder(DisplayMap.this)
                    .setTitle(getString(R.string.Confirm_Correction))
                    .setMessage(String.format(Locale.getDefault(),
                            getString(R.string.Set_pointer_to) + " X: %.2f, Y: %.2f?", correctedMapX, correctedMapY))
                    .setPositiveButton(getString(R.string.Yes), (dialog, which) -> {
                        com.google.ar.core.Frame frame = null;
                        if (arFragment != null && arFragment.getArSceneView() != null) {
                            frame = arFragment.getArSceneView().getArFrame();
                        }
                        if (frame != null) {
                            Pose cameraPose = frame.getCamera().getPose();
                            float displacementX = cameraPose.tx();
                            float displacementY = cameraPose.tz();
                            startX = finalCorrectedMapX - displacementX;
                            startY = finalCorrectedMapY + displacementY;
                        } else {
                            // fallback: set start to tapped point (conservative)
                            startX = finalCorrectedMapX;
                            startY = finalCorrectedMapY;
                        }

                        // Reset any active path-rotation transform because this is a position correction
//                        correctionActive = false;
//                        correctionAngle = 0f;
//                        correctionTransX = 0f;
//                        correctionTransY = 0f;

                        lastMapX = finalCorrectedMapX;
                        lastMapY = finalCorrectedMapY;
                        CoordinatesTXT.setText(String.format(Locale.getDefault(),
                                "%.1f,%.1f", finalCorrectedMapX, finalCorrectedMapY));

                        float pixelX = (finalCorrectedMapX / mapWidth) * containerWidth;
                        float pixelY = containerHeight - ((finalCorrectedMapY / mapHeight) * containerHeight);
                        float adjustedX = pixelX - (pointerSize / 2f);
                        float adjustedY = pixelY - (pointerSize / 2f);
                        float clampedX = Math.max(0, Math.min(adjustedX, containerWidth - pointerSize));
                        float clampedY = Math.max(0, Math.min(adjustedY, containerHeight - pointerSize));

                        pointerView.setX(clampedX);
                        pointerView.setY(clampedY);
                        pointerView.setVisibility(VISIBLE);

                        if (trailing && trailingLineView != null) {
//                            trailingLineView.clearExceptStart();
                            float centerX = clampedX + (pointerSize / 2f);
                            float centerY = clampedY + (pointerSize / 2f);
                            trailingLineView.addPoint(centerX, centerY);
                            lastCORDx=centerX;lastCORDy=centerY;
                            startX=centerX;startY=centerY;

                            Log.d("KR2_L","Point Added : "+centerX+","+centerY);
                            crtptAdded=true;
                        }
                        correctionActive = false;
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        }
    }
    }

    @Override
    public void onZoomOrPan() {
        if (!isZoomed) {
            isZoomed = true;
            resetZoomButton.setVisibility(VISIBLE);
        }
    }

    private void endMapSession() {
        Toast.makeText(this, "Ending map session...", Toast.LENGTH_SHORT).show();
        rootLayout.resetZoom();
        isZoomed = false;
        resetZoomButton.setVisibility(GONE);
        rootLayout.postDelayed(() -> {
            Bitmap mergedMapBitmap = captureViewBitmap(rootLayout);
            if (mergedMapBitmap != null) {
                saveBitmapAndMetrics(mergedMapBitmap);
            } else {
                Toast.makeText(this, getString(R.string.Failed_to_capture_merged_map), Toast.LENGTH_SHORT).show();
            }
            Intent intent1 = new Intent(DisplayMap.this, BaseActivity.class);
            intent1.putExtra("fragmentToLoad", "savedMap");
            startActivity(intent1);
            finish();
        }, 350);
    }

    private void saveBitmapAndMetrics(Bitmap bitmapToSave) {
        String timestampStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File mergedFile = new File(mapFolder, "mapPhoto_" + timestampStr + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
            bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            Log.d(TAG, "Merged map saved at: " + mergedFile.getAbsolutePath());
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.Error_saving_merged_map) + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error saving merged map", e);
        }

        try {
            JSONObject metricsObj = new JSONObject();
            metricsObj.put("steps", stepCounterManager.getSessionSteps());
            metricsObj.put("distance", stepCounterManager.getDistanceMeters());
            metricsObj.put("h", mapHeight);
            metricsObj.put("w", mapWidth);
            metricsObj.put("imageUri", imageUri);
            File metricsFile = new File(mapFolder, "metrics.json");
            try (FileOutputStream fos = new FileOutputStream(metricsFile)) {
                fos.write(metricsObj.toString().getBytes());
            }
            SessionManager.saveLastMapPath(DisplayMap.this, mapFolder.getAbsolutePath());
            Log.d(TAG, "Metrics saved at: " + metricsFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving metrics", e);
        }
    }

    private Bitmap captureViewBitmap(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error capturing view bitmap", e);
            return null;
        }
    }

    private void updateMetricsUI() {
        runOnUiThread(() -> {
            if (stepsTXT == null) return;
            int steps = stepCounterManager.getSessionSteps();
            float distance = stepCounterManager.getDistanceMeters();
            STEPS = steps;
            Log.d("KR", "Steps : " + STEPS);
            String steps_distane_text = getString(R.string.steps_info, steps, distance);
//            stepsDistanceTextView.setText(steps_distane_text);
            stepsTXT.setText("" + steps);
            distanceTXT.setText(distance + " m");
            if (pathLayout != null && pathLayout.getVisibility() == View.VISIBLE) {
                stepProgress.setMax(15);
                stepProgress.setProgress(steps);
                stepCountText.setText(steps + "");

                if (steps >= 15) {

                    mapClickable = true;
                    applyPathCorrectionMode = true;
                    stepProgress.setVisibility(INVISIBLE);
                    path_calibration_subtitle.setText(getString(R.string.click_on_map_for_path_correction));
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        stepCounterManager.register();
        metricsHandler.post(metricsRunnable);
    }

    @Override
    protected void onPause() {
        stepCounterManager.unregister();
        metricsHandler.removeCallbacks(metricsRunnable);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, getString(R.string.Camera_permission_required), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void registerArUpdateListener() {
        if (arFragment.getArSceneView() != null) {
            arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
            Log.d("KRTAG", "AR session: Started");
        } else {
            Log.d("KRTAG", "AR session: inelse");
            new Handler().postDelayed(this::registerArUpdateListener, 100);
        }
    }

    private void onUpdateFrame(com.google.ar.sceneform.FrameTime frameTime) {
        Log.d(TAG, "ARFragment session: " + (arFragment.getArSceneView().getSession() != null));
        if (arFragment == null || arFragment.getArSceneView() == null || arFragment.getArSceneView().getArFrame() == null) {
            return;
        }
        com.google.ar.core.Frame frame = arFragment.getArSceneView().getArFrame();
        Pose cameraPose = frame.getCamera().getPose();
        float displacementX = cameraPose.tx();
        float displacementY = cameraPose.tz();

        // RAW map coordinates from AR (uncorrected)
        float rawMapX = startX + displacementX;
        float rawMapY = startY - displacementY;

        // Apply correction transform (if active) to get the coordinates we will DISPLAY & record
        float correctedX = rawMapX;
        float correctedY = rawMapY;

         if (correctionActive) {

            float[] rotated = rotatePoint(rawMapX, rawMapY, startX, startY, correctionAngle,lastCORDx,lastCORDy);
            correctedX = rotated[0] + correctionTransX;
            correctedY = rotated[1] + correctionTransY;

        }
        Log.d("KR", "rawMapX: " + rawMapX + " rawMapY: " + rawMapY + " Corrected X and Y : " + correctedX + " , " + correctedY + " Correction Angle :" + correctionAngle);
        // Use corrected coords from here onward (important!)
        lastMapX = correctedX;
        lastMapY = correctedY;

        // Clamp for display
        showx = Math.max(0, Math.min(correctedX, mapWidth));
        showy = Math.max(0, Math.min(correctedY, mapHeight));
        CoordinatesTXT.setText(String.format(Locale.getDefault(), "%.1f,%.1f", showx, showy));

        int containerWidth = mapView.getWidth();
        int containerHeight = mapView.getHeight();
        if (containerWidth == 0 || containerHeight == 0) return;

        // Map corrected map coords -> pixel coords
        float pixelX = (correctedX / mapWidth) * containerWidth;
        float pixelY = containerHeight - ((correctedY / mapHeight) * containerHeight);
        float adjustedX = pixelX - (pointerSize / 2f);
        float adjustedY = pixelY - (pointerSize / 2f);
        float clampedX = Math.max(0, Math.min(adjustedX, containerWidth - pointerSize));
        float clampedY = Math.max(0, Math.min(adjustedY, containerHeight - pointerSize));

        pointerView.setX(clampedX);
        pointerView.setY(clampedY);
        pointerView.setVisibility(VISIBLE);

        if (trailing && trailingLineView != null) {
            float centerX = clampedX + (pointerSize / 2f);
            float centerY = clampedY + (pointerSize / 2f);
            trailingLineView.addPoint(centerX, centerY);
            lastCORDx=centerX;lastCORDy=centerY;
            Log.d("KR2","Point Added : "+centerX+","+centerY);
        }
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(cameraIntent);
    }

    private void handleCameraResult(Intent data) {
        if (data != null && data.getExtras() != null) {
            Bitmap cameraPhoto = (Bitmap) data.getExtras().get("data");
            if (cameraPhoto != null) {
                savePhoto(cameraPhoto);
            }
        }
    }

    private void savePhoto(Bitmap cameraPhoto) {
        try {
            String timestampStr = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date());
            File file = new File(mapFolder, "IMG_" + timestampStr + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                cameraPhoto.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            updatePhotosMetadata(file.getName(), lastMapX, lastMapY, mapWidth, mapHeight);
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.Error_saving_photo) + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error saving photo", e);
        }
    }

    private void updatePhotosMetadata(String photoFileName, float x, float y, float origMapWidth, float origMapHeight) {
        try {
            File jsonFile = new File(mapFolder, "photos.json");
            JSONArray jsonArray = new JSONArray();
            if (jsonFile.exists()) {
                // Read existing content
            }
            JSONObject newEntry = new JSONObject();
            newEntry.put("mapX", x);
            newEntry.put("mapY", y);
            newEntry.put("cameraPhoto", photoFileName);
            newEntry.put("mapWidth", origMapWidth);
            newEntry.put("mapHeight", origMapHeight);
            jsonArray.put(newEntry);
            try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                fos.write(jsonArray.toString().getBytes());
            }
            Toast.makeText(this, getString(R.string.Metadata_updated), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error updating metadata", e);
        }
    }

    private void addStaticMarker() {
        runOnUiThread(() -> {
            int containerWidth = mapView.getWidth();
            int containerHeight = mapView.getHeight();
            if (containerWidth == 0) return;

            float pixelX = (lastMapX / mapWidth) * containerWidth;
            float pixelY = containerHeight - ((lastMapY / mapHeight) * containerHeight);

            int staticMarkerSize = (int) (20 * getResources().getDisplayMetrics().density);
            float left = pixelX - (staticMarkerSize / 2f);
            float top = pixelY - (staticMarkerSize / 2f);

            ImageView staticMarker = new ImageView(this);
            staticMarker.setImageResource(R.drawable.pointer1);
            FrameLayout.LayoutParams markerLp = new FrameLayout.LayoutParams(staticMarkerSize, staticMarkerSize);
            staticMarker.setLayoutParams(markerLp);

            staticMarker.setX(left);
            staticMarker.setY(top);
            staticMarker.setElevation(2f);

            rootLayout.addView(staticMarker);
            Log.d(TAG, "Static marker added at: (" + left + ", " + top + ")");
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.appbar_items, menu);
        if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                Log.e(TAG, "onMenuOpened", e);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.import_new_map) {
            startActivity(new Intent(DisplayMap.this, BaseActivity.class));
            return true;
        } else if (itemId == R.id.settings) {
            showSwitchDialog();
            return true;
        } else if (itemId == R.id.exit) {
            confirmExit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSwitchDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.settings, null);
        Switch switchOption = dialogView.findViewById(R.id.switch1);
        switchOption.setChecked(settings.getTrailingFlag());
//        Switch potraitMode = dialogView.findViewById(R.id.potrait_flag_switch);
//        potraitMode.setChecked(settings.getPotraitFlag());
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.Settings))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    settings.setTrailing_flag(switchOption.isChecked());
                    trailing = settings.getTrailingFlag();
//                    String ScreenMode=potraitMode.isChecked()?"Potraint Mode":"Landscape Mode";
//                    if(settings.getPotraitFlag()!=potraitMode.isChecked()) {
//                        Toast.makeText(this, "Cannot Switch To" + ScreenMode, Toast.LENGTH_SHORT).show();
//                    }
//                    Toast.makeText(this, getString(R.string.Trailing_is)+" " + (trailing ? getString(R.string.ON) : getString(R.string.OFF)), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }


    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.All_progress_will_be_gone))
                .setPositiveButton(getString(R.string.Yes), (dialog, which) -> finishAffinity())
                .setNegativeButton(getString(R.string.No), null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (sensorTimer != null) sensorTimer.cancel();
        confirmExit();
    }

    private void startSensorCalibration() {
        // Reset UI
        circleProgress.setProgress(0);
        timerText.setText("5.00 s");

        sensorTimer = new CountDownTimer(5000, 10) { // tick every 10 ms for smooth progress
            @Override
            public void onTick(long millisUntilFinished) {
                int elapsed = (int) (5000 - millisUntilFinished);
                circleProgress.setProgress(elapsed); // ProgressBar max = 5000
                timerText.setText(String.format(Locale.getDefault(),
                        "%.2f s", millisUntilFinished / 1000f));
            }

            @Override
            public void onFinish() {
                circleProgress.setProgress(5000);
                timerText.setText("0.00 s");
                // Move to next screen
                sensorLayout.setVisibility(View.GONE);
                pathLayout.setVisibility(View.VISIBLE);
                startPathCalibration();
            }
        }.start();
    }

    private void startPathCalibration() {
        stepProgress.setMax(15);
        stepProgress.setProgress(0);
        stepCountText.setText("0");

        // If the step counter has already recorded some steps, immediately reflect that
        int steps = stepCounterManager.getSessionSteps();
        stepProgress.setProgress(steps);
        stepCountText.setText("" + steps);

        // If already done, flip screens immediately
        if (steps >= 15) {
            mapClickable = true;
            applyPathCorrectionMode = true;
            stepProgress.setVisibility(INVISIBLE);
            stepCountText.setText(getString(R.string.click_on_map_for_path_correction));
        }
    }

    void showMenuLayout() {

        pathLayout.setVisibility(View.GONE);
        menuLayout.setVisibility(View.VISIBLE);
        rootLayout.setZoomEnabled(true);
    }


}

//    private void showTimerDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        View dialogView = LayoutInflater.from(this).inflate(R.layout.calibration, null);
//        TextView dialogTimerText = dialogView.findViewById(R.id.dialogTimerText);
//        ImageView dialogHandImage = dialogView.findViewById(R.id.dialogHandImage);
//
//        Glide.with(this).asGif().load(R.drawable.gif_hand).into(dialogHandImage);
//        AlertDialog dialog = builder.setView(dialogView).setCancelable(false).create();
//
//        new CountDownTimer(5000, 100) {
//            @SuppressLint("SetTextI18n")
//            public void onTick(long millisUntilFinished) {
//                dialogTimerText.setText(
//                        String.format(
//                                Locale.getDefault(),
//                                "%s %.1f %s",
//                                getString(R.string.Please_Move_your_Device_for),
//                                millisUntilFinished / 1000.0,
//                                getString(R.string.Seconds)
//                        )
//                );
//            }
//            public void onFinish() {
//                dialog.dismiss();
//                Toast.makeText(DisplayMap.this, getString(R.string.Calibration_Success), Toast.LENGTH_SHORT).show();
//                TextView mainTimerText = findViewById(R.id.timer);
//                mainTimerText.setText(String.format(Locale.getDefault(), "%s : %s X %s", getString(R.string.Scale), mapHeightStr, mapWidthStr));
//                mainTimerText.setVisibility(VISIBLE);
//            }
//        }.start();
//
//        dialog.show();
//    }