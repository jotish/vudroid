package org.vudroid.core.views;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ToggleButton;
import android.widget.ZoomControls;
import org.vudroid.core.events.BringUpZoomControlsListener;
import org.vudroid.core.events.ZoomListener;
import org.vudroid.core.models.ZoomModel;

public class PageViewZoomControls extends ZoomControls implements BringUpZoomControlsListener, ZoomListener
{
    private int atomicShowCounter = 0;
    private final ZoomModel zoomModel;

    public PageViewZoomControls(Context context, final ZoomModel zoomModel)
    {
        super(context);
        hide();
        setOnZoomInClickListener(new OnClickListener()
        {
            public void onClick(View view)
            {
                zoomModel.increaseZoom();
            }
        });
        setOnZoomOutClickListener(new OnClickListener()
        {
            public void onClick(View view)
            {
                zoomModel.decreaseZoom();
            }
        });
        final ToggleButton button = new ToggleButton(context);
        button.setTextOn("Scroll On");
        final String s = "Scroll Off";
        button.setTextOff(s);
        button.setText(s);
        button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton compoundButton, boolean b)
            {
                zoomModel.setHorizontalScrollEnabled(b);
            }
        });
        addView(button, 0);
        this.zoomModel = zoomModel;
        setZoomEnabled();
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

    public void zoomChanged(float newZoom, float oldZoom)
    {
        setZoomEnabled();
    }

    private void setZoomEnabled()
    {
        setIsZoomOutEnabled(zoomModel.canDecrement());
    }
}
