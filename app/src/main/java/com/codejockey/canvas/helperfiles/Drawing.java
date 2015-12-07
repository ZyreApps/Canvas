package com.codejockey.canvas.com.codejockey.canvas.helperfiles;

/*  Canvas Drawing class

    Accepts touch events and draws curves on a canvas. Solves these
    major problems:

    * Smooths jagged lines by drawing b-spline curves between sample
      points.
    * Snaps curve start and end points to the border to make it easier
      to draw closed areas.
    * Snaps curve end to curve start, if user drew a shape, to make it
      easier to draw closed shapes.
    * Adjusts curve thickness based on drawing velocity, so that slow
      drawing creates thinner lines.
    * Smooths curve thickness by removing outliers (some devices have
      peaky sample rates).
    * Allows perfect shape filling by using 100% pixel colors (no anti-
      aliasing).
    * Draws on larger bitmap, scales down to view size, to allow
      zooming and give better image quality.

    Algorithm by Pieter Hintjens and Darrin Smith.
 */

        import android.graphics.Bitmap;
        import android.graphics.Canvas;
        import android.graphics.Color;
        import android.graphics.Paint;
        import android.graphics.Point;
        import android.os.AsyncTask;
        import android.os.Vibrator;
        import android.util.Log;
        import android.view.GestureDetector;
        import android.view.MotionEvent;
        import android.widget.ImageView;
        import android.content.Context;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.ListIterator;

//  Import Magnet API
//        import com.samsung.magnet.wrapper.MagnetAgent;
//        import com.samsung.magnet.wrapper.MagnetAgentImpl;
//        import com.samsung.magnet.wrapper.MagnetAgent.ChannelListener;
//        import com.samsung.magnet.wrapper.MagnetAgent.MagnetListener;
//        import com.samsung.magnet.wrapper.MagnetAgent.MagnetServiceListener;

//        import com.samsung.allshare.canvas.QueueLinearFloodFiller;

public class Drawing
{
    private static final String TAG = "CanvasDrawing";

    private Context context;
    private ImageView imageview;
    private Bitmap bitmap;
    private Canvas canvas = new Canvas ();
    private Paint paint = new Paint ();

    //  Magnet interface
//    private MagnetAgent magnet = new MagnetAgentImpl ();
    private String device_id = "";

    //  Drawing state
    private int width;
    private int height;
    private int ink;
    private int paper;

    //  Ratio of height or width for snaps
    private static final float SNAP_MARGIN = 0.04f;
    //  Snap methods
    private static final int SNAP_NONE = 0;
    private static final int SNAP_TO_EDGE = 1;
    private static final int SNAP_TO_START = 2;
    //  Snap margins
    private int snapmin_x, snapmin_y;
    private int snapmax_x, snapmax_y;

    //  Current drawn curve
    private Point curve_start;          //  Requested starting point
    private Point curve_start_snap;     //  Snapped starting point
    private Point curve_end;            //  Requested ending point
    private Point curve_end_snap;       //  Snapped ending point
    private float curve_width;          //  Current (median) width
    private float curve_extent;         //  Maximum distance from start

    //  Curve drawing constants
    //  We vary paintbrush width by at most this much each stroke
    //  to reduce the effects of event time spikes (affects some devices)
    private static final float OUTLIER_TOLERANCE = 0.1f;
    //  Number of steps we draw between points
    private static final int CURVE_STEPS = 30;
    //  Weight of curve between points
    private static final float CURVE_DENSITY = 5.0f;
    //  Starting point for event sample rate guess
    private static final int DIFF_BASELINE = 40;

    //  Points in curve; we store last four knots
    private Point [] knots = new Point [4];
    private long last_knot_time = 0;

    //  Smart invalidation after drawing curve
    private float minx, maxx, miny, maxy;

    //  Median event sample rate, using the baseline as initial guess
    private long median_diff = DIFF_BASELINE;

    //  Replaying to UI, don't process input events
    private boolean ui_locked;

    //  Did we snap the start of the line to the edge?
    private boolean start_snapped;

    //  Each command represents one instruction
    //  We can replay these from first to last
    class command {
        //  These are the command types we know
        public static final int RESET = 1;     //  Reset drawing
        public static final int PAPER = 2;     //  Set paper color
        public static final int INK   = 3;     //  Set ink color
        public static final int ERASE = 4;     //  Erase canvas
        public static final int DOWN  = 5;     //  Start a line
        public static final int MOVE  = 6;     //  Draw the line
        public static final int UP    = 7;     //  Draw the line
        public static final int FILL  = 8;     //  Fill an area
        private int type;
        private int x;
        private int y;
        private int color;
        public command (int type, Point point) {
            this.type = type;
            this.x = point.x;
            this.y = point.y;
        }
        public command (int type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
        public command (int type, int color) {
            this.type = type;
            this.color = color;
        }
    }
    //  List of commands since we started
    private List commands = new ArrayList <command> (1000);

    public Drawing (Context _context, ImageView _imageview)
    {
        //  Store parent context
        context = _context;
        imageview = _imageview;

        //  Create our bitmap drawing area
        reset (1200, 1600);

        //  Set-up paint brush
        paint.setStyle (Paint.Style.STROKE);
        paint.setStrokeJoin (Paint.Join.ROUND);
        paint.setStrokeCap (Paint.Cap.SQUARE);

        //  Connect to the Magnet network
//        magnet.initServiceWithCustomAction ("com.samsung.magnet.service.Canvas",
//                context, service_listener);
//        magnet.registerPublicChannelListener (channel_listener);
    }

    public void onTouchEvent (MotionEvent event)
    {
        if (!gesture_detector.onTouchEvent (event)) {
            Point point = get_point (event);
            switch (event.getAction ()) {
                case MotionEvent.ACTION_DOWN:
                    commands.add (new command (command.DOWN, point));
                    down_headless (point);
                    break;
                case MotionEvent.ACTION_MOVE:
                    commands.add (new command (command.MOVE, point));
                    move_headless (point);
                    rect_invalidate ();
                    break;
                case MotionEvent.ACTION_UP:
                    commands.add (new command (command.UP, point));
                    up_headless (point);
                    rect_invalidate ();
                    break;
            }
        }
    }

    //  Set the canvas size
    public void reset (int _width, int _height)
    {
        commands.add (new command (command.RESET, _width, _height));
        width = _width;
        height = _height;
        paper = Color.WHITE;
        ink = Color.BLACK;
        bitmap = Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);

        //  Calculate snap knots, each time the width/height changes
        snapmin_x = (int) (width * SNAP_MARGIN);
        snapmin_y = (int) (height * SNAP_MARGIN);
        snapmax_x = width - snapmin_x;
        snapmax_y = height - snapmin_y;

        //  Draw the bitmap on the image
        canvas.setBitmap (bitmap);
        canvas.drawColor (paper);
        imageview.setImageBitmap (bitmap);
        imageview.setScaleType (ImageView.ScaleType.FIT_XY);
        imageview.invalidate ();
    }

    //  Set the drawing ink color
    public void setInk (int _ink)
    {
        commands.add (new command (command.INK, _ink));
        ink = _ink;
    }

    //  Set the drawing paper color
    public void setPaper (int _paper)
    {
        commands.add (new command (command.PAPER, _paper));
        paper = _paper;
    }

    //  Reset the canvas to the current paper color
    public void erase ()
    {
        commands.add (new command (command.ERASE, paper));
        erase_headless ();
    }

    //  Event handlers
    //  ---------------------------------------------------------------------
/*
    private MagnetServiceListener service_listener = new MagnetServiceListener ()
    {
        @Override
        public void onWifiConnectivity () {
            device_id = magnet.getLocalName ();
            Log.i (TAG, "Magnet onWifiConnectivity received for device ID: " + device_id);
        }
        @Override
        public void onServiceTerminated () {
            Log.i (TAG, "Magnet onServiceTerminated received");
        }
        @Override
        public void onServiceNotFound () {
            Log.i (TAG, "Magnet onServiceNotFound received");
        }
        @Override
        public void onNoWifiConnectivity () {
            Log.i (TAG, "Magnet onNoWifiConnectivity received");
        }
        @Override
        public void onMagnetPeers () {
            Log.i (TAG, "Magnet onMagnetPeers received");
        }
        @Override
        public void onMagnetNoPeers () {
            Log.i (TAG, "Magnet onMagnetNoPeers received");
        }
        @Override
        public void onInvalidSdk () {
            Log.i (TAG, "Magnet onInvalidSdk received");
        }
    };

    private ChannelListener channel_listener = new ChannelListener ()
    {
        @Override
        public void onFailure (int reason) {
            Log.i (TAG, "Magnet onFailure received");
        }
        @Override
        public void onFileReceived (
                String fromNode, String fromChannel,
                String originalName, String hash,
                String exchangeId, String type,
                long fileSize, String tmp_path) {
            Log.i (TAG, "Magnet onFileReceived received");
        }
        @Override
        public void onFileFailed (
                String fromNode, String fromChannel,
                String originalName, String hash,
                String exchangeId, int reason) {
            Log.i (TAG, "Magnet onFileFailed received");
        }
        @Override
        public void onDataReceived (
                String fromNode, String fromChannel, String type, List<byte[]> payload) {
            Log.i (TAG, "Magnet onDataReceived received");
        }
        @Override
        public void onChunkReceived (
                String fromNode, String fromChannel,
                String originalName, String hash, String exchangeId, String type,
                long fileSize, long offset) {
            Log.i (TAG, "Magnet onChunkReceived received");
        }
        @Override
        public void onFileNotified (
                String arg0, String arg1, String arg2,
                String arg3, String arg4, String arg5,
                long arg6, String arg7) {
            Log.i (TAG, "Magnet onFileNotified received");
        }
        @Override
        public void onJoinEvent (String fromNode, String fromChannel)
        {
            Log.i (TAG, "Magnet onJoinEvent received, node:" + fromNode + " channel:" + fromChannel);
            if (device_id == null) {
                Log.i (TAG, "onJoinEvent: E: NULL device ID");
                device_id = magnet.getLocalName ();
            }
        }
        @Override
        public void onLeaveEvent (String fromNode, String fromChannel) {
            Log.i (TAG, "Magnet onLeaveEvent received, node: " + fromNode + " channel: " + fromChannel);
        }
    };
*/
    //  Private methods
    //  ---------------------------------------------------------------------

    private GestureDetector gesture_detector = new GestureDetector (
            new GestureDetector.SimpleOnGestureListener () {
                public boolean onSingleTapUp (MotionEvent event) {
                    Point point = get_point (event);
                    commands.add (new command (command.FILL, point));
                    fill_headless (point);
                    imageview.invalidate ();
                    vibrate ();
                    return true;
                }
                public void onLongPress (MotionEvent event) {
                    new replay_commands ().execute ("nothing");
                }
            }
    );

    //  Headless methods do not create any command history
    //  Thus they can safely be called to replay commands
    private void erase_headless ()
    {
        canvas.drawColor (paper);
    }

    //  Start a new line
    private void down_headless (Point point)
    {
        curve_start = point;
        curve_start_snap = snap (point, SNAP_TO_EDGE);
        curve_extent = 0;

        if (curve_start.equals (curve_start_snap))
            curve_open (curve_start);
        else {
            curve_open (curve_start_snap);
            curve_move (curve_start);
        }
    }

    //  Continue the line
    private void move_headless (Point point)
    {
        curve_move (point);

        //  Track the extent of the curve to help us decide whether to
        //  close the shape automatically.
        float extent = distance (point, curve_start);
        if (curve_extent < extent)
            curve_extent = extent;
    }

    //  End the line
    private void up_headless (Point point)
    {
        //  Snap the end of the curve back to the start if close enough
        //  but only if the start wasn't itself snapped to the edge.
        curve_end = point;
        if (curve_start.equals (curve_start_snap))
            curve_end_snap = snap (point, SNAP_TO_START);
        else
            curve_end_snap = snap (point, SNAP_TO_EDGE);

        if (curve_end.equals (curve_end_snap))
            curve_close (curve_end);
        else {
            curve_move  (curve_end);
            curve_close (curve_end_snap);
        }
    }

    //  Fill the selected area with the current ink color
    public void fill_headless (Point point)
    {
        int targetColor = bitmap.getPixel (point.x, point.y);
        QueueLinearFloodFiller filler = new QueueLinearFloodFiller (bitmap, targetColor, ink);
        filler.setTolerance (25);
        filler.floodFill (point.x, point.y);
    }

    //  Convert motion event coordinates into point in our drawing
    private Point get_point (MotionEvent event)
    {
        Point point = new Point ();
        point.x = (int) (event.getX () * width / imageview.getWidth ());
        point.y = (int) (event.getY () * height / imageview.getHeight ());
        return point;
    }

    //  Return point with snap if requested
    private Point snap (Point point, int mode)
    {
        if (mode == SNAP_TO_EDGE) {
            if (point.x < snapmin_x)
                point.x = 0;
            else
            if (point.x > snapmax_x)
                point.x = width;

            if (point.y < snapmin_y)
                point.y = 0;
            else
            if (point.y > snapmax_y)
                point.y = height;
        }
        else
        if (mode == SNAP_TO_START) {
            //  Snap back to start if start didn't move to edge.
            //  We snap back if we're less than 50% of the curve
            //  extent away from the starting point, i.e. we made
            //  some kind of shape. Ending point must also be
            //  reasonably close to starting point.
            float extent = distance (curve_start, point);
            if (curve_start.equals (curve_start_snap)
                    &&  extent < curve_extent / 2.0f
                    &&  extent < snapmin_x + snapmin_y)
                point = curve_start;
            else
                point = snap (point, SNAP_TO_EDGE);
        }
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

    //  Plots a b-spline curve through the last four knots of the curve
    //  Based on Section 4.2 of Ammeraal, L. (1998) Computer Graphics for
    //  Java Programmers, Chichester: John Wiley.
    //
    private void curve_open (Point knot)
    {
        //  Load up our knots with our start position.
        //  This solves two problems; one that we need at least 4
        //  knots to draw a curve and two, that we lose the first
        //  point unless we repeat it three times.
        knots [1] = knot;
        knots [2] = knot;
        knots [3] = knot;
        last_knot_time = System.currentTimeMillis ();
        curve_width = 2.0f;
    }

    private void curve_move (Point knot)
    {
        //  Adds a knot and draws the curve. Since we've preloaded
        //  the knots in curve_open this will draw between two or
        //  more points (aka knot in b-spline jargon).
        knots [0] = knots [1];
        knots [1] = knots [2];
        knots [2] = knots [3];
        knots [3] = knot;

        //  Sample rates range from 60-100 msecs depending on the device
        //  We estimate a rolling median using the simple technique of
        //  taking each new value; if it's larger than sample rate, add
        //  1 to sample rate and if it's smaller, subtract 1.
        long this_knot_time = System.currentTimeMillis ();
        long time_diff = this_knot_time - last_knot_time;
        if (time_diff > 10)
            median_diff += median_diff > time_diff? -1: 1;
        last_knot_time = this_knot_time;

        curve_plot ();
    }

    private void curve_close (Point knot)
    {
        //  Close the curve, drawing the end three times to ensure
        //  the curve is fully connected; otherwise the actual end
        //  point won't be drawn (the curve will stop just short).
        curve_move (knot);
        curve_move (knot);
        curve_move (knot);
    }

    //  We always draw the last 4 knots
    private void curve_plot ()
    {
        //  We take DIFF_BASELINE msec as being our "normal" sample
        //  rate. On slower screens the lines will otherwise be too fat.
        float compensation = (float)
                median_diff / DIFF_BASELINE / CURVE_STEPS * CURVE_DENSITY;

        float x1 = -1;
        float y1 = -1;
        float a0 = (knots [0].x + 4 * knots [1].x + knots [2].x) / 6;
        float b0 = (knots [0].y + 4 * knots [1].y + knots [2].y) / 6;
        float a1 = (knots [2].x - knots [0].x) / 2;
        float b1 = (knots [2].y - knots [0].y) / 2;
        float a2 = (knots [0].x - 2 * knots [1].x + knots [2].x) / 2;
        float b2 = (knots [0].y - 2 * knots [1].y + knots [2].y) / 2;
        float a3 = (knots [3].x - knots [0].x + 3 * (knots [1].x - knots [2].x)) / 6;
        float b3 = (knots [3].y - knots [0].y + 3 * (knots [1].y - knots [2].y)) / 6;

        rect_reset ();
        for (int step = 0; step <= CURVE_STEPS; step++) {
            float x0 = x1;
            float y0 = y1;
            float t = (float) step / (float) CURVE_STEPS;
            x1 = ((a3 * t + a2) * t + a1) * t + a0;
            y1 = ((b3 * t + b2) * t + b1) * t + b0;
            if (x0 != -1) {
                float distance = (float) Math.sqrt ((x1 - x0) * (x1 - x0)
                        + (y1 - y0) * (y1 - y0));
                if (distance > 0) {
                    float target_width = (float) distance / compensation + 1.0f;
                    if (target_width > curve_width)
                        curve_width += OUTLIER_TOLERANCE;
                    else
                        curve_width -= OUTLIER_TOLERANCE;

                    paint.setStrokeWidth (curve_width);
                    paint.setColor (ink);
                    canvas.drawLine (x0, y0, x1, y1, paint);
                    rect_stretch (x0, y0, x1, y1);
                }
            }
        }
    }

    //  Reset invalidation rectangle
    private void rect_reset ()
    {
        minx = width;
        maxx = -1;
        miny = height;
        maxy = -1;
    }

    private void rect_stretch (float x0, float y0, float x1, float y1)
    {
        if (minx > x0 - curve_width)
            minx = x0 - curve_width;
        if (miny > y0 - curve_width)
            miny = y0 - curve_width;
        if (maxx < x1 + curve_width)
            maxx = x1 + curve_width;
        if (maxy < y1 + curve_width)
            maxy = y1 + curve_width;
    }

    private void rect_invalidate ()
    {
        //  Adjust rectangle for imageview only the affected rectangle
        minx = minx / width * imageview.getWidth ();
        maxx = maxx / width * imageview.getWidth ();
        miny = miny / height * imageview.getHeight ();
        maxy = maxy / height * imageview.getHeight ();
        if (maxx > minx && maxy > miny)
            imageview.invalidate ((int) minx, (int) miny, (int) maxx, (int) maxy);
    }

    private void vibrate ()
    {
        Vibrator mVibrator;
        mVibrator = (Vibrator) context.getSystemService (Context.VIBRATOR_SERVICE);
        mVibrator.vibrate (10);
    }

    private class replay_commands extends AsyncTask <String, Bitmap, String> {
        protected void onPreExecute () {
            ui_locked = true;
        }
        protected String doInBackground (String... params) {
            Bitmap offscreen = Bitmap.createBitmap (width, height, Bitmap.Config.ARGB_8888);
            canvas.setBitmap (offscreen);

            ListIterator iterator = commands.listIterator ();
            while (iterator.hasNext ()) {
                command cmd = (command) iterator.next ();
                switch (cmd.type) {
                    case command.RESET:
                        erase_headless ();
                        break;
                    case command.PAPER:
                        paper = cmd.color;
                        break;
                    case command.INK:
                        ink = cmd.color;
                        break;
                    case command.ERASE:
                        erase_headless ();
                        break;
                    case command.DOWN:
                        down_headless (new Point (cmd.x, cmd.y));
                        break;
                    case command.MOVE:
                        move_headless (new Point (cmd.x, cmd.y));
                        break;
                    case command.UP:
                        up_headless (new Point (cmd.x, cmd.y));
                        break;
                    case command.FILL:
                        fill_headless (new Point (cmd.x, cmd.y));
                        break;
                }
                Bitmap onscreen = Bitmap.createBitmap (offscreen);
                publishProgress (onscreen);
            }
            return null;
        }
        protected void onProgressUpdate (Bitmap... onscreen) {
            imageview.setImageBitmap (onscreen [0]);
            imageview.invalidate ();
        }
        protected void onPostExecute (String result) {
            ui_locked = false;
        }
    }

    private void trace (String s, Point p)
    {
        Log.d (TAG, s + " " + p.x + "/" + p.y);
    }
}
