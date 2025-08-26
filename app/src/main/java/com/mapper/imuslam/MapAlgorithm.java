package com.mapper.imuslam;

import static android.app.Activity.RESULT_OK;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;
import androidx.fragment.app.Fragment;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MapAlgorithm extends Fragment {

    private static final String TAG = "MapAlgorithm";

    // Parameters passed as Strings
    private String mapHeightStr, mapWidthStr, imageUri, startXStr, startYStr, mapFolderPath;
    // Converted numeric parameters
    private float mapHeight, mapWidth, startX, startY;

    // Views from fragment XML
    private ConstraintLayout rootLayout; // This is defined in your XML
    private ImageView mapView;
    private TextView coordinatesText;

    // Dynamic pointer view (the moving marker)
    private View pointerView;
    private int pointerSize;
    private static final int POINTER_SIZE_DP = 20;

    // AR Fragment and coordinates
    private ArFragment arFragment;
    private float lastMapX, lastMapY;
    public boolean trailing = true;

    // Camera launcher and folder for photos
    private ActivityResultLauncher<Intent> cameraLauncher;
    private File mapFolder;

    // Our custom view for drawing the trailing line
    private TrailingLineView trailingLineView;

    // New: Floating overlay container (added to decor view)
    private FrameLayout overlayContainer;
    private float smoothFactor = 0.9f; // Closer to 1 = more smoothing
    private float smoothedX = 0, smoothedY = 0;

    Pose initialPose;
    Anchor originAnchor;

    public MapAlgorithm() {
        // Required empty constructor.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            imageUri = getArguments().getString("imageUri");
            mapHeightStr = getArguments().getString("mapHeight");
            mapWidthStr = getArguments().getString("mapWidth");
            startXStr = getArguments().getString("startX");
            startYStr = getArguments().getString("startY");
            mapFolderPath = getArguments().getString("mapFolderPath"); // optional
            try {
                mapHeight = Float.parseFloat(mapHeightStr);
                mapWidth = Float.parseFloat(mapWidthStr);
                startX = Float.parseFloat(startXStr);
                startY = Float.parseFloat(startYStr);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Invalid map parameters", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
        lastMapX = startX;
        lastMapY = startY;

        // Initialize or create the map folder.
        if (mapFolderPath != null) {
            mapFolder = new File(mapFolderPath);
        } else {
            File parent = getContext().getExternalFilesDir("SLAM_MAPS");
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String folderName = "SLAM_Map_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date());
            mapFolder = new File(parent, folderName);
            if (!mapFolder.exists()) {
                mapFolder.mkdirs();
            }
        }
        // In onCreateView or initialization:

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate your fragment XML as usual.
        View view = inflater.inflate(R.layout.fragment_map_algorithm, container, false);

        // Get references from XML.
        rootLayout = view.findViewById(R.id.root_layout);
        if (rootLayout == null) {
            Log.e(TAG, "root_layout not found in XML.");
            return view;
        }
        mapView = view.findViewById(R.id.mapView);
        if (imageUri != null) {
            try {
                Uri uri = Uri.parse(imageUri);
                InputStream is = getContext().getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                mapView.setImageBitmap(bitmap);
                mapView.setVisibility(VISIBLE);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Failed to load map image", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
        coordinatesText = view.findViewById(R.id.coordinates);

        // Create pointer view (dynamic marker)
        pointerSize = (int) (POINTER_SIZE_DP * getResources().getDisplayMetrics().density + 0.5f);
        pointerView = new View(getContext());
        LayoutParams pointerLp = new LayoutParams(pointerSize, pointerSize);
        pointerLp.leftToLeft = LayoutParams.PARENT_ID;
        pointerLp.topToTop = LayoutParams.PARENT_ID;
        pointerView.setLayoutParams(pointerLp);
        pointerView.setBackgroundResource(R.drawable.pointer);
        pointerView.setVisibility(View.INVISIBLE);
        // (Do NOT add pointerView to rootLayout now)

        // Setup AR Fragment.
        if (view.findViewById(R.id.ar_fragment_container) != null) {
            if (savedInstanceState == null) {
                arFragment = new ArFragment();
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.ar_fragment_container, arFragment)
                        .commitAllowingStateLoss();
            } else {
                arFragment = (ArFragment) getChildFragmentManager().findFragmentById(R.id.ar_fragment_container);
            }
            if (arFragment != null) {
                arFragment.getViewLifecycleOwnerLiveData().observe(getViewLifecycleOwner(), lifecycleOwner -> {
                    if (lifecycleOwner != null && arFragment.getArSceneView() != null) {
                        if (arFragment.getPlaneDiscoveryController() != null) {
                            arFragment.getPlaneDiscoveryController().hide();
                            arFragment.getPlaneDiscoveryController().setInstructionView(null);
                        }
                        Objects.requireNonNull(arFragment.getArSceneView().getSession()).configure(
                                new Config(arFragment.getArSceneView().getSession()).setInstantPlacementMode(
                                        Config.InstantPlacementMode.LOCAL_Y_UP
                                )
                        );

                        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

                    } else {
                        Log.e(TAG, "AR scene not ready.");
                    }
                });
            } else {
                Log.e(TAG, "ARFragment is null after transaction.");
            }
        } else {
            Log.e(TAG, "AR container not found in XML.");
        }

        // Instead of adding overlays to rootLayout (which is in a wrap_content LinearLayout),
        // we create a floating overlay container and add it to the activity's content view.
        // Use mapView's global layout to know its dimensions and screen position.
        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int width = mapView.getWidth();
                int height = mapView.getHeight();
                // Get mapView's location on screen.
                int[] loc = new int[2];
                mapView.getLocationOnScreen(loc);

                // Create an overlay container with fixed dimensions.
                overlayContainer = new FrameLayout(getContext());
                FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(width, height);
                overlayContainer.setLayoutParams(overlayLp);
                // Position the container at the same screen coordinates as mapView.
                overlayContainer.setX(loc[0]);
                overlayContainer.setY(loc[1]);

                // Create the trailing overlay.
                trailingLineView = new TrailingLineView(getContext());
                FrameLayout.LayoutParams trailLp = new FrameLayout.LayoutParams(width, height);
                trailingLineView.setLayoutParams(trailLp);
                // Add trailing overlay and pointer into overlayContainer.
                overlayContainer.addView(trailingLineView);
                // Set pointerView layout parameters for the overlay.
                FrameLayout.LayoutParams pointerOverlayLp = new FrameLayout.LayoutParams(pointerSize, pointerSize);
                pointerView.setLayoutParams(pointerOverlayLp);
                overlayContainer.addView(pointerView);

                // Add the overlay container to the activity's content view.
                // (Using android.R.id.content is generally more reliable than getDecorView())
                ViewGroup contentView = (ViewGroup) getActivity().findViewById(android.R.id.content);
                contentView.addView(overlayContainer);
                // Ensure pointer is above the trailing overlay.
                pointerView.bringToFront();
            }
        });

        // Setup camera launcher.
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleCameraResult(result.getData());
                    }
                }
        );

        // Button listeners (unchanged)
        Button clickPhotoButton = view.findViewById(R.id.clickPhoto);
        if (clickPhotoButton != null) {
            clickPhotoButton.setOnClickListener(v -> {
                addStaticMarker();
                openCamera();
            });
        } else {
            Log.e(TAG, "clickPhoto button not found.");
        }
        Button endMapButton = view.findViewById(R.id.endMap);
        if (endMapButton != null) {
            endMapButton.setOnClickListener(v -> {
                Toast.makeText(getContext(), "Ending map session...", Toast.LENGTH_SHORT).show();
                Bitmap mergedMapBitmap = captureViewBitmap(mapView);
                if (mergedMapBitmap != null) {
                    String timestampStr = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    File mergedFile = new File(mapFolder, "mapPhoto_" + timestampStr + ".jpg");
                    try {
                        FileOutputStream fos = new FileOutputStream(mergedFile);
                        mergedMapBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.flush();
                        fos.close();
                        Toast.makeText(getContext(), "Merged map saved: " + mergedFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Merged map saved at: " + mergedFile.getAbsolutePath());
                    } catch (IOException e) {
                        Toast.makeText(getContext(), "Error saving merged map: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error saving merged map");
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to capture merged map", Toast.LENGTH_SHORT).show();
                }
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.container, new SavedMap())
                        .addToBackStack(null)
                        .commit();
            });
        } else {
            Log.e(TAG, "endMap button not found.");
        }
        initialPose = Pose.makeTranslation(0, 0, 0); // origin
        originAnchor = arFragment.getArSceneView().getSession().createAnchor(initialPose);
        return view;
    }

    private Bitmap captureViewBitmap(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new android.graphics.Canvas(bitmap));
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error capturing view bitmap");
            return null;
        }
    }

    private void onUpdateFrame(FrameTime frameTime) {
        if (arFragment == null || arFragment.getArSceneView() == null) {
            Log.w(TAG, "ARFragment or its scene view is null. Skipping update.");
            return;
        }
        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) {
            Log.w(TAG, "AR frame is null.");
            return;
        }
        Pose cameraPose = frame.getCamera().getPose();
        Pose anchorPose = originAnchor.getPose();
        Pose relativePose = anchorPose.inverse().compose(cameraPose);
        float displacementX = relativePose.tx();
        float displacementY = relativePose.tz();
//
//        Pose cameraPose = frame.getCamera().getPose();
//        float displacementX = cameraPose.tx();
//        float displacementY = cameraPose.tz();
        smoothedX = smoothFactor * smoothedX + (1 - smoothFactor) * displacementX;
        smoothedY = smoothFactor * smoothedY + (1 - smoothFactor) * displacementY;

        float currentMapX = startX + smoothedX;
        float currentMapY = startY - smoothedY;

//        float currentMapX = startX + displacementX;
//        float currentMapY = startY - displacementY;
        lastMapX = currentMapX;
        lastMapY = currentMapY;
        String coords = String.format(Locale.getDefault(), "X: %.2f, Y: %.2f", currentMapX, currentMapY);
        coordinatesText.setText(coords);

        int containerWidth = mapView.getWidth();
        int containerHeight = mapView.getHeight();
        if (containerWidth == 0 || containerHeight == 0) {
            Log.w(TAG, "MapView dimensions not ready.");
            return;
        }
        float pixelX = (currentMapX / mapWidth) * containerWidth;
        float pixelY = containerHeight - ((currentMapY / mapHeight) * containerHeight);
        float adjustedX = pixelX - (pointerSize / 2f);
        float adjustedY = pixelY - (pointerSize / 2f);
        // Update pointer position in the overlay.
        pointerView.post(() -> {
            pointerView.setX(adjustedX);
            pointerView.setY(adjustedY);
            pointerView.setVisibility(VISIBLE);
        });
        Log.d(TAG, "Pointer updated: X=" + adjustedX + ", Y=" + adjustedY);

        // Update trailing overlay if enabled.
        if (trailing && trailingLineView != null) {
            float centerX = adjustedX + pointerSize / 2f;
            float centerY = adjustedY + pointerSize / 2f;
            trailingLineView.addPoint(centerX, centerY);
        }
    }

    private void openCamera() {
        Log.d(TAG, "openCamera() called.");
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getContext().getPackageManager()) != null) {
            cameraLauncher.launch(cameraIntent);
            Log.d(TAG, "Camera intent launched.");
        } else {
            Toast.makeText(getContext(), "No camera app found", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No camera app available.");
        }
    }

    private void handleCameraResult(Intent data) {
        if (data.getExtras() != null) {
            Bitmap cameraPhoto = (Bitmap) data.getExtras().get("data");
            if (cameraPhoto != null) {
                savePhoto(cameraPhoto);
            } else {
                Toast.makeText(getContext(), "Failed to capture image", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Camera photo is null.");
            }
        }
    }

    private void savePhoto(Bitmap cameraPhoto) {
        try {
            if (mapFolder != null && !mapFolder.exists()) {
                mapFolder.mkdirs();
            }
            String timestampStr = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date());
            File file = new File(mapFolder, "IMG_" + timestampStr + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            cameraPhoto.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            Toast.makeText(getContext(), "Photo Saved: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Photo saved at: " + file.getAbsolutePath());
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error saving photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error saving photo");
        }
    }

    // Adds a static marker at the current ARCore coordinates.
    private void addStaticMarker() {
        // Instead of adding to root_layout, add static markers to the overlay container.
        if (overlayContainer == null) {
            Log.e(TAG, "Overlay container not initialized.");
            return;
        }
        int containerWidth = mapView.getWidth();
        int containerHeight = mapView.getHeight();
        if (containerWidth == 0 || containerHeight == 0) {
            Log.e(TAG, "MapView dimensions not available for static marker placement.");
            return;
        }
        float pixelX = (lastMapX / mapWidth) * containerWidth;
        float pixelY = containerHeight - ((lastMapY / mapHeight) * containerHeight);
        float adjustedX = pixelX - (pointerSize / 2f);
        float adjustedY = pixelY - (pointerSize / 2f);
        float centerX = adjustedX + pointerSize / 2f;
        float centerY = adjustedY + pointerSize / 2f;
        int staticMarkerSize = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
        float left = centerX - staticMarkerSize / 2f;
        float top = centerY - staticMarkerSize / 2f;

        ImageView staticMarker = new ImageView(getContext());
        staticMarker.setImageResource(R.drawable.pointer1);
        LayoutParams markerLp = new LayoutParams(staticMarkerSize, staticMarkerSize);
        staticMarker.setLayoutParams(markerLp);
        staticMarker.setX(left);
        staticMarker.setY(top);
        staticMarker.setOnClickListener(v ->
                Toast.makeText(getContext(), "Static marker clicked", Toast.LENGTH_SHORT).show()
        );
        overlayContainer.addView(staticMarker);
        Log.d(TAG, "Static marker added at: (" + left + ", " + top + ")");
    }
}
