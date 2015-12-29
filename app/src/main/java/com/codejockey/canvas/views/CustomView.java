package com.codejockey.canvas.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
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
    private static final String TAG = CustomView.class.getName();

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
    private ArrayList<Paint> undonePaints = new ArrayList<Paint>();

    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;

    private void init()
    {
        currentBrushSize = getResources().getInteger(R.integer.medium_size);
        lastBrushSize = currentBrushSize;

        drawPath = new Path();
        drawPaint = getNewPaint();

//        paints.add(drawPaint);

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
            Paint paint = paints.get(paintIndex);

            canvas.drawPath(p, paint);

            Log.d(TAG, "In onDraw. Brush size: " + paint.getStrokeWidth() + " Paint color: " + paint.getColor());

            paintIndex++;
        }

        canvas.drawPath(drawPath, drawPaint);

        Log.d(TAG, "Leaving onDraw. Brush size was set to: " + drawPaint.getStrokeWidth() + "Paths size=" + paths.size() + " Paints size=" + paints.size());
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


    final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener()
    {
        public void onLongPress(MotionEvent e)
        {
            Log.i(TAG, "Longpress detected...time to flood fill");
        }
    });

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {

        float touchX = event.getX();
        float touchY = event.getY();

        gestureDetector.onTouchEvent(event);


        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                touch_start(touchX, touchY);
                invalidate();

  //              Log.i(TAG, "Starting potential long press handler");
  //              handler.postDelayed(mLongPressed, 1000);
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(touchX, touchY);
                invalidate();
//                handler.removeCallbacks(mLongPressed);
//                Log.i(TAG, "Moved: Stopping potential long press handler");
                break;
            case MotionEvent.ACTION_UP:
                touch_up();
                invalidate();
//                handler.removeCallbacks(mLongPressed);
//                Log.i(TAG, "Up: Stopping potential long press handler");
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

        if(eraseMode)
        {
            drawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
        else
        {
            drawPaint.setXfermode(null);
        }
    }

    /** Start new Drawing */
    public void eraseAll()
    {
       drawPath = new Path();
       drawPaint = new Paint();
       paths.clear();
       paints.clear();
       drawCanvas.drawColor(Color.WHITE);
       invalidate();
    }


    private void touch_start(float x, float y)
    {
        undonePaths.clear();
        undonePaints.clear();

        drawPath.reset();
        drawPath.moveTo(x, y);

        mX = x;
        mY = y;

        Log.d(TAG, "Leaving touch_start. Brush size was set to: " + drawPaint.getStrokeWidth());
    }

    private void touch_up()
    {
        drawPath.lineTo(mX, mY);

        drawCanvas.drawPath(drawPath, drawPaint);

        paths.add(drawPath);
        paints.add(drawPaint);

        Log.d(TAG, "In touch_up. Brush size was set to: " + drawPaint.getStrokeWidth());

        drawPath = new Path();
        drawPaint = getNewPaint();
    }

    private void touch_move(float x, float y)
    {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE)
        {
            drawPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
    }


    public void onClickUndo ()
    {
       if (paths.size()>0)
        {
            undonePaths.add(paths.remove(paths.size()-1));
            undonePaints.add(paints.remove(paints.size()-1));

            paintColor = paints.get(paints.size() -1).getColor();
            drawPaint.setColor(paintColor);

            invalidate();
        }

        Log.d(TAG, "Leaving onClickUndo. Paths size=" + paths.size() + " Paints size=" + paints.size());
    }

    public void onClickRedo ()
    {
       if (undonePaths.size()>0)
        {
            paths.add(undonePaths.remove(undonePaths.size()-1));
            paints.add(undonePaints.remove(undonePaints.size()-1));
            invalidate();
        }

    }

    //method to set brush size
    public void setBrushSize(float newSize)
    {

        if(true)
        {
            return;
        }

        float pixelAmount = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newSize, getResources().getDisplayMetrics());
        currentBrushSize = pixelAmount;

        drawPath = new Path();
        drawPaint = getNewPaint();

        paths.add(drawPath);
        paints.add(drawPaint);

        drawPaint.setStrokeWidth(currentBrushSize);

        Log.d(TAG, "Leaving setBrushSize. Brush size set to: " + currentBrushSize);

    }

    public void setColor(int color)
    {
        paintColor = color;
        drawPaint.setColor(paintColor);

        Log.d(TAG, "Setting paint color to: " + drawPaint.getColor());
    }

    private Paint getNewPaint()
    {
        Paint newPaint = new Paint();
        newPaint.setColor(paintColor);
        newPaint.setAntiAlias(true);
        newPaint.setStyle(Paint.Style.STROKE);
        newPaint.setStrokeJoin(Paint.Join.ROUND);
        newPaint.setStrokeCap(Paint.Cap.ROUND);

        newPaint.setStrokeWidth(currentBrushSize);

        Log.d(TAG, "Leaving getNewPaint. Brush size set to: " + currentBrushSize);

        return newPaint;
    }

    public void setLastBrushSize(float lastSize)
    {
        lastBrushSize=lastSize;
        currentBrushSize = lastBrushSize;


        drawPaint.setStrokeWidth(lastBrushSize);


        Log.d(TAG, "Leaving setLastBrushSize. Last brush size set to: " + lastBrushSize);

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


    final Handler handler = new Handler();

    Runnable mLongPressed = new Runnable()
    {
        public void run()
        {
            Log.i("", "Long press!");
        }
    };

}
