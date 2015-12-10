/*  Canvas Palette class

    Accepts touch events and draws palette on a canvas
 */
 
package com.codejockey.canvas.helperfiles;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Vibrator;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.content.Context;

import com.codejockey.canvas.R;


public class Palette
{
    private static final String TAG = "CanvasPalette";
    private Context context;
    
    private ImageView imageview;
    private Bitmap bitmap;
    private Canvas canvas = new Canvas ();
    private Paint paint = new Paint ();
    private Drawing drawing;
    
    //  Dimensions of the palette bitmap
    private int height = PALETTE_BOX;
    private int width = PALETTE_BOX * PALETTE_SIZE;
    
    //  Array of color palettes of 50x50px, 8 at a time
    private static final int PALETTE_SIZE = 8;
    private static final int PALETTE_BOX = 50;
    private int [] colors;
    private int color_base = 0;

    //  If we stay in one color for this many msecs it's like a long press
    private static final int LONG_PRESS = 500;
    
    //  Current drawn points
    private Point start_point;          //  Requested starting point
    private Point end_point;            //  Requested ending point
    private int start_color;            //  Index into colors
    private long start_time;            //  For timing long pushes

    public Palette (Context _context, ImageView _imageview, Drawing _drawing)
    {
        //  Store parent context
        context = _context;
        imageview = _imageview;
        drawing = _drawing;

        //  Get colors for palettes
        colors = context.getResources ().getIntArray (R.array.color_array);
        
        //  Create our bitmap drawing area
        bitmap = Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);
        
        //  Draw the bitmap on the image
        imageview.setImageBitmap (bitmap);
        imageview.setScaleType (ImageView.ScaleType.FIT_XY);

        //  Create our drawing objects
        canvas.setBitmap (bitmap);
        redraw ();
    }

    public void onTouchEvent (MotionEvent event)
    {
        if (!gesture_detector.onTouchEvent (event)) {
            switch (event.getAction ()) {
                case MotionEvent.ACTION_DOWN:
                    touch_down (event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    touch_move (event);
                    break;
                case MotionEvent.ACTION_UP:
                    touch_up (event);
                    break;
            }
        }
    }
    
    public void redraw ()
    {
        int color_index = color_base;
        int box_start = 0;
        
        for (int box = 0; box < PALETTE_SIZE; box++) {
            paint.setColor (colors [color_index++]);
            canvas.drawRect (box_start, 0, box_start + PALETTE_BOX, height, paint);
            box_start += PALETTE_BOX;
        }
        imageview.invalidate ();
    }

    //  Private methods
    //  ---------------------------------------------------------------------
    
    private GestureDetector gesture_detector = new GestureDetector (
        new GestureDetector.SimpleOnGestureListener () {
            // Long press
            // - in palette means fill whole canvas with drawing color
            public void onLongPress (MotionEvent event) {
                if (start_color >= 0) {
                    Log.d (TAG, "Palette OnLongPress -- erase");
                    touch_fill (event);
                }
            }
            // Single tap
            // - in palette means select color
            public boolean onSingleTapUp (MotionEvent event) {
                touch_select (event);
                return true;
            }
        }
    );

    private void touch_down (MotionEvent event)
    {
        start_point = get_point (event);
        start_color = get_color (start_point);
        start_time = System.currentTimeMillis ();
        
        Log.d (TAG, "Start color=" + start_color);
    }

    //  Returns index into color array, or -1 if no valid selection
    private int get_color (Point point)
    {
        if (point.y < height)
            return color_base + (point.x / PALETTE_BOX);
        else
            return -1;
    }

    private void touch_move (MotionEvent event)
    {
        //  Get current point, color, and time
        Point point = get_point (event);
        int color = get_color (point);
        
        if (color != start_color) {
            //  Invalidate start color if we left the box
            start_color = -1;
            Log.d (TAG, "Invalidated start color");
        }
        else
        if (color >= 0
        &&  System.currentTimeMillis () - start_time > LONG_PRESS) {
            Log.d (TAG, "Simulated long press in color -- erase");
            touch_fill (event);
        }
    }

    //  End the line
    private void touch_up (MotionEvent event)
    {
        Point point = get_point (event);
        int delta = point.x - start_point.x;
        
        if (Math.abs (delta) > PALETTE_BOX) {
            if (delta > 0){
                Log.d (TAG, "Right color swipe");
                color_base -= PALETTE_SIZE;
            }
            else{
                Log.d (TAG, "Left color swipe");
                color_base += PALETTE_SIZE;
            }
            color_base = (color_base + colors.length) % colors.length;
            Log.d (TAG, " -- index=" + color_base);
            redraw ();
        }
    }

    //  Fill the canvas the current ink color
    public void touch_fill (MotionEvent event)
    {
        Point point = get_point (event);
        int color = get_color (point);
        if (color >= 0) {
            drawing.setPaper (colors [color]);
            drawing.erase ();
            imageview.invalidate ();
            vibrate ();

            //  Set new ink to black or white
            if (colors [color] == Color.BLACK)
                drawing.setInk (Color.WHITE);
            else
                drawing.setInk (Color.BLACK);

            //  Invalidate touch down color so we don't repeat the fill
            start_color = -1;
        }
    }
    
    //  Fill the canvas the current ink color
    public void touch_select (MotionEvent event)
    {
        Point point = get_point (event);
        int color = get_color (point);
        if (color >= 0) {
            drawing.setInk (colors [color]);
            vibrate ();
        }
    }

    //  Convert motion event coordinates into point in our drawing
    private Point get_point (MotionEvent event)
    {
        Point point = new Point ();
        point.x = (int) (event.getX () * width / imageview.getWidth ());
        point.y = (int) (event.getY () * height / imageview.getHeight ());
        return point;
    }

    //  Calculate distance in pixels between two knots
    private float distance (Point p1, Point p2)
    {
        float distance = (float) Math.sqrt (
            (p2.x - p1.x) * (p2.x - p1.x)
          + (p2.y - p1.y) * (p2.y - p1.y));
        return distance;
    }

    private void vibrate ()
    {
        Vibrator mVibrator;
        mVibrator = (Vibrator) context.getSystemService (Context.VIBRATOR_SERVICE);
        mVibrator.vibrate (10);
    }

    private void trace (String s, Point p)
    {
        Log.d (TAG, s + " " + p.x + "/" + p.y);
    }
}
