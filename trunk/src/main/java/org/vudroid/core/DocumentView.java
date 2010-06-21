package org.vudroid.core;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;
import org.vudroid.core.events.ZoomListener;
import org.vudroid.core.models.CurrentPageModel;
import org.vudroid.core.models.DecodingProgressModel;
import org.vudroid.core.models.ZoomModel;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public class DocumentView extends View implements ZoomListener
{
    private final ZoomModel zoomModel;
    private final CurrentPageModel currentPageModel;
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
    private RectF viewRect;
    private boolean inZoom;

    public DocumentView(Context context, final ZoomModel zoomModel, DecodingProgressModel progressModel, CurrentPageModel currentPageModel)
    {
        super(context);
        this.zoomModel = zoomModel;
        this.progressModel = progressModel;
        this.currentPageModel = currentPageModel;
        setKeepScreenOn(true);
        scroller = new Scroller(getContext());
        setFocusable(true);
        setFocusableInTouchMode(true);
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
        isInitialized = true;
        invalidatePageSizes();
        goToPageImpl(pageToGoTo);
    }

    private void goToPageImpl(final int toPage)
    {
        scrollTo(0, pages.get(toPage).getTop());        
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt)
    {
        super.onScrollChanged(l, t, oldl, oldt);
        currentPageModel.setCurrentPageIndex(getCurrentPage());
        if (inZoom)
        {
            return;
        }
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
            pageNumToPage.getValue().startDecodingVisibleNodes(invalidate);
        }
    }

    private void removeImageFromInvisiblePages()
    {
        for (Integer visiblePageNum : pages.keySet())
        {
            pages.get(visiblePageNum).removeInvisibleBitmaps();
        }
    }

    private void stopDecodingInvisiblePages()
    {
        for (Integer decodingPageNum : pages.keySet())
        {
            pages.get(decodingPageNum).stopDecodingInvisibleNodes();
        }
    }

    private void stopDecodingAllPages()
    {
        for (Integer decodingPageNum : pages.keySet())
        {
            pages.get(decodingPageNum).stopDecoding();
        }
    }

    public void showDocument()
    {
        // use post to ensure that document view has width and height before decoding begin
        post(new Runnable()
        {
            public void run()
            {
                init();
                updatePageVisibility();
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
        inZoom = true;
        stopScroller();
        final float ratio = newZoom / oldZoom;
        scrollTo((int) ((getScrollX() + getWidth() / 2) * ratio - getWidth() / 2), (int) ((getScrollY() + getHeight() / 2) * ratio - getHeight() / 2));
        invalidatePageSizes();
        postInvalidate();
    }

    public void commitZoom()
    {
        stopDecodingAllPages();
        removeImageFromInvisiblePages();
        startDecodingVisiblePages(true);
        inZoom = false;
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
                stopScroller();
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            switch (event.getKeyCode())
            {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    lineByLineMoveTo(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    lineByLineMoveTo(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    verticalDpadScroll(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    verticalDpadScroll(-1);
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void verticalDpadScroll(int direction)
    {
        scroller.startScroll(getScrollX(), getScrollY(), 0, direction * getHeight()/2);
        invalidate();
    }

    private void lineByLineMoveTo(int direction)
    {
        if (direction == 1 ? getScrollX() == getRightLimit() : getScrollX() == getLeftLimit())
        {
            scroller.startScroll(getScrollX(), getScrollY(), direction * (getLeftLimit()-getRightLimit()), (int) (direction * pages.get(getCurrentPage()).bounds.height()/50));
        }
        else
        {
            scroller.startScroll(getScrollX(), getScrollY(), direction * getWidth() / 2, 0);
        }
        invalidate();
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
        viewRect = null;
    }

    private RectF getViewRect()
    {
        if (viewRect == null)
        {
            viewRect = new RectF(getScrollX(), getScrollY(), getScrollX() + getWidth(), getScrollY() + getHeight());
        }
        return viewRect;
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    {
        super.onLayout(changed, left, top, right, bottom);
        invalidateScroll();
        invalidatePageSizes();
        commitZoom();
    }

    private void invalidatePageSizes()
    {
        if (!isInitialized)
        {
            return;
        }
        float heightAccum = 0;
        int width = getWidth();
        float zoom = zoomModel.getZoom();
        for (int i = 0; i < pages.size(); i++)
        {
            Page page = pages.get(i);
            float pageHeight = page.getPageHeight(width, zoom);
            page.setBounds(new RectF(0, heightAccum, width * zoom, heightAccum + pageHeight));
            heightAccum += pageHeight;
        }
    }

    private void invalidateScroll()
    {
        if (!isInitialized)
        {
            return;
        }
        stopScroller();
        final Page page = pages.get(0);
        if (page == null || page.bounds == null)
        {
            return;
        }
        final float v = zoomModel.getZoom();
        float ratio = getWidth() * v / page.bounds.width();
        scrollTo((int) (getScrollX() * ratio), (int) (getScrollY() * ratio));
    }

    private void stopScroller()
    {
        if (!scroller.isFinished())
        {
            scroller.abortAnimation();
        }
    }

    private class Page
    {
        private final int index;
        private RectF bounds;
        private PageTreeNode node;

        private Page(int index)
        {
            this.index = index;
            node = new PageTreeNode(new RectF(0, 0, 1, 1), this, 1, null);
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
            node.draw(canvas);
        }

        public float getAspectRatio()
        {
            return aspectRatio;
        }

        public void setAspectRatio(float aspectRatio)
        {
            if (this.aspectRatio != aspectRatio)
            {
                this.aspectRatio = aspectRatio;
                invalidatePageSizes();
            }
        }

        public boolean isVisible()
        {
            return RectF.intersects(getViewRect(), bounds);
        }

        public void setAspectRatio(int width, int height)
        {
            setAspectRatio(width * 1.0f / height);
        }

        public void removeInvisibleBitmaps()
        {
            node.removeInvisibleBitmaps();
        }

        private void startDecodingVisibleNodes(boolean invalidate)
        {
            node.startDecodingVisibleNodes(invalidate);
        }

        private void stopDecodingInvisibleNodes()
        {
            node.stopDecodingInvisibleNodes();
        }

        private void stopDecoding()
        {
            node.stopDecoding();
        }

        private void setBounds(RectF pageBounds)
        {
            bounds = pageBounds;
            node.invalidateNodeBounds();
        }

    }

    private class PageTreeNode
    {
        private Bitmap bitmap;
        private SoftReference<Bitmap> bitmapWeakReference;
        private boolean decodingNow;
        private final RectF pageSliceBounds;
        private final Page page;
        private RectF targetRect;
        private PageTreeNode[] children;
        private final float childrenZoomThreshold;
        private Matrix matrix = new Matrix();

        private PageTreeNode(RectF localPageSliceBounds, Page page, float childrenZoomThreshold, PageTreeNode parent)
        {
            this.pageSliceBounds = evaluatePageSliceBounds(localPageSliceBounds, parent);
            this.page = page;
            this.childrenZoomThreshold = childrenZoomThreshold;
        }

        private RectF evaluatePageSliceBounds(RectF localPageSliceBounds, PageTreeNode parent)
        {
            if (parent == null)
            {
                return localPageSliceBounds;
            }
            final Matrix matrix = new Matrix();
            matrix.postScale(parent.pageSliceBounds.width(), parent.pageSliceBounds.height());
            matrix.postTranslate(parent.pageSliceBounds.left, parent.pageSliceBounds.top);
            final RectF sliceBounds = new RectF();
            matrix.mapRect(sliceBounds, localPageSliceBounds);
            return sliceBounds;
        }

        public void setBitmap(Bitmap bitmap)
        {
            if (bitmap != null && bitmap.getWidth() == -1 && bitmap.getHeight() == -1)
            {
                return;
            }
            if (this.bitmap != bitmap)
            {
                if (bitmap != null)
                {
                    if (this.bitmap != null)
                    {
                        this.bitmap.recycle();
                    }
                    bitmapWeakReference = new SoftReference<Bitmap>(bitmap);
                    postInvalidate();
                }
                this.bitmap = bitmap;
            }
        }

        public Bitmap getBitmap()
        {
            return bitmapWeakReference != null ? bitmapWeakReference.get() : null;
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
                            page.setAspectRatio(decodeService.getPageWidth(page.index), decodeService.getPageHeight(page.index));
                            invalidateChildren();
                        }
                    });
                }
            }, zoomModel.getZoom(), pageSliceBounds);
        }

        public void draw(Canvas canvas)
        {
            if (getBitmap() != null)
            {
                canvas.drawBitmap(getBitmap(), new Rect(0, 0, getBitmap().getWidth(), getBitmap().getHeight()), getTargetRect(), new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            if (children == null)
            {
                return;
            }
            for (PageTreeNode child : children)
            {
                child.draw(canvas);
            }
        }

        private RectF getTargetRect()
        {
            if (targetRect == null)
            {
                matrix.reset();
                matrix.postScale(page.bounds.width(), page.bounds.height());
                matrix.postTranslate(page.bounds.left, page.bounds.top);
                targetRect = new RectF();
                matrix.mapRect(targetRect, pageSliceBounds);
            }
            return targetRect;
        }

        private void stopDecoding()
        {
            invalidateChildren();
            if (children != null)
            {
                for (PageTreeNode child : children)
                {
                    child.stopDecoding();
                }
            }
            stopDecodingThisNode();
        }

        private void stopDecodingThisNode()
        {
            if (!isDecodingNow())
            {
                return;
            }
            decodeService.stopDecoding(this);
            setDecodingNow(false);
        }

        private boolean isVisible()
        {
            return RectF.intersects(getViewRect(), getTargetRect());
        }

        private void removeInvisibleBitmaps()
        {
            invalidateChildren();
            if (children != null)
            {
                for (PageTreeNode child : children)
                {
                    child.removeInvisibleBitmaps();
                }
            }
            if (isVisibleAndNotHiddenByChildren())
            {
                return;
            }
            setBitmap(null);
        }

        private boolean isHiddenByChildren()
        {
            if (children == null)
            {
                return false;
            }
            for (PageTreeNode child : children)
            {
                if (child.getBitmap() == null)
                {
                    return false;
                }
            }
            return true;
        }

        private void startDecodingVisibleNodes(boolean invalidate)
        {
            if (!isVisible())
            {
                return;
            }
            invalidateChildren();
            if (thresholdHit())
            {
                for (PageTreeNode child : children)
                {
                    child.startDecodingVisibleNodes(invalidate);
                }
            } else
            {
                if (getBitmap() != null && !invalidate)
                {
                    restoreBitmapReference();
                    return;
                }
                decodePageTreeNode();
            }
        }

        private void restoreBitmapReference()
        {
            setBitmap(getBitmap());
        }

        private void invalidateChildren()
        {
            if (thresholdHit() && children == null && isVisible())
            {
                final float newThreshold = childrenZoomThreshold * 2;
                children = new PageTreeNode[]
                        {
                                new PageTreeNode(new RectF(0, 0, 0.5f, 0.5f), page, newThreshold, this),
                                new PageTreeNode(new RectF(0.5f, 0, 1.0f, 0.5f), page, newThreshold, this),
                                new PageTreeNode(new RectF(0, 0.5f, 0.5f, 1.0f), page, newThreshold, this),
                                new PageTreeNode(new RectF(0.5f, 0.5f, 1.0f, 1.0f), page, newThreshold, this)
                        };
            }
            if (!thresholdHit() && getBitmap() != null || !isVisible())
            {
                recycleChildren();
            }
        }

        private void recycleChildren()
        {
            if (children == null)
            {
                return;
            }
            for (PageTreeNode child : children)
            {
                child.recycle();
            }
            if (!childrenContainBitmaps())
            {
                children = null;
            }
        }

        private boolean containsBitmaps()
        {
            return getBitmap() != null || childrenContainBitmaps();
        }

        private boolean childrenContainBitmaps()
        {
            if (children == null)
            {
                return false;
            }
            for (PageTreeNode child : children)
            {
                if (child.containsBitmaps())
                {
                    return true;
                }
            }
            return false;
        }

        private boolean thresholdHit()
        {
            return zoomModel.getZoom() >= childrenZoomThreshold;
        }

        private void recycle()
        {
            stopDecodingThisNode();
            setBitmap(null);
            if (children != null)
            {
                for (PageTreeNode child : children)
                {
                    child.recycle();
                }
            }
        }

        public void stopDecodingInvisibleNodes()
        {
            invalidateChildren();
            if (children != null)
            {
                for (PageTreeNode child : children)
                {
                    child.stopDecodingInvisibleNodes();
                }
            }
            if (isVisibleAndNotHiddenByChildren())
            {
                return;
            }
            stopDecodingThisNode();
        }

        private boolean isVisibleAndNotHiddenByChildren()
        {
            return isVisible() && !isHiddenByChildren();
        }

        private void invalidateNodeBounds()
        {
            targetRect = null;
            if (children != null)
            {
                for (PageTreeNode child : children)
                {
                    child.invalidateNodeBounds();
                }
            }
        }
    }
}
