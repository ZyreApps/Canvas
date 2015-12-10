package com.codejockey.canvas.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.codejockey.canvas.R;

import java.util.ArrayList;

/**
 * Created by Valentine on 10/27/2015.
 *
 * Updated by DarrinS
 * Fixed the bug where the size selection didn't work
 * Added functionality to allow different sized brushes to be used (no change made to prior strokes)
 * Added Fixed/Variable selection capability (no functionality for it yet...comes next).
 */
public class CustomView extends View
{

    private static final String LOG_CAT = CustomView.class.getSimpleName();

    //drawing path
    private Path drawPath;

    //defines how to draw
    private Paint drawPaint;

    //initial color
    private int paintColor = 0xFF660000;

    private Paint _paintBlur;

    //flag to set erase mode
    private boolean eraseMode = false;

    //canvas - holding pen, holds your drawings
    //and transfers them to the view
    private Canvas drawCanvas;

    //canvas bitmap
    private Bitmap canvasBitmap;

    //brush size
    private float currentBrushSize;
    private float lastBrushSize;
    private int lastColor = 0xFF000000;

    private ArrayList<Path> paths = new ArrayList<Path>();
    private ArrayList<Paint> paints = new ArrayList<Paint>();

    private ArrayList<Path> undonePaths = new ArrayList<Path>();
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private void init()
    {
        currentBrushSize = getResources().getInteger(R.integer.medium_size);
        lastBrushSize = currentBrushSize;

        drawPath = new Path();
        drawPaint = new Paint();
        drawPaint.setColor(paintColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(currentBrushSize);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        paints.add(drawPaint);

/*
        this._paintBlur = new Paint();
        this._paintBlur.set(drawPaint);
        this._paintBlur.setAntiAlias(true);
        this._paintBlur.setDither(true);
        this._paintBlur.setStyle(Paint.Style.STROKE);
        this._paintBlur.setStrokeJoin(Paint.Join.ROUND);
        this._paintBlur.setStrokeCap(Paint.Cap.ROUND);
        this._paintBlur.setColor(Color.RED);
        this._paintBlur.setStrokeWidth(currentBrushSize);
        this._paintBlur.setMaskFilter(new BlurMaskFilter(10.0F, BlurMaskFilter.Blur.OUTER));
*/
    }


    public CustomView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        int paintIndex = 0;

        for (Path p : paths)
        {
            canvas.drawPath(p, paints.get(paintIndex));

            paintIndex++;
        }

        canvas.drawPath(drawPath, drawPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
       //create canvas of certain device size.
        super.onSizeChanged(w, h, oldw, oldh);

        //create Bitmap of certain w,h
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        //apply bitmap to graphic to start drawing.
        drawCanvas = new Canvas(canvasBitmap);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        float touchX = event.getX();
        float touchY = event.getY();

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                touch_start(touchX, touchY);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(touchX, touchY);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touch_up();
                invalidate();
                break;
            default:
                return false;
        }
        return true;
    }

    /** Set erase true or false */
    public void setErase(boolean isErase)
    {
        eraseMode = isErase;

        if(eraseMode){
            drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        } else {
            drawPaint.setXfermode(null);
        }
    }

    /** Start new Drawing */
    public void eraseAll() {
       drawPath = new Path();
        paths.clear();
        drawCanvas.drawColor(Color.WHITE);
        invalidate();
    }


    private void touch_start(float x, float y) {
        undonePaths.clear();
        drawPath.reset();
        drawPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    private void touch_up() {
        drawPath.lineTo(mX, mY);
        drawCanvas.drawPath(drawPath, drawPaint);
        paths.add(drawPath);
        paints.add(drawPaint);

        drawPath = new Path();
        drawPaint = new Paint();
    }

    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            drawPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }


    public void onClickUndo () {
       if (paths.size()>0)
        {
            undonePaths.add(paths.remove(paths.size()-1));
            invalidate();
        }

    }

    public void onClickRedo ()
    {
       if (undonePaths.size()>0)
        {
            paths.add(undonePaths.remove(undonePaths.size()-1));
            invalidate();
        }

    }

    //method to set brush size
    public void setBrushSize(float newSize)
    {
        float pixelAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newSize, getResources().getDisplayMetrics());
        currentBrushSize = pixelAmount;


        drawPath = new Path();
        drawPaint = new Paint();

        drawPaint.setColor(paintColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        drawPaint.setStrokeWidth(newSize);

        paints.add(drawPaint);
    }

    public void setColor(int color)
    {
        drawPath = new Path();
        drawPaint = new Paint();

        drawPaint.setColor(color);
        drawPaint.setAntiAlias(true);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        drawPaint.setStrokeWidth(currentBrushSize);

        paints.add(drawPaint);
    }

    public void setLastBrushSize(float lastSize)
    {
        lastBrushSize=lastSize;
    }

    public void setLastColor(int lastColor)
    {
        this.lastColor = lastColor;
    }

    public float getLastBrushSize()
    {
        return lastBrushSize;
    }

    public int getLastColor()
    {
        return lastColor;
    }



}
