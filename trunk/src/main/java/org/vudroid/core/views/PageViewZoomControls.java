package org.vudroid.core.views;

import android.R;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import org.vudroid.core.events.BringUpZoomControlsListener;
import org.vudroid.core.models.ZoomModel;

public class PageViewZoomControls extends LinearLayout implements BringUpZoomControlsListener
{
    private int atomicShowCounter = 0;

    public PageViewZoomControls(Context context, final ZoomModel zoomModel)
    {
        super(context);
        hide();
        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(300);
        seekBar.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int i, boolean b)
            {
                zoomModel.setZoom(1.0f + i / 100.0f);
                bringUpZoomControls();
            }

            public void onStartTrackingTouch(SeekBar seekBar)
            {
            }

            public void onStopTrackingTouch(SeekBar seekBar)
            {
                zoomModel.commit();
            }
        });

        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.BOTTOM);
        final ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_menu_zoom);
        linearLayout.addView(imageView);
        linearLayout.addView(seekBar);
        addView(linearLayout);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev)
    {
        final boolean inControlsArea = ev.getX() > getLeft() && ev.getX() < getRight() &&
                ev.getY() > getTop() && ev.getY() < getBottom();
        if (inControlsArea)
        {
            bringUpZoomControls();
        }
        return !inControlsArea;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        return false;
    }

    public void bringUpZoomControls()
    {
        if (atomicShowCounter == 0)
        {
            show();
        }
        final int currentCounter = ++atomicShowCounter;
        postDelayed(new Runnable()
        {
            public void run()
            {
                if (currentCounter != atomicShowCounter)
                {
                    return;
                }
                hide();
                atomicShowCounter = 0;
            }
        }, 2000);
    }

    private void show()
    {
        fade(View.VISIBLE, 0.0f, 1.0f);
    }

    private void hide()
    {
        fade(View.GONE, 1.0f, 0.0f);
    }

    private void fade(int visibility, float startAlpha, float endAlpha)
    {
        AlphaAnimation anim = new AlphaAnimation(startAlpha, endAlpha);
        anim.setDuration(500);
        startAnimation(anim);
        setVisibility(visibility);
    }
}
