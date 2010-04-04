package org.vudroid.core;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;
import org.vudroid.core.events.ZoomListener;
import org.vudroid.core.models.ZoomModel;

import java.util.HashMap;
import java.util.Map;

public class DocumentView extends View implements ZoomListener
{
    private final ZoomModel zoomModel;
    private DecodeService decodeService;
    private final HashMap<Integer, Page> pages = new HashMap<Integer, Page>();
    private boolean isInitialized = false;
    private int pageToGoTo;
    private float lastX;
    private float lastY;
    private VelocityTracker velocityTracker;
    private final Scroller scroller;

    public DocumentView(Context context, ZoomModel zoomModel)
    {
        super(context);
        this.zoomModel = zoomModel;
        setKeepScreenOn(true);
        scroller = new Scroller(getContext());
    }

    public void setDecodeService(DecodeService decodeService)
    {
        this.decodeService = decodeService;
    }

    private void init()
    {
        if (isInitialized)
        {
            return;
        }
        final int width = decodeService.getEffectivePagesWidth();
        final int height = decodeService.getEffectivePagesHeight();
        for (int i = 0; i < decodeService.getPageCount(); i++)
        {
            addPageToMainLayoutIfNotAvailable(width, height, i);
        }
        invalidatePageSizes();
        goToPageImpl(pageToGoTo);
        isInitialized = true;
    }

    private void addPageToMainLayoutIfNotAvailable(int width, int height, int pageIndex)
    {
        if (pages.containsKey(pageIndex))
        {
            return;
        }
        pages.put(pageIndex, new Page(pageIndex));
        setAspectRatio(width, height, pageIndex);
    }

    private void setAspectRatio(int width, int height, int pageIndex)
    {
        pages.get(pageIndex).setAspectRatio(width * 1.0f / height);
    }

    private void goToPageImpl(final int toPage)
    {
        post(new Runnable()
        {
            public void run()
            {
                scrollTo(0, pages.get(toPage).getTop());
            }
        });
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt)
    {
        super.onScrollChanged(l, t, oldl, oldt);
        // on scrollChanged can be called from scrollTo just after new layout applied so we should wait for relayout
        post(new Runnable()
        {
            public void run()
            {
                updatePageVisibility();
            }
        });
    }

    private void updatePageVisibility()
    {
        stopDecodingInvisiblePages();
        removeImageFromInvisiblePages();
        startDecodingVisiblePages();
    }

    private void startDecodingVisiblePages()
    {
        startDecodingVisiblePages(false);
    }

    private void startDecodingVisiblePages(boolean invalidate)
    {
        for (final Map.Entry<Integer, Page> pageNumToPage : pages.entrySet())
        {
            final Page page = pageNumToPage.getValue();
            if (isPageVisible(page))
            {
                final Integer pageNum = pageNumToPage.getKey();
                if (page.getBitmap() != null && !invalidate)
                {
                    continue;
                }
                decodePage(pageNum);
            }
        }
    }

    private void removeImageFromInvisiblePages()
    {
        for (Integer visiblePageNum : pages.keySet())
        {
            if (!isPageVisible(pages.get(visiblePageNum)))
            {
                removeImageFromPage(visiblePageNum);
            }
        }
    }

    private void stopDecodingInvisiblePages()
    {
        for (Integer decodingPageNum : pages.keySet())
        {
            if (!isPageVisible(pages.get(decodingPageNum)) && pages.get(decodingPageNum).decodingNow)
            {
                stopDecodingPage(decodingPageNum);
            }
        }
    }

    private void stopDecodingAllPages()
    {
        for (Integer decodingPageNum : pages.keySet())
        {
            if (pages.get(decodingPageNum).decodingNow)
            {
                stopDecodingPage(decodingPageNum);
            }
        }
    }

    private void stopDecodingPage(Integer decodingPageNum)
    {
        decodeService.stopDecoding(decodingPageNum);
        removeDecodingStatus(decodingPageNum);
    }

    private void decodePage(final Integer pageNum)
    {
        if (pages.containsKey(pageNum) && pages.get(pageNum).decodingNow)
        {
            return;
        }
        addPageToMainLayoutIfNotAvailable(getWidth(), getHeight(), pageNum);
        setDecodingStatus(pageNum);
        decodeService.decodePage(pageNum, new DecodeService.DecodeCallback()
        {
            public void decodeComplete(final Bitmap bitmap)
            {
                post(new Runnable()
                {
                    public void run()
                    {
                        submitBitmap(pageNum, bitmap);
                    }
                });
            }
        }, zoomModel.getZoom());
    }

    private void setDecodingStatus(Integer pageNum)
    {
        if (!pages.get(pageNum).decodingNow && pages.containsKey(pageNum) && pages.get(pageNum).getBitmap() == null)
        {
            pages.get(pageNum).decodingNow = true;
        }
    }

    private void removeDecodingStatus(Integer decodingPageNum)
    {
        if (pages.get(decodingPageNum).decodingNow && pages.containsKey(decodingPageNum))
        {
            pages.get(decodingPageNum).decodingNow = false;
        }
    }

    private boolean isPageVisible(Page page)
    {
        float pageTop = page.getTop();
        float pageBottom = page.bounds.bottom;
        int top = getScrollY();
        int bottom = top + getHeight();

        return top <= pageTop && pageTop <= bottom ||
                top <= pageBottom && pageBottom <= bottom ||
                top <= pageTop && pageBottom <= bottom ||
                pageTop <= top && bottom <= pageBottom;
    }

    private void submitBitmap(final Integer pageNum, final Bitmap bitmap)
    {
        addImageToPage(pageNum, bitmap);
        removeDecodingStatus(pageNum);
    }

    private void addImageToPage(Integer pageNum, final Bitmap bitmap)
    {
        init();
        final Page page = pages.get(pageNum);
        page.setBitmap(bitmap);
        setPageSize(pageNum, bitmap);
    }

    private void setPageSize(Integer pageNum, Bitmap bitmap)
    {
        setAspectRatio(bitmap.getWidth(), bitmap.getHeight(), pageNum);
    }

    private void removeImageFromPage(Integer fromPage)
    {
        pages.get(fromPage).setBitmap(null);
    }

    public void showDocument()
    {
        // use post to ensure that document view has width and height before decoding begin
        post(new Runnable()
        {
            public void run()
            {
                decodePage(0);
            }
        });
    }

    public void goToPage(int toPage)
    {
        if (isInitialized)
        {
            goToPageImpl(toPage);
        } else
        {
            pageToGoTo = toPage;
        }
    }

    public int getCurrentPage()
    {
        for (Map.Entry<Integer, Page> entry : pages.entrySet())
        {
            if (isPageVisible(entry.getValue()))
            {
                return entry.getKey();
            }
        }
        return 0;
    }

    public void zoomChanged(float newZoom, float oldZoom)
    {
        stopDecodingAllPages();
        startDecodingVisiblePages(true);
        final float ratio = newZoom / oldZoom;
        scrollTo((int) ((getScrollX() + getWidth() / 2) * ratio - getWidth()/2), (int) ((getScrollY()+getHeight()/2) * ratio - getHeight()/2));
        invalidatePageSizes();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        zoomModel.bringUpZoomControls();

        if (velocityTracker == null)
        {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        switch (ev.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished())
                {
                    scroller.abortAnimation();
                }
                lastX = ev.getX();
                lastY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                scrollBy((int) (lastX - ev.getX()), (int) (lastY - ev.getY()));
                lastX = ev.getX();
                lastY = ev.getY();
                break;
            case MotionEvent.ACTION_UP:
                velocityTracker.computeCurrentVelocity(1000);
                scroller.fling(getScrollX(), getScrollY(), (int) -velocityTracker.getXVelocity(), (int) -velocityTracker.getYVelocity(), 0, (int) (getWidth() * zoomModel.getZoom()), 0, (int) pages.get(pages.size()-1).bounds.bottom);
                velocityTracker.recycle();
                velocityTracker = null;
                break;
        }
        return true;
    }

    @Override
    public void computeScroll()
    {
        if (scroller.computeScrollOffset())
        {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        for (Page page : pages.values())
        {
            page.draw(canvas);
        }
    }

    private void invalidatePageSizes()
    {
        float heightAccum = 0;
        int width = getWidth();
        float zoom = zoomModel.getZoom();
        for (int i = 0; i < pages.size(); i++)
        {
            Page page = pages.get(i);
            float pageHeight = page.getPageHeight(width, zoom);
            page.bounds = new RectF(0, heightAccum, width * zoom, heightAccum + pageHeight);
            heightAccum += pageHeight;
        }
    }

    private class Page
    {
        private final int index;
        private RectF bounds;

        private Page(int index)
        {
            this.index = index;
        }

        private float aspectRatio;
        private Bitmap bitmap;
        private boolean decodingNow;

        private float getPageHeight(int mainWidth, float zoom)
        {
            return mainWidth / getAspectRatio() * zoom;
        }

        public int getTop()
        {
            return Math.round(bounds.top);
        }

        public void draw(Canvas canvas)
        {
            if (getBitmap() == null)
            {
                return;
            }
            canvas.drawBitmap(getBitmap(), new Rect(0, 0, getBitmap().getWidth(), getBitmap().getHeight()), bounds, new Paint());
        }

        public Bitmap getBitmap()
        {
            return bitmap;
        }

        public void setBitmap(Bitmap bitmap)
        {
            if (this.bitmap != null)
            {
                this.bitmap.recycle();
            }
            this.bitmap = bitmap;
            invalidate();
        }

        public float getAspectRatio()
        {
            return aspectRatio;
        }

        public void setAspectRatio(float aspectRatio)
        {
            this.aspectRatio = aspectRatio;
            invalidatePageSizes();
        }
    }
}
