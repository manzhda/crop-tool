package com.tac.cropmodule3d;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * Created by kulik on 17.02.15.
 */
public class CroppingTread extends Thread implements View.OnTouchListener {
    private static final String TAG = CroppingTread.class.getSimpleName();
    private static final int[] DRAWING_LINES = new int[]{1, 3, 2, 0};
    private final SurfaceHolder mHolder;
    private final Paint red;
    private final Paint mTextPaint;
    private Bitmap mBitmapToCrop;
    // flag to hold game state
    private volatile boolean mRunning;
    private Configuration mConf;
    private Pin[] mPins;
    private volatile Pin mActivePin;
    private int mWidth;
    private int mHeight;
    private Object bitmapUseLock = new Object();
    private float mScale;

    public CroppingTread(SurfaceHolder holder, Configuration conf) {
        mConf = conf;
        mHolder = holder;
        red = new Paint();
        red.setColor(Color.RED);
        red.setStrokeWidth(3);
        red.setTextSize(24);
        red.setAntiAlias(true);

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(24);
        mTextPaint.setFakeBoldText(true);
        mTextPaint.setAntiAlias(true);
    }

    public void setRunning(boolean running) {
        this.mRunning = running;
    }

    @Override
    public void run() {
        while (mRunning) {
            if (mPins == null) {
                initDefaultPoints();
            }
            Pin[] pins = new Pin[4];
            int active;
            synchronized (mPins) {
                for (int i = 0; i < 4; i++) {
                    Pin pin = mPins[i];
                    pins[i] = new Pin(pin.x, pin.y, pin.radius);
                    if (pin == mActivePin) {
                        active = i;
                    }
                }
            }
            Canvas canvas = mHolder.lockCanvas();
            canvas.drawColor(Color.BLACK);
            synchronized (bitmapUseLock) {
                if (mBitmapToCrop != null) {
                    canvas.save();
                    canvas.scale(mScale, mScale );
                    canvas.drawBitmap(mBitmapToCrop, 0, 0, new Paint());
                    canvas.restore();
                }
            }
            if (mConf.pinDrawable == null) {
                for (int i = 0; i < 4; i++) {
                    drawCircle(canvas, pins[i].x, pins[i].y, i);
//                    canvas.drawRect(pins[i].touchRect, mTextPaint);
                }
            } else {
                for (int i = 0; i < 4; i++) {
//                    if (mPins ==  active && mConf.pinDrawable instanceof StateListDrawable) {
//                     mConf.pinDrawable.
//                    }
                }
            }
            int dsx = (int) (pins[0].x);
            int dsy = (int) (pins[0].y);
            for (int i : DRAWING_LINES) {
                canvas.drawLine(pins[i].x, pins[i].y, dsx, dsy, red);
                dsx = (int) (pins[i].x);
                dsy = (int) (pins[i].y);
            }
            mHolder.unlockCanvasAndPost(canvas);
            try {
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException e) {
            }
        }
    }

    private void drawCircle(Canvas canvas, float x, float y, int pos) {
        canvas.drawCircle(x, y, 40, red);
        canvas.drawText(String.valueOf(pos + 1), x, y, mTextPaint);
//        canvas.drawText("x:" + p.x + " y:" + p.y, p.x - p.radius * 2, p.y + p.radius * 2, red);
    }

    private void initDefaultPoints() {
        int width = getWidth();
        int height = getHeight();

        mPins = new Pin[4];
        mPins[0] = new Pin(width / 4, height / 4, 40);
        mPins[1] = new Pin(width * 3 / 4, height / 4, 40);
        mPins[2] = new Pin(width / 4, height * 3 / 4, 40);
        mPins[3] = new Pin(width * 3 / 4, height * 3 / 4, 40);
    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {

        final float x = event.getX();
        final float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                findTouchedPin(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                synchronized (this) {
                    if (mActivePin != null) {
                        int r = mActivePin.radius;
                        //don't go over view bounds
                        if (r + x > getWidth() ||
                                x - r < 0 ||
                                y - r < 0 ||
                                y + r > getHeight()) {
                            return true;
                        }
                        mActivePin.x = (int) x;
                        mActivePin.y = (int) y;
//                        mActivePin.updateTouch();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                mActivePin = null;
        }
        synchronized (this) {
            notify();
        }
        return true;
    }

    public boolean findTouchedPin(float x, float y) {
        boolean isTouched = false;
        mActivePin = null;
        for (Pin p : mPins) {
//            isTouched = p.touchRect.contains((int) x, (int) y);
            isTouched = (Math.pow(x - p.x, 2) + Math.pow(p.y - y, 2)) < Math.pow(p.radius, 2);
            if (isTouched) {
                Log.d(TAG, "catched");
                mActivePin = p;
                break;
            }
        }
        return isTouched;
    }

    public void setDimentions(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public Bitmap getBitmapToCrop() {
        return mBitmapToCrop;
    }

    public void setBitmapToCrop(Bitmap bitmapToCrop) {
        synchronized (bitmapUseLock) {
            if (mBitmapToCrop != null) {
                mBitmapToCrop.recycle();
            }
            mBitmapToCrop = bitmapToCrop;
            mScale = Math.min((float) mWidth / mBitmapToCrop.getWidth(), (float) mHeight / mBitmapToCrop.getHeight());
        }
    }

    public Bitmap crop() {
        Mat imageMat = new Mat();
        Utils.bitmapToMat(mBitmapToCrop, imageMat);
        Pin[] pins = new Pin[4];
        for (int i = 0; i < 4; i++) {
            Pin pin = mPins[i];
            pins[i] = new Pin((int) (pin.x / mScale), (int) (pin.y / mScale), 0);
        }
        Bitmap output = TransformationEngine.get().initArea(pins[0].point(), pins[1].point(), pins[2].point(), pins[3].point())
                .transform(imageMat);
        return output;
    }

    public void setupAutoPins() {
        //get original image Mat;
        Mat imageMat = new Mat();
        Utils.bitmapToMat(mBitmapToCrop, imageMat);
        List<MatOfPoint2f> corners = new TransformationEngine().findCorners(mBitmapToCrop, imageMat, 0.3f, 40, 2);
        MatOfPoint2f square = rectangleCornersFrom(corners);
//        MatOfPoint2f square = corners.get(0);

//        Utils.matToBitmap(corners, bitmap);
        if (square != null) {
            Point[] points = square.toArray();
            Point[] sorted = TransformationEngine.orderPoints(points);
            mPins[0].x = (int) (sorted[0].x * mScale);
            mPins[0].y = (int) (sorted[0].y * mScale);
            mPins[1].x = (int) (sorted[1].x * mScale);
            mPins[1].y = (int) (sorted[1].y * mScale);
            mPins[2].x = (int) (sorted[2].x * mScale);
            mPins[2].y = (int) (sorted[2].y * mScale);
            mPins[3].x = (int) (sorted[3].x * mScale);
            mPins[3].y = (int) (sorted[3].y * mScale);
        }
    }

    private MatOfPoint2f rectangleCornersFrom(List<MatOfPoint2f> squares) {
        if (squares.size() > 0) {
            double maxSquareArea = -1.0;
            int maxSquareAreaIndex = -1;

            for (int index = 0; index < squares.size(); index++) {
                //TODO use abs value
                double squareArea = Imgproc.contourArea(squares.get(index));
                if (squareArea > maxSquareArea) {
                    maxSquareArea = squareArea;
                    maxSquareAreaIndex = index;
                }
            }
            MatOfPoint2f square = squares.get(maxSquareAreaIndex);
            return square;

        }
        return null;
    }


    public static class Pin {
        public volatile int x;
        public volatile int y;
        int radius;
//        Rect touchRect;

        public Pin(int x, int y, int r) {
            this.x = x;
            this.y = y;
            this.radius = r;
//            this.touchRect = new Rect(x - radius / 2, y - radius / 2, x + radius / 2, y + radius / 2);
        }

        public Point point() {
            return new Point(x, y);
        }

//        public void updateTouch() {
//            touchRect.set(x - radius / 2, y - radius / 2, x + radius / 2, y + radius / 2);
//        }
    }
}