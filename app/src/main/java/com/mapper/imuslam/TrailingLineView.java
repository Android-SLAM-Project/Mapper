package com.mapper.imuslam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;

public class TrailingLineView extends View {
    private Paint paint;
    private Path path;
    private Float startX = null;
    private Float startY = null;

    public TrailingLineView(Context context) {
        super(context);
        init();
    }

    public TrailingLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5); // adjust as needed
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        path = new Path();
    }

    // Adds a new point to the path.
    public void addPoint(float x, float y) {
        if (path.isEmpty()) {
            path.moveTo(x, y);
            startX = x;
            startY = y;
            Log.d("TrailingLineView", "First point added: (" + x + ", " + y + ")");
        } else {
            path.lineTo(x, y);
            Log.d("TrailingLineView", "Point added: (" + x + ", " + y + ")");
        }
        invalidate();
    }
    public void clear() {
        path.reset();
        startX = null;
        startY = null;
        invalidate();
    }
    public void clearExceptStart() {
        if (startX != null && startY != null) {
            path.reset();
            path.moveTo(startX, startY); // Move back to the original start point
        } else {
            path.reset();
        }
        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
    }
}

