package org.vudroid.core;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;
import org.vudroid.core.events.ZoomListener;
import org.vudroid.core.models.DecodingProgressModel;
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
    private DecodingProgressModel progressModel;
    private float firstX;
    private float firstY;

    public DocumentView(Context context, final ZoomModel zoomModel, DecodingProgressModel progressModel)
    {
        super(context);
        this.zoomModel = zoomModel;
        this.progressModel = progressModel;
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
            pages.put(i, new Page(i));
            pages.get(i).setAspectRatio(width, height);
        }
        invalidatePageSizes();
        goToPageImpl(pageToGoTo);
        isInitialized = true;
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
            if (page.isVisible())
            {
                page.startDecodingVisibleNodes(invalidate);
            }
        }
    }

    private void removeImageFromInvisiblePages()
    {
        for (Integer visiblePageNum : pages.keySet())
        {
            if (!pages.get(visiblePageNum).isVisible())
            {
                pages.get(visiblePageNum).removeInvisibleBitmaps();
            }
        }
    }

    private void stopDecodingInvisiblePages()
    {
        for (Integer decodingPageNum : pages.keySet())
        {
            if (!pages.get(decodingPageNum).isVisible() && pages.get(decodingPageNum).isDecodingNow())
            {
                stopDecodingPage(decodingPageNum);
            }
        }
    }

    private void stopDecodingAllPages()
    {
        for (Integer decodingPageNum : pages.keySet())
        {
            if (pages.get(decodingPageNum).isDecodingNow())
            {
                stopDecodingPage(decodingPageNum);
            }
        }
    }

    private void stopDecodingPage(Integer decodingPageNum)
    {
        pages.get(decodingPageNum).stopDecoding();
    }

    public void showDocument()
    {
        // use post to ensure that document view has width and height before decoding begin
        post(new Runnable()
        {
            public void run()
            {
                init();
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
            if (entry.getValue().isVisible())
            {
                return entry.getKey();
            }
        }
        return 0;
    }

    public void zoomChanged(float newZoom, float oldZoom)
    {
        final float ratio = newZoom / oldZoom;
        scrollTo((int) ((getScrollX() + getWidth() / 2) * ratio - getWidth() / 2), (int) ((getScrollY() + getHeight() / 2) * ratio - getHeight() / 2));
        invalidatePageSizes();
        invalidate();
    }

    public void commitZoom()
    {
        stopDecodingAllPages();
        startDecodingVisiblePages(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        super.onTouchEvent(ev);

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
                firstX = ev.getX();
                firstY = ev.getY();
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
                scroller.fling(getScrollX(), getScrollY(), (int) -velocityTracker.getXVelocity(), (int) -velocityTracker.getYVelocity(), getLeftLimit(), getRightLimit(), getTopLimit(), getBottomLimit());
                velocityTracker.recycle();
                velocityTracker = null;

                if (Math.abs(firstX - ev.getX()) < 2 && Math.abs(firstY - ev.getY()) < 2)
                {
                    zoomModel.bringUpZoomControls();
                }
                break;
        }
        return true;
    }

    private int getTopLimit()
    {
        return 0;
    }

    private int getLeftLimit()
    {
        return 0;
    }

    private int getBottomLimit()
    {
        return (int) pages.get(pages.size() - 1).bounds.bottom - getHeight();
    }

    private int getRightLimit()
    {
        return (int) (getWidth() * zoomModel.getZoom()) - getWidth();
    }

    @Override
    public void scrollTo(int x, int y)
    {
        super.scrollTo(Math.min(Math.max(x, getLeftLimit()), getRightLimit()), Math.min(Math.max(y, getTopLimit()), getBottomLimit()));
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
        private PageTreeNode[] nodes;

        private Page(int index)
        {
            this.index = index;
            nodes = new PageTreeNode[] {
                    new PageTreeNode(new RectF(0,0,0.5f,0.5f), this),
                    new PageTreeNode(new RectF(0.5f,0,1.0f,0.5f), this),
                    new PageTreeNode(new RectF(0,0.5f,0.5f,1.0f), this),
                    new PageTreeNode(new RectF(0.5f,0.5f,1.0f,1.0f), this)
            };
        }

        private float aspectRatio;

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
            if (!isVisible())
            {
                return;
            }
            final Paint rectPaint = new Paint();
            rectPaint.setColor(Color.GRAY);
            rectPaint.setStrokeWidth(4);
            rectPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(bounds, rectPaint);
            rectPaint.setColor(Color.BLACK);
            rectPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(bounds, rectPaint);

            final TextPaint paint = new TextPaint();
            paint.setColor(Color.BLACK);
            paint.setAntiAlias(true);
            paint.setTextSize(24);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Page " + (index + 1), bounds.centerX(), bounds.centerY(), paint);
            for (PageTreeNode node : nodes)
            {
                node.draw(canvas);
            }
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

        public boolean isVisible()
        {
            float pageTop = getTop();
            float pageBottom = bounds.bottom;
            int top = getScrollY();
            int bottom = top + getHeight();

            return top <= pageTop && pageTop <= bottom ||
                    top <= pageBottom && pageBottom <= bottom ||
                    top <= pageTop && pageBottom <= bottom ||
                    pageTop <= top && bottom <= pageBottom;
        }



        public void setAspectRatio(int width, int height)
        {
            setAspectRatio(width * 1.0f / height);
        }

        public void setPageSizeByBitmap(Bitmap bitmap)
        {
            setAspectRatio(bitmap.getWidth(), bitmap.getHeight());
        }

        public void removeInvisibleBitmaps()
        {
            for (PageTreeNode node : nodes)
            {
                node.setBitmap(null);
            }
        }

        private void startDecodingVisibleNodes(boolean invalidate)
        {
            for (PageTreeNode node : nodes)
            {
                if (node.getBitmap() != null && !invalidate)
                {
                    return;
                }
                node.decodePageTreeNode();
            }
        }

        public boolean isDecodingNow()
        {
            for (PageTreeNode node : nodes)
            {
                if (node.isDecodingNow())
                {
                    return true;
                }
            }
            return false;
        }

        private void stopDecoding()
        {
            for (PageTreeNode node : nodes)
            {
                node.stopDecoding();
            }
        }
    }

    private class PageTreeNode
    {
        private Bitmap bitmap;
        private boolean decodingNow;
        private final RectF pageSliceBounds;
        private final Page page;

        private PageTreeNode(RectF pageSliceBounds, Page page)
        {
            this.pageSliceBounds = pageSliceBounds;
            this.page = page;
        }

        public void setBitmap(Bitmap bitmap)
        {
            if (this.bitmap != null)
            {
                this.bitmap.recycle();
            }
            this.bitmap = bitmap;
//            if (bitmap != null)
//            {
//                setPageSizeByBitmap(bitmap);
//            } //TODO
            postInvalidate();
        }

        public Bitmap getBitmap()
        {
            return bitmap;
        }

        public boolean isDecodingNow()
        {
            return decodingNow;
        }

        public void setDecodingNow(boolean decodingNow)
        {
            if (this.decodingNow != decodingNow)
            {
                this.decodingNow = decodingNow;
                if (decodingNow)
                {
                    progressModel.increase();
                } else
                {
                    progressModel.decrease();
                }
            }
        }

        private void decodePageTreeNode()
        {
            if (isDecodingNow())
            {
                return;
            }
            setDecodingNow(true);
            decodeService.decodePage(this, page.index, new DecodeService.DecodeCallback()
            {
                public void decodeComplete(final Bitmap bitmap)
                {
                    post(new Runnable()
                    {
                        public void run()
                        {
                            setBitmap(bitmap);
                            setDecodingNow(false);
                        }
                    });
                }
            }, zoomModel.getZoom(), pageSliceBounds);
        }

        public void draw(Canvas canvas)
        {
            if (getBitmap() == null)
            {
                return;
            }
            final Matrix matrix = new Matrix();
            matrix.postScale(page.bounds.width(), page.bounds.height());
            matrix.postTranslate(page.bounds.left, page.bounds.top);
            final RectF targetRect = new RectF();
            matrix.mapRect(targetRect, pageSliceBounds);
            canvas.drawBitmap(getBitmap(), new Rect(0, 0, getBitmap().getWidth(), getBitmap().getHeight()), targetRect, new Paint(Paint.FILTER_BITMAP_FLAG));    
        }

        private void stopDecoding()
        {
            decodeService.stopDecoding(this);
            setDecodingNow(false);
        }
    }
}
