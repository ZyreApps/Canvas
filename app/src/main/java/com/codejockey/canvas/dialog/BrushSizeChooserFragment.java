package com.codejockey.canvas.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.codejockey.canvas.R;
import com.codejockey.canvas.listener.OnNewBrushSizeSelectedListener;


/**
 * Created by Valentine on 6/6/2015.
 */
public class BrushSizeChooserFragment extends DialogFragment
{

    private float selectedBrushSize;
    private OnNewBrushSizeSelectedListener mListener;
    private SeekBar                        brushSizeSeekBar;
    private TextView                       minValue;
    private TextView                       maxValue;
    private TextView                       currentValue;
    private int                            currentBrushSize ;
    private static boolean                 bVariableSize;
    private static String                  sCurrentBrushSizeKey = "current_brush_size";
    private static String                  sFixedOrVariableKey = "fixed_or_variable";

    /**
     *
     * @param listener an implementation of the listener
     *
     */
    public void setOnNewBrushSizeSelectedListener(OnNewBrushSizeSelectedListener listener)
    {
        mListener = listener;
    }

    public static BrushSizeChooserFragment NewInstance(int size)
    {
        BrushSizeChooserFragment fragment = new BrushSizeChooserFragment();
        Bundle args = new Bundle();

        if(size < 1)
        {
            size = 1;
        }

        args.putInt(sCurrentBrushSizeKey, size);
        args.putBoolean(sFixedOrVariableKey, bVariableSize);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        if (args != null && args.containsKey(sCurrentBrushSizeKey))
        {
            int brushSize = args.getInt(sCurrentBrushSizeKey, 0);
            if (brushSize > 0)
            {
                currentBrushSize = brushSize;
            }
        }

        if (args != null && args.containsKey(sFixedOrVariableKey))
        {
            bVariableSize = args.getBoolean(sFixedOrVariableKey, false);
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
        final View dialogView = inflater.inflate(R.layout.dialog_brush_size_chooser, null);

        if (dialogView != null)
        {
            //set the starting value of the seek bar for visual aide
            minValue = (TextView)dialogView.findViewById(R.id.text_view_min_value);
            int minSize = getResources().getInteger(R.integer.min_size);
            minValue.setText(minSize + "");

            maxValue = (TextView)dialogView.findViewById(R.id.text_view_max_value);
            maxValue.setText(String.valueOf(getResources().getInteger(R.integer.max_size)));


            currentValue = (TextView)dialogView.findViewById(R.id.text_view_brush_size);

            if (currentBrushSize > 0)
            {
                currentValue.setText(getResources().getString(R.string.label_brush_size) + currentBrushSize);
            }

            brushSizeSeekBar = (SeekBar)dialogView.findViewById(R.id.seek_bar_brush_size);
            brushSizeSeekBar.setProgress(currentBrushSize);

            brushSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
            {
                int progressChanged = 0;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
                {
                    progressChanged = progress;
                    currentValue.setText(getResources().getString(R.string.label_brush_size) + progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar)
                {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar)
                {
                    mListener.OnNewBrushSizeSelected(progressChanged);
                }
            });

            //get the fixed/variable radio buttons
            final RadioButton fixed      = (RadioButton)dialogView.findViewById(R.id.radioButtonFixed);
            final RadioButton variable   = (RadioButton)dialogView.findViewById(R.id.radioButtonVariable);
            final RadioGroup  radioGroup = (RadioGroup)dialogView.findViewById(R.id.radioGroup);

            if(bVariableSize)
            {
                brushSizeSeekBar.setEnabled(false);
                variable.toggle();
            }
            else
            {
                brushSizeSeekBar.setEnabled(true);
                fixed.toggle();
            }

            radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId)
                {
                    if(fixed.getId() == checkedId)
                    {
                        brushSizeSeekBar.setEnabled(true);
                        bVariableSize = false;
                    }
                    else
                    {
                        brushSizeSeekBar.setEnabled(false);
                        bVariableSize = true;
                    }

                }
            });

        }

        builder.setTitle("Choose new Brush Size").setPositiveButton("Ok", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        }).setView(dialogView);


        return builder.create();

    }


}
