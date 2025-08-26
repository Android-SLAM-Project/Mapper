package com.mapper.imuslam;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SingleMap extends Fragment {

    private static final String TAG = "SingleMap";
    private String folderPath; // Passed via fragment arguments.
    private ZoomImageView zoomImageView;
    private int MapHeight = 0, MapWidth = 0;
    private TextView metricsTextView; // For displaying steps and distance.

    public SingleMap() {
        // Required empty public constructor.
    }

    public static SingleMap newInstance(String fileUri) {
        SingleMap fragment = new SingleMap();
        Bundle args = new Bundle();
        args.putString("fileUri", fileUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retrieve folder path from arguments.
        if (getArguments() != null) {
            folderPath = getArguments().getString("fileUri");
        }
        Log.d(TAG, "Folder path received: " + folderPath);
    }

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, android.view.ViewGroup container,
                             Bundle savedInstanceState) {
        // Create a FrameLayout to hold both the ZoomImageView and a metrics TextView.
        FrameLayout root = new FrameLayout(getContext());

        // Create and add the ZoomImageView.
        zoomImageView = new ZoomImageView(getContext());
        FrameLayout.LayoutParams zoomLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(zoomImageView, zoomLp);

        // Create and add the metrics TextView.
        metricsTextView = new TextView(getContext());
        metricsTextView.setBackgroundColor(0xAA000000); // semi-transparent black background.
        metricsTextView.setTextColor(0xFFFFFFFF);
        metricsTextView.setPadding(16, 16, 16, 16);
        // Place the TextView at top-left.
        FrameLayout.LayoutParams metricsLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        metricsLp.leftMargin = 16;
        metricsLp.topMargin = 16;
        root.addView(metricsTextView, metricsLp);

        return root;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (folderPath == null) {
            Toast.makeText(getContext(), "No map folder provided", Toast.LENGTH_SHORT).show();
            return;
        }
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            Toast.makeText(getContext(), "Map folder not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // List and log all files in the folder for diagnostics.
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            Toast.makeText(getContext(), "No files in folder", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Files in folder (" + folder.getAbsolutePath() + "):");
        for (File f : files) {
            Log.d(TAG, "   " + f.getName());
        }

        // Find merged map image.
        File mergedMapFile = null;
        for (File f : files) {
            String name = f.getName().toLowerCase(Locale.getDefault());
            if (name.contains("mapphoto") && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
                mergedMapFile = f;
                break;
            }
        }
        if (mergedMapFile != null) {
            Bitmap bmp = BitmapFactory.decodeFile(mergedMapFile.getAbsolutePath());
            if (bmp != null) {
                zoomImageView.setBitmap(bmp);
                Log.d(TAG, "Merged image loaded from: " + mergedMapFile.getAbsolutePath());
            } else {
//                Toast.makeText(getContext(), "Failed to decode merged map image", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Bitmap decoding returned null for file: " + mergedMapFile.getAbsolutePath());
            }
        } else {
//            Toast.makeText(getContext(), "No Data Saved in Map", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Merged map image file not found in folder.");
            return;
        }

        // Read metrics from metrics.json (if exists) and update the metrics TextView.
        File metricsFile = new File(folder, "metrics.json");
        if (metricsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(metricsFile);
                 java.util.Scanner scanner = new java.util.Scanner(fis).useDelimiter("\\A")) {
                String jsonStr = scanner.hasNext() ? scanner.next() : "";
                if (!jsonStr.isEmpty()) {
                    JSONObject metricsObj = new JSONObject(jsonStr);
                    int steps = metricsObj.getInt("steps");
                    double distance = metricsObj.getDouble("distance");
                    int h = metricsObj.getInt("h");
                    int w = metricsObj.getInt("w");
                    SpannableString boldText = new SpannableString(
                            String.format(Locale.getDefault(), "Scale: %d X %d, Steps: %d, Distance: %.2f m", h, w, steps, distance)
                    );
                    boldText.setSpan(new StyleSpan(Typeface.BOLD), 0, boldText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    metricsTextView.setText(boldText);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading metrics.json");
            }
        } else {
            metricsTextView.setText("Metrics not available");
        }

        // Load photo metadata from photos.json.
        JSONArray photosArray = null;
        File jsonFile = new File(folder, "photos.json");
        if (jsonFile.exists()) {
            try (FileInputStream fis = new FileInputStream(jsonFile);
                 java.util.Scanner scanner = new java.util.Scanner(fis).useDelimiter("\\A")) {
                String content = scanner.hasNext() ? scanner.next() : "";
                photosArray = new JSONArray(content);
                Log.d(TAG, "photos.json entries: " + photosArray.length());
            } catch (Exception e) {
                Log.e(TAG, "Error reading photos.json");
            }
        } else {
            Log.e(TAG, "photos.json not found");
        }
        // Process photo metadata and create photo pointers.
        if (photosArray != null) {
            List<PhotoPointer> pointers = new ArrayList<>();
            // For each entry, convert raw ARCore coordinates to merged image pixel coordinates.
            for (int i = 0; i < photosArray.length(); i++) {
                try {
                    JSONObject obj = photosArray.getJSONObject(i);
                    float arMapX = (float) obj.getDouble("mapX");
                    float arMapY = (float) obj.getDouble("mapY");
                    String cameraPhotoName = obj.getString("cameraPhoto");
                    float origMapWidth = (float) obj.getDouble("mapWidth");
                    float origMapHeight = (float) obj.getDouble("mapHeight");
                    MapHeight = (int) origMapHeight;
                    MapWidth = (int) origMapWidth;
                    Bitmap bmp = zoomImageView.getBitmap();
                    if (bmp == null) continue;
                    float pointerX = (arMapX / origMapWidth) * bmp.getWidth();
                    float pointerY = bmp.getHeight() - ((arMapY / origMapHeight) * bmp.getHeight());
                    pointers.add(new PhotoPointer(pointerX, pointerY, cameraPhotoName,
                            arMapX, arMapY, origMapWidth, origMapHeight));
                } catch (Exception e) {
                    Log.e(TAG, "Error processing photo metadata");
                }
            }
            zoomImageView.setPhotoPointers(pointers, folder);
        }
    }

    /**
     * Helper class representing a photo pointer (hotspot) that also stores raw ARCore coordinates
     * and the original map dimensions.
     */
    public static class PhotoPointer {
        public float x, y; // Pixel coordinates for display.
        public String photoFileName;
        public float arMapX, arMapY; // Raw ARCore coordinates.
        public float origMapWidth, origMapHeight; // Original map dimensions.

        public PhotoPointer(float x, float y, String photoFileName,
                            float arMapX, float arMapY, float origMapWidth, float origMapHeight) {
            this.x = x;
            this.y = y;
            this.photoFileName = photoFileName;
            this.arMapX = arMapX;
            this.arMapY = arMapY;
            this.origMapWidth = origMapWidth;
            this.origMapHeight = origMapHeight;
        }
    }

    /**
     * Custom view that supports pinch zoom, pan, and displays photo pointers.
     */
    public static class ZoomImageView extends View {

        private Bitmap displayBitmap;
        private final Matrix matrix = new Matrix();
        private float scaleFactor = 1.0f;
        private float posX = 0, posY = 0;
        private float minScaleFactor = 1.0f;
        private ScaleGestureDetector scaleDetector;
        private GestureDetector gestureDetector;
        private float lastTouchX, lastTouchY;

        // List of photo pointers in image coordinate space.
        private List<PhotoPointer> pointers = new ArrayList<>();
        // Folder to load photo files.
        private File folder;
        // Original marker bitmap.
        private Bitmap markerBitmap;
        // Tap threshold in screen pixels.
        private final float TAP_THRESHOLD = 40 * getResources().getDisplayMetrics().density;

        public ZoomImageView(Context context) {
            super(context);
            init(context);
        }

        public ZoomImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init(context);
        }

        private void init(Context context) {
            scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
            gestureDetector = new GestureDetector(context, new GestureListener());
            setFocusable(true);
            setClickable(true);
            markerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pointer1);
        }

        /**
         * Set the bitmap to display.
         */
        public void setBitmap(Bitmap bitmap) {
            if (bitmap == null) return;
            displayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            post(() -> {
                fitImageToView();
                invalidate();
            });
        }

        public Bitmap getBitmap() {
            return displayBitmap;
        }

        /**
         * Fit the image to the view dimensions.
         */
        private void fitImageToView() {
            if (displayBitmap == null || getWidth() == 0 || getHeight() == 0) return;
            float viewW = getWidth();
            float viewH = getHeight();
            float imgW = displayBitmap.getWidth();
            float imgH = displayBitmap.getHeight();
            float scaleX = viewW / imgW;
            float scaleY = viewH / imgH;
            minScaleFactor = Math.min(scaleX, scaleY);
            scaleFactor = minScaleFactor;
            posX = (viewW - imgW * scaleFactor) / 2;
            posY = (viewH - imgH * scaleFactor) / 2;
            updateMatrix();
        }

        /**
         * Update the transformation matrix.
         */
        private void updateMatrix() {
            matrix.reset();
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(posX, posY);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (displayBitmap != null && !displayBitmap.isRecycled()) {
                updateMatrix();
                canvas.drawBitmap(displayBitmap, matrix, null);
            }
            if (displayBitmap != null) {
                float imageWidth = displayBitmap.getWidth() * scaleFactor;
                float imageHeight = displayBitmap.getHeight() * scaleFactor;
                @SuppressLint("DrawAllocation") RectF imageRect = new RectF(posX, posY, posX + imageWidth, posY + imageHeight);
                for (PhotoPointer pointer : pointers) {
                    float[] pt = { pointer.x, pointer.y };
                    matrix.mapPoints(pt);
                    if (!imageRect.contains(pt[0], pt[1])) continue;
                    // Base marker size in pixels.
                    int BASE_MARKER_SIZE = 50;
                    int markerSize = (int) (BASE_MARKER_SIZE * scaleFactor);
                    @SuppressLint("DrawAllocation") RectF dest = new RectF(pt[0] - markerSize / 2f, pt[1] - markerSize / 2f,
                            pt[0] + markerSize / 2f, pt[1] + markerSize / 2f);
                    canvas.drawBitmap(markerBitmap, null, dest, null);
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            if (event.getPointerCount() == 1) {
                int action = event.getActionMasked();
                float damping = 0.7f;
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        posX += dx * damping;
                        posY += dy * damping;
                        constrainPanning();
                        invalidate();
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        handleTap(event.getX(), event.getY());
                        break;
                }
            }
            return true;
        }

        /**
         * Constrain panning so the image always covers the view.
         */
        private void constrainPanning() {
            if (displayBitmap == null) return;
            float viewW = getWidth();
            float viewH = getHeight();
            float imgW = displayBitmap.getWidth() * scaleFactor;
            float imgH = displayBitmap.getHeight() * scaleFactor;
            if (imgW < viewW) {
                posX = (viewW - imgW) / 2;
            } else {
                posX = Math.min(0, Math.max(posX, viewW - imgW));
            }
            if (imgH < viewH) {
                posY = (viewH - imgH) / 2;
            } else {
                posY = Math.min(0, Math.max(posY, viewH - imgH));
            }
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                float maxScaleFactor = 7.0f;
                scaleFactor = Math.max(minScaleFactor, Math.min(scaleFactor, maxScaleFactor));
                constrainPanning();
                invalidate();
                return true;
            }
        }

        private class GestureListener extends GestureDetector.SimpleOnGestureListener {
            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                posX -= distanceX;
                posY -= distanceY;
                constrainPanning();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                fitImageToView();
                invalidate();
                Toast.makeText(getContext(), "View reset", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }
        }

        public void setPhotoPointers(List<PhotoPointer> pointers, File folder) {
            if (pointers != null) {
                this.pointers = pointers;
            }
            this.folder = folder;
        }

        @SuppressLint("SetTextI18n")
        private void handleTap(float tapX, float tapY) {
            float[] values = new float[9];
            matrix.getValues(values);
            float currentScale = values[Matrix.MSCALE_X];
            float transX = values[Matrix.MTRANS_X];
            float transY = values[Matrix.MTRANS_Y];
            float imageX = (tapX - transX) / currentScale;
            float imageY = (tapY - transY) / currentScale;
            float threshold = TAP_THRESHOLD / currentScale;
            PhotoPointer nearest = null;
            float minDist = Float.MAX_VALUE;
            for (PhotoPointer pointer : pointers) {
                float dx = pointer.x - imageX;
                float dy = pointer.y - imageY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < threshold && dist < minDist) {
                    minDist = dist;
                    nearest = pointer;
                }
            }
            if (nearest != null && folder != null) {
                File photoFile = new File(folder, nearest.photoFileName);
                if (photoFile.exists()) {
                    Bitmap photoBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                    if (photoBitmap != null) {
                        LinearLayout layout = new LinearLayout(getContext());
                        layout.setOrientation(LinearLayout.VERTICAL);

                        AppCompatImageView imageView = new AppCompatImageView(getContext());
                        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 800);
                        imageView.setLayoutParams(imageParams);
                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        imageView.setImageBitmap(photoBitmap);
                        layout.addView(imageView);

                        TextView coordText = new TextView(getContext());
                        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        coordText.setLayoutParams(textParams);
                        coordText.setText("Map Coordinates: (" + nearest.arMapX + ", " + nearest.arMapY + ")\n" +
                                "Map Dimensions: (" + nearest.origMapWidth + ", " + nearest.origMapHeight + ")");
                        layout.addView(coordText);

                        new AlertDialog.Builder(getContext())
                                .setTitle("Captured Photo")
                                .setView(layout)
                                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                                .show();
                        return;
                    } else {
                        Toast.makeText(getContext(), "Failed to decode photo", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }
}
