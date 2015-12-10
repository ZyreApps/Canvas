package com.codejockey.canvas.dialog;

import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

import com.codejockey.canvas.R;

import yuku.ambilwarna.AmbilWarnaDialog;

/**
 * Created by darri_000 on 12/10/2015.
 */
public class ColorChooserFragment extends DialogFragment 
{
    private OnNewColorSelectedListener mListener;
    public static final String sCurrentColorKey = "com.codejockey.canvas.current_color_key";
    private int currentColor = 0xFF000000;

    /**
     *
     * @param listener an implementation of the listener
     *
     */
    public void setOnNewColorSelectedListener(OnNewColorSelectedListener listener)
    {
        mListener = listener;
    }

    public static ColorChooserFragment NewInstance(int color)
    {
        ColorChooserFragment fragment = new ColorChooserFragment();
        Bundle args = new Bundle();


        args.putInt(sCurrentColorKey, color);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        if (args != null && args.containsKey(sCurrentColorKey))
        {
            int Color = args.getInt(sCurrentColorKey, 0);

            if (Color > 0)
            {
                currentColor = Color;
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        // Begin building a new dialog.
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Get the layout inflater.
        final LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate the layout for this dialog.
        final View dialogView = inflater.inflate(R.layout.dialog_color_chooser, null);

        if (dialogView != null)
        {
            //Set up the view for the color picker
            final View colorPicker = dialogView.findViewById(R.id.color_picker);
        }

        builder.setTitle("Choose New Color").setPositiveButton("Ok", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        }).setView(dialogView);


        //Set up the view for the color picker
        final View colorPicker = dialogView.findViewById(R.id.color_picker);

        final AmbilWarnaDialog dialog = new AmbilWarnaDialog(dialogView.getContext(), currentColor, new AmbilWarnaDialog.OnAmbilWarnaListener()
        {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color)
            {
                // color is the color selected by the user.
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {
                // cancel was selected by the user
            }

        });


        dialog.show();

        return builder.create();

    }


}
