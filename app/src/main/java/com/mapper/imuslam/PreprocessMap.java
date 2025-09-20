package com.mapper.imuslam;

import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.Scanner;

public class PreprocessMap extends Fragment {

    // Keys for new map creation.
    private static final String ARG_PARAM1 = "imageUri";
    private static final String ARG_PARAM2 = "mapHeight";
    private static final String ARG_PARAM3 = "mapWidth";

    private String mapHeight, mapWidth, imageUri;
    private TextView mapCoordinates;
    private ImageView previewImage;
    Settings settings;
    private Button StartButton;
    private View pointerView;
    private FrameLayout mapContainer;
    private float touchX, touchY;
    private int mapH, mapW;
    private String startX, startY;
//    CardView bgcardView;

    public PreprocessMap() {
        // Required empty public constructor.
    }

    public static PreprocessMap newInstance(String imageUri, String mapHeight, String mapWidth) {
        PreprocessMap fragment = new PreprocessMap();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, imageUri);
        args.putString(ARG_PARAM2, mapHeight);
        args.putString(ARG_PARAM3, mapWidth);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            // If resuming a saved map, look for the folder path key "mapFolderPath".
            if (args.containsKey("mapFolderPath")) {
                String mapFolderPath = args.getString("mapFolderPath");
                File metricsFile = new File(mapFolderPath, "metrics.json");
                if (metricsFile.exists()) {
                    try {
                        String json = new Scanner(metricsFile).useDelimiter("\\A").next();
                        JSONObject metrics = new JSONObject(json);
                        // Retrieve dimensions and the image URI.
                        mapHeight = String.valueOf(metrics.getDouble("h"));
                        mapWidth = String.valueOf(metrics.getDouble("w"));
                        imageUri = metrics.optString("imageUri", "");
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Error loading saved map data", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            } else {
                // Otherwise, use parameters passed for new map creation.
                imageUri = args.getString(ARG_PARAM1);
                mapHeight = args.getString(ARG_PARAM2);
                mapWidth = args.getString(ARG_PARAM3);
            }
        }
        // Convert dimensions to integers (using double parsing to handle strings like "480.0")
        try {
            double heightDouble = Double.parseDouble(mapHeight);
            double widthDouble = Double.parseDouble(mapWidth);
            mapH = (int) heightDouble;
            mapW = (int) widthDouble;
        } catch (Exception e) {
            Toast.makeText(getContext(), "Invalid Map Dimensions", Toast.LENGTH_SHORT).show();
            mapH = -1;
            mapW = -1;
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preprocess_map, container, false);

        previewImage = view.findViewById(R.id.mapImageView);
        StartButton = view.findViewById(R.id.startButton);
        mapCoordinates = view.findViewById(R.id.map_coordinates);
//        bgcardView = view.findViewById(R.id.bgcardView);
        Log.d("ImageUri", imageUri);
        Log.d("MapHeight", mapHeight);
        Log.d("MapWidth", mapWidth);

        // Calculate display dimensions based on map dimensions.
        int displayHeight = getResources().getDisplayMetrics().heightPixels - 150;
        int displayWidth = getResources().getDisplayMetrics().widthPixels - 150;
        int layoutWidth, layoutHeight;
        boolean isLandscape = displayWidth > displayHeight;
        int a = (int) (100 * this.getResources().getDisplayMetrics().density);
        if (isLandscape) {
            displayHeight = displayHeight - a;
        }
//        if (mapW > mapH) {
//            if (isLandscape) {
//                // fit to height in landscape
//                layoutHeight = displayHeight;
//                layoutWidth = (int) ((displayHeight * mapW) / (float) mapH);
//            } else {
//                // fit to width in portrait
//                layoutWidth = displayWidth;
//                layoutHeight = (int) ((displayWidth * mapH) / (float) mapW);
//            }
//        }
//        else {
//            if (isLandscape) {
//                // fit to height in landscape
//                layoutHeight = displayHeight;
//                layoutWidth = (int) ((displayHeight * mapW) / (float) mapH);
//            } else {
//                // fit to width in portrait
//                layoutWidth = displayWidth;
//                layoutHeight = (int) ((displayWidth * mapH) / (float) mapW);
//            }
//        }
        float mapAspect = (float) mapW / mapH;
        float displayAspect = (float) displayWidth / displayHeight;

        if (mapAspect > displayAspect) {
            // Map is proportionally wider than the screen area → fit width
            layoutWidth = displayWidth;
            layoutHeight = (int) (displayWidth / mapAspect);
        } else {
            // Map is proportionally taller → fit height
            layoutHeight = displayHeight;
            layoutWidth = (int) (displayHeight * mapAspect);
        }

        layoutWidth = Math.min(layoutWidth, displayWidth);
        layoutHeight = Math.min(layoutHeight, displayWidth);
//        ViewGroup.LayoutParams cardParams = bgcardView.getLayoutParams();
//        cardParams.width = layoutWidth + 150;//(int)(8 * getResources().getDisplayMetrics().density);
//        cardParams.height = layoutHeight + 150;//(int)(8 * getResources().getDisplayMetrics().density);
//        bgcardView.setLayoutParams(cardParams);
        // Set up container for the map image.
        ViewGroup parent = (ViewGroup) previewImage.getParent();
        if (parent instanceof FrameLayout) {
            mapContainer = (FrameLayout) parent;
        } else {
            int index = parent.indexOfChild(previewImage);
            parent.removeView(previewImage);
            mapContainer = new FrameLayout(getContext());
            mapContainer.setLayoutParams(previewImage.getLayoutParams());
            parent.addView(mapContainer, index);
            mapContainer.addView(previewImage);
        }

        // Create the pointer view.
        pointerView = new View(getContext());
        int pointerSize = 20; // in dp.
        float density = getResources().getDisplayMetrics().density;
        int pointerSizePx = (int) (pointerSize * density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(pointerSizePx, pointerSizePx);
        params.gravity = Gravity.TOP | Gravity.START;
        pointerView.setLayoutParams(params);
        pointerView.setBackgroundResource(R.drawable.pointer1);
        pointerView.setVisibility(View.INVISIBLE);
        mapContainer.addView(pointerView);

        // Load the image.
        try {
            if (imageUri != null && !imageUri.isEmpty()) {
                // Check if imageUri refers to a real file.
                File imageFile = new File(imageUri);
                if (imageFile.exists()) {
                    // Create a file URI.
                    Uri fileUri = Uri.fromFile(imageFile);
                    previewImage.setImageURI(fileUri);
                } else {
                    // Otherwise, try parsing the URI.
                    previewImage.setImageURI(Uri.parse(imageUri));
                }
                previewImage.getLayoutParams().height = layoutHeight;
                previewImage.getLayoutParams().width = layoutWidth;
            } else {
                Toast.makeText(getContext(), "No image found to resume", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("PreprocessMap", "Error loading image");
            Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
        }

        Log.d("ImageUri", imageUri != null ? imageUri : "null");
        Log.d("MapHeight", mapHeight != null ? mapHeight : "null");
        Log.d("MapWidth", mapWidth != null ? mapWidth : "null");

        // Add touch listener to capture a starting point.
        previewImage.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                    event.getAction() == MotionEvent.ACTION_MOVE) {
                float viewWidth1  = previewImage.getWidth();
                float viewHeight1 = previewImage.getHeight();

                float imgWidth   = previewImage.getDrawable().getIntrinsicWidth();
                float imgHeight  = previewImage.getDrawable().getIntrinsicHeight();

                // 2. compute scale used to fit the image (FIT_CENTER-like)
                float scale = Math.min(viewWidth1 / imgWidth, viewHeight1 / imgHeight);

                // 3. find the offset because image is centered in the view
                float dx = (viewWidth1  - imgWidth  * scale) / 2f;
                float dy = (viewHeight1 - imgHeight * scale) / 2f;

                // 4. convert touch to bitmap-relative coordinates
                float touchX = (event.getX() - dx) / scale;
                float touchY = (event.getY() - dy) / scale;

                // clamp to image bounds if needed
                touchX = Math.max(0, Math.min(touchX, imgWidth));
                touchY = Math.max(0, Math.min(touchY, imgHeight));

                // now use touchX, touchY for your pointer
                Log.d("Pointer", "X:" + touchX + "  Y:" + touchY);

                BitmapDrawable drawable = (BitmapDrawable) previewImage.getDrawable();
                if (drawable == null) return false;
                int viewWidth = previewImage.getWidth();
                int viewHeight = previewImage.getHeight();
                float rawX = event.getX();
                float rawY = event.getY();
                rawX = Math.max(0, Math.min(rawX, viewWidth));
                rawY = Math.max(0, Math.min(rawY, viewHeight));

                calculateCoordinatesRelativeToImage(rawX, rawY);
                updatePointerPosition(rawX, rawY);
                Log.d("RAW", "X:" + rawX + "  Y:" + rawY);
                return true;
            }
            return false;
        });

        // On Start button click, launch DisplayMap with the collected data.
        StartButton.setOnClickListener(v -> {
            if (pointerView.getVisibility() == VISIBLE) {
                Toast.makeText(getContext(), "Starting, Please Wait...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getContext(), DisplayMap.class);
                intent.putExtra("imageUri", imageUri);
                intent.putExtra("mapHeight", mapHeight);
                intent.putExtra("mapWidth", mapWidth);
                intent.putExtra("startX", startX);
                intent.putExtra("startY", startY);
                // Pass the folder path if it exists (for resuming maps).
//                intent.putExtra("mapFolderPath", getArguments() != null ? getArguments().getString("mapFolderPath") : "");
                intent.putExtra("mapFolderPath", "");
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Please select a starting point on the map", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    /**
     * Calculate coordinates relative to the image, taking scaling into account.
     */
    @SuppressLint("SetTextI18n")
    private void calculateCoordinatesRelativeToImage(float rawX, float rawY) {
        int viewWidth = previewImage.getWidth();
        int viewHeight = previewImage.getHeight();
        BitmapDrawable drawable = (BitmapDrawable) previewImage.getDrawable();
        if (drawable == null) return;

        int imageWidth = drawable.getBitmap().getWidth();
        int imageHeight = drawable.getBitmap().getHeight();

        float scaleX = (float) imageWidth / viewWidth;
        float scaleY = (float) imageHeight / viewHeight;
        touchX = rawX * scaleX;
        touchY = rawY * scaleY;

        StartButton.setVisibility(VISIBLE);
        float realX = (touchX / imageWidth) * mapW;
        float realY = ((imageHeight - touchY) / imageHeight) * mapH;

        mapCoordinates.setVisibility(VISIBLE);
        StartButton.setVisibility(VISIBLE);
        startX = String.valueOf(realX);
        startY = String.valueOf(realY);
        mapCoordinates.setText("X: " + startX + "  Y: " + startY);
    }

    /**
     * Update the pointer view's position.
     */
    private void updatePointerPosition(float rawX, float rawY) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pointerView.getLayoutParams();
        params.leftMargin = (int) (rawX - (pointerView.getWidth() / 2));
        params.topMargin = (int) (rawY - (pointerView.getHeight() / 2));
        pointerView.setLayoutParams(params);
        pointerView.setVisibility(VISIBLE);
    }
}
