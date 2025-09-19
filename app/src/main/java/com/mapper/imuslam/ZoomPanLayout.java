package com.mapper.imuslam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ZoomPanLayout extends FrameLayout {

    private float scaleFactor = 1.0f;
    private final float minScaleFactor = 1.0f;
    private final float maxScaleFactor = 7.0f;
    boolean zoomEnabled = true;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public interface OnMapTappedListener {
        void onMapTapped(float touchX, float touchY);
    }
    private OnMapTappedListener mapTappedListener;

    public interface OnZoomPanListener {
        void onZoomOrPan();
    }

    private OnZoomPanListener zoomPanListener;


    public ZoomPanLayout(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomPanLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnMapTappedListener(OnMapTappedListener listener) {
        this.mapTappedListener = listener;
    }

    public void setOnZoomPanListener(OnZoomPanListener listener) {
        this.zoomPanListener = listener;
    }
    public void setZoomEnabled(boolean enabled) {
        this.zoomEnabled = enabled;
    }

    /** Public getter */
    public boolean isZoomEnabled() {
        return zoomEnabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        boolean handled = scaleDetector.onTouchEvent(event);
        handled = gestureDetector.onTouchEvent(event) || handled;
        return handled || super.onTouchEvent(event);
    }

    public void resetZoom() {
        animate().scaleX(minScaleFactor).scaleY(minScaleFactor)
                .translationX(0).translationY(0)
                .setDuration(300)
                .start();
        scaleFactor = minScaleFactor;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!zoomEnabled) {
                // ignore zoom gestures completely
                return true;
            }
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(minScaleFactor, Math.min(scaleFactor, maxScaleFactor));
            setScaleX(scaleFactor);
            setScaleY(scaleFactor);

            if (zoomPanListener != null) {
                zoomPanListener.onZoomOrPan();
            }
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (!zoomEnabled) return false;
            setTranslationX(getTranslationX() - distanceX);
            setTranslationY(getTranslationY() - distanceY);

            if (zoomPanListener != null) {
                zoomPanListener.onZoomOrPan();
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            if (!zoomEnabled) {
                // block double-tap reset if zoom disabled
                return true;
            }
            resetZoom();
            Toast.makeText(getContext(), "View Reset", Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {

            if (mapTappedListener != null) {
                float touchX = (e.getX() - getTranslationX()) / getScaleX();
                float touchY = (e.getY() - getTranslationY()) / getScaleY();
                mapTappedListener.onMapTapped(touchX, touchY);
                return true;
            }
            return super.onSingleTapUp(e);
        }
    }
}