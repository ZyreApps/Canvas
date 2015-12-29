package com.codejockey.canvas.views;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.codejockey.canvas.R;
import com.codejockey.canvas.dialog.BrushSizeChooserFragment;
import com.codejockey.canvas.listener.OnNewBrushSizeSelectedListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import yuku.ambilwarna.AmbilWarnaDialog;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = MainActivity.class.getName();
    private Toolbar mToolbar_top;
    private Toolbar mToolbar_bottom;
    private static final String LOG_CAT = MainActivity.class.getSimpleName();


    private CustomView mCustomView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCustomView = (CustomView)findViewById(R.id.custom_view);

        mToolbar_top = (Toolbar) findViewById(R.id.toolbar_top);
        setSupportActionBar(mToolbar_top);

        mToolbar_bottom = (Toolbar)findViewById(R.id.toolbar_bottom);
        mToolbar_bottom.setOverflowIcon(ContextCompat.getDrawable(this, R.drawable.ic_dots_vertical_white_24dp));

        mToolbar_bottom.inflateMenu(R.menu.menu_drawing);
        mToolbar_bottom.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
        {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {
                handleDrawingIconTouched(item.getItemId());
                return false;
            }
        });


        //Set up the Zyre functionality
        setUpJni();

        //Test that functionality
//        this.zyre_test(true);
    }

    private void handleDrawingIconTouched(int itemId)
    {
        switch (itemId){
            case R.id.action_delete:
                deleteDialog();
                break;
            case R.id.action_undo:
                mCustomView.onClickUndo();
                break;
            case R.id.action_redo:
                mCustomView.onClickRedo();
                break;
            case R.id.action_share:
                shareDrawing();
                break;
            case R.id.action_brush:
                brushSizePicker();
                break;
            case R.id.action_color:
                colorPicker();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        return super.onOptionsItemSelected(item);
    }

    private void deleteDialog()
    {
        AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
        deleteDialog.setTitle(getString(R.string.delete_drawing));
        deleteDialog.setMessage(getString(R.string.new_drawing_warning));
        deleteDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                mCustomView.eraseAll();
                dialog.dismiss();
            }
        });

        deleteDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });

        deleteDialog.show();
    }

    public void saveDrawingDialog(){
        //save drawing attach to Notification Bar and let User Open Image to share.
        AlertDialog.Builder saveDialog = new AlertDialog.Builder(this);
        saveDialog.setTitle("Save drawing");
        saveDialog.setMessage("Save drawing to device Gallery?");
        saveDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int which){
                saveThisDrawing();
            }
        });
        saveDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int which){
                dialog.cancel();
            }
        });
        saveDialog.show();
    }

    public void saveThisDrawing()
    {
        String path = Environment.getExternalStorageDirectory().toString();
        path = path  +"/"+ getString(R.string.app_name);
        File dir = new File(path);
        //save drawing
        mCustomView.setDrawingCacheEnabled(true);

        //attempt to save
        String imTitle = "Drawing" + "_" + System.currentTimeMillis()+".png";
        String imgSaved = MediaStore.Images.Media.insertImage(
                getContentResolver(), mCustomView.getDrawingCache(),
                imTitle, "a drawing");

        try
        {
            if (!dir.isDirectory()|| !dir.exists())
            {
                dir.mkdirs();
            }
            mCustomView.setDrawingCacheEnabled(true);
            File file = new File(dir, imTitle);
            FileOutputStream fOut = new FileOutputStream(file);
            Bitmap bm =  mCustomView.getDrawingCache();
            bm.compress(Bitmap.CompressFormat.PNG, 100, fOut);


        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Uh Oh!");
            alert.setMessage("Oops! Image could not be saved. Do you have enough space in your device?1");
            alert.setPositiveButton("OK", null);
            alert.show();

        }
        catch (IOException e)
        {
            Toast unsavedToast = Toast.makeText(getApplicationContext(),
                    "Oops! Image could not be saved. Do you have enough space in your device2?", Toast.LENGTH_SHORT);
            unsavedToast.show();
            e.printStackTrace();
        }

        if(imgSaved!=null){
            Toast savedToast = Toast.makeText(getApplicationContext(),
                    "Drawing saved to Gallery!", Toast.LENGTH_SHORT);
            savedToast.show();
        }

        mCustomView.destroyDrawingCache();
    }

    private void shareDrawing()
    {
        mCustomView.setDrawingCacheEnabled(true);
        mCustomView.invalidate();
        String path = Environment.getExternalStorageDirectory().toString();
        OutputStream fOut = null;
        File file = new File(path, "android_drawing_app.jpg");
        file.getParentFile().mkdirs();

        try
        {
            file.createNewFile();
        }
        catch (Exception e)
        {
            Log.e(LOG_CAT, e.getCause() + e.getMessage());
        }

        try
        {
            fOut = new FileOutputStream(file);
        }
        catch (Exception e)
        {
            Log.e(LOG_CAT, e.getCause() + e.getMessage());
        }

        if (mCustomView.getDrawingCache() == null)
        {
            Log.e(LOG_CAT,"Unable to get drawing cache ");
        }

        mCustomView.getDrawingCache().compress(Bitmap.CompressFormat.JPEG, 85, fOut);

        try
        {
            fOut.flush();
            fOut.close();
        }
        catch (IOException e)
        {
            Log.e(LOG_CAT, e.getCause() + e.getMessage());
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM,Uri.fromFile(file));
        shareIntent.setType("image/jpg");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share image"));


    }

    private void brushSizePicker()
    {
        //Implement get/set brush size
        BrushSizeChooserFragment brushDialog = BrushSizeChooserFragment.NewInstance((int) mCustomView.getLastBrushSize());

        brushDialog.setOnNewBrushSizeSelectedListener(new OnNewBrushSizeSelectedListener()
        {
            @Override
            public void OnNewBrushSizeSelected(float newBrushSize)
            {
                mCustomView.setBrushSize(newBrushSize);
                mCustomView.setLastBrushSize(newBrushSize);
            }
        });

        brushDialog.show(getSupportFragmentManager(), "Dialog");
    }

    private void colorPicker()
    {
        final AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, mCustomView.getLastColor(), new AmbilWarnaDialog.OnAmbilWarnaListener()
        {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color)
            {
                // color is the color selected by the user.
                mCustomView.setColor(color);
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {
                // cancel was selected by the user
            }

        });


        dialog.show();

    }

    private void setUpJni()
    {
        try
        {
            Log.d(TAG,"About to load libraries");
            System.loadLibrary("czmq");
            System.loadLibrary("zmq");
            System.loadLibrary("zyre");
            System.loadLibrary("sodium");
//            System.loadLibrary("zyrejni");
        }
        catch (Exception e)
        {
            Log.w(TAG, "Exception caught trying to load library: " + e);
        }

    }


//    public native void zyre_test(boolean b);
}
