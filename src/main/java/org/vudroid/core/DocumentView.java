package org.vudroid.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import org.vudroid.core.events.ZoomListener;
import org.vudroid.core.models.ZoomModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DocumentView extends ScrollView implements ZoomListener
{
    private final ZoomModel zoomModel;
    private DecodeService decodeService;
    private final Map<Integer, FrameLayout> pages = new HashMap<Integer, FrameLayout>();
    private final Map<Integer, Bitmap> visiblePageNumToBitmap = new HashMap<Integer, Bitmap>();
    private final Set<Integer> decodingPageNums = new HashSet<Integer>();
    private boolean isInitialized = false;
    private int pageToGoTo;
    private float lastX;
    private VelocityTracker velocityTracker;
    private final Scroller scroller;
    private final HashMap<Integer, Float> pageIndexToAspectRatio = new HashMap<Integer, Float>();
    private Animation.AnimationListener animationListener;
    private final Rect tempRect = new Rect();
    private final HashMap<Integer,Bitmap> pendingBitmaps = new HashMap<Integer, Bitmap>();

    public DocumentView(Context context, ZoomModel zoomModel)
    {
        super(context);
        this.zoomModel = zoomModel;
        initLayout();
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
        final LinearLayout linearLayout = getMainLayout();
        final int width = decodeService.getEffectivePagesWidth();
        final int height = decodeService.getEffectivePagesHeight();
        for (int i = 0; i < decodeService.getPageCount(); i++)
        {
            addPageToMainLayoutIfNotAvailable(linearLayout, width, height, i);
        }
        goToPageImpl(pageToGoTo);
        isInitialized = true;
    }

    private void addPageToMainLayoutIfNotAvailable(LinearLayout mainLayout, int width, int height, int pageIndex)
    {
        if (pages.containsKey(pageIndex))
        {
            return;
        }
        final FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setLayoutParams(new LayoutParams(width, height));
        setAspectRatio(width, height, pageIndex);
        frameLayout.addView(createPageNumView(pageIndex));
        pages.put(pageIndex, frameLayout);
        mainLayout.addView(frameLayout);
    }

    private void setAspectRatio(int width, int height, int pageIndex)
    {
        pageIndexToAspectRatio.put(pageIndex, width * 1.0f / height);
    }

    private LinearLayout getMainLayout()
    {
        return (LinearLayout) findViewWithTag(LinearLayout.class);
    }

    private LinearLayout initLayout()
    {
        final LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setTag(LinearLayout.class);
        linearLayout.setAnimationCacheEnabled(false);
        addView(linearLayout);
        return linearLayout;
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
        for (final Map.Entry<Integer, FrameLayout> pageNumToPage : pages.entrySet())
        {
            final FrameLayout page = pageNumToPage.getValue();
            if (isPageVisible(page))
            {
                final Integer pageNum = pageNumToPage.getKey();
                if (visiblePageNumToBitmap.containsKey(pageNum) && !invalidate)
                {
                    continue;
                }
                decodePage(pageNum);
            }
        }
    }

    private void removeImageFromInvisiblePages()
    {
        for (Integer visiblePageNum : new HashMap<Integer, Bitmap>(visiblePageNumToBitmap).keySet())
        {
            if (!isPageVisible(pages.get(visiblePageNum)))
            {
                removeImageFromPage(visiblePageNum);
            }
        }
    }

    private void stopDecodingInvisiblePages()
    {
        for (Integer decodingPageNum : new HashSet<Integer>(decodingPageNums))
        {
            if (!isPageVisible(pages.get(decodingPageNum)))
            {
                stopDecodingPage(decodingPageNum);
            }
        }
    }

    private void stopDecodingAllPages()
    {
        for (Integer decodingPageNum : new HashSet<Integer>(decodingPageNums))
        {
            stopDecodingPage(decodingPageNum);
        }
    }

    private void stopDecodingPage(Integer decodingPageNum)
    {
        decodeService.stopDecoding(decodingPageNum);
        removeDecodingStatus(decodingPageNum);
    }

    private void decodePage(final Integer pageNum)
    {
        if (decodingPageNums.contains(pageNum))
        {
            return;
        }
        addPageToMainLayoutIfNotAvailable(getMainLayout(), getWidth(), getHeight(), pageNum);
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
        if (!decodingPageNums.contains(pageNum) && pages.containsKey(pageNum) && !visiblePageNumToBitmap.containsKey(pageNum))
        {
            final ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            bar.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
            bar.setTag(ProgressBar.class);
            pages.get(pageNum).addView(bar);
        }
        decodingPageNums.add(pageNum);
    }

    private void removeDecodingStatus(Integer decodingPageNum)
    {
        if (decodingPageNums.contains(decodingPageNum) && pages.containsKey(decodingPageNum))
        {
            final FrameLayout page = pages.get(decodingPageNum);
            page.removeView(page.findViewWithTag(ProgressBar.class));
        }
        decodingPageNums.remove(decodingPageNum);
    }

    private boolean isPageVisible(FrameLayout page)
    {
        return page.getGlobalVisibleRect(tempRect);
    }

    private void submitBitmap(final Integer pageNum, final Bitmap bitmap)
    {
        if (isAnimationRunning())
        {
            final Bitmap oldBitmap = pendingBitmaps.put(pageNum, bitmap);
            if (oldBitmap != null)
            {
                oldBitmap.recycle();
            }
            return;
        }
        addImageToPage(pageNum, bitmap);
        removeDecodingStatus(pageNum);
    }

    private void submitPendingBitmaps()
    {
        for (Map.Entry<Integer, Bitmap> pageBitmapEntry : pendingBitmaps.entrySet())
        {
            submitBitmap(pageBitmapEntry.getKey(), pageBitmapEntry.getValue());
        }
        pendingBitmaps.clear();
    }

    private boolean isAnimationRunning()
    {
        return getMainLayout().getAnimation() != null;
    }

    private void addImageToPage(Integer pageNum, final Bitmap bitmap)
    {
        init();
        final FrameLayout page = pages.get(pageNum);
        ImageView imageView = (ImageView) page.findViewWithTag(ImageView.class);
        if (imageView == null)
        {
            imageView = createImageView(bitmap);
            page.addView(imageView);
        }
        else
        {
            imageView.setImageBitmap(bitmap);
        }
        setPageSize(pageNum, bitmap);
        final Bitmap oldBitmap = visiblePageNumToBitmap.put(pageNum, bitmap);
        if (oldBitmap != null)
        {
            oldBitmap.recycle();
        }
    }

    private void setPageSize(Integer pageNum, Bitmap bitmap)
    {
        setAspectRatio(bitmap.getWidth(), bitmap.getHeight(), pageNum);
        if (getMainLayout().getAnimation() == null)
        {
            setPageSizeByAspectRatio(pageNum);
        }
    }

    private void setPageSizeByAspectRatio(Integer pageNum)
    {
        setPageSizeByAspectRatio(getWidth(), pages.get(pageNum), pageNum, zoomModel.getZoom(), null, 0);
    }

    private void removeImageFromPage(Integer fromPage)
    {
        final FrameLayout page = pages.get(fromPage);
        final View imageView = page.findViewWithTag(ImageView.class);
        if (imageView == null)
        {
            return;
        }
        page.removeView(imageView);
        final Bitmap bitmap = visiblePageNumToBitmap.remove(fromPage);
        bitmap.recycle();
    }

    private ImageView createImageView(Bitmap bitmap)
    {
        final ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(bitmap);
        imageView.setTag(ImageView.class);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        return imageView;
    }

    private TextView createPageNumView(int i)
    {
        TextView pageNumTextView = new TextView(getContext());
        pageNumTextView.setText("Page " + (i + 1));
        pageNumTextView.setTextSize(32);
        pageNumTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        pageNumTextView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        return pageNumTextView;
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
        }
        else
        {
            pageToGoTo = toPage;
        }
    }

    public int getCurrentPage()
    {
        for (Map.Entry<Integer, FrameLayout> entry : pages.entrySet())
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
        applyScaleAnimation(newZoom, oldZoom);
    }

    private void applyScaleAnimation(final float newZoom, final float oldZoom)
    {
        if (isAnimationRunning())
        {
            animationListener.onAnimationEnd(getAnimation());
        }
        final float ratio = newZoom / oldZoom;
        final ScaleAnimation animation = new ScaleAnimation(1.0f, ratio, 1.0f, ratio, getScrollX() + getWidth()/2, getScrollY() + getHeight()/2);

        animation.setDuration(150);
        animation.setFillAfter(true);
        animationListener = new Animation.AnimationListener()
        {
            public void onAnimationEnd(Animation animation)
            {
                removeAnimation();
                submitPendingBitmaps();
                final int width = getWidth();
                final int currentPage = getCurrentPage();
                HeightAccum heightAccum = new HeightAccum();
                for (Map.Entry<Integer, FrameLayout> pageIndexToPage : pages.entrySet())
                {
                    final FrameLayout page = pageIndexToPage.getValue();
                    final Integer pageIndex = pageIndexToPage.getKey();
                    setPageSizeByAspectRatio(width, page, pageIndex, newZoom, heightAccum, currentPage);
                }
                lastUpdateScrollByZoom = new UpdateScrollByZoom(newZoom, oldZoom, heightAccum, getScrollY());
            }

            public void onAnimationStart(Animation animation)
            {
            }

            public void onAnimationRepeat(Animation animation)
            {
            }
        };
        animation.setAnimationListener(animationListener);
        getMainLayout().startAnimation(animation);
    }

    private class HeightAccum
    {
        private int currentPageHeight;
        private int newPageHeight;
    }

    private class UpdateScrollByZoom
    {
        private final float newZoom;
        private final float oldZoom;
        private final HeightAccum heightAccum;
        private final float currentScrollY;

        private UpdateScrollByZoom(float newZoom, float oldZoom, HeightAccum heightAccum, float currentScrollY)
        {
            this.newZoom = newZoom;
            this.oldZoom = oldZoom;
            this.heightAccum = heightAccum;
            this.currentScrollY = currentScrollY;
        }
    }

    private UpdateScrollByZoom lastUpdateScrollByZoom;

    private void setPageSizeByAspectRatio(int mainWidth, FrameLayout page, Integer pageIndex, float zoom, HeightAccum heightAccum, int currentPage)
    {
        final int newHeight = Math.round(mainWidth / pageIndexToAspectRatio.get(pageIndex) * zoom);
        final int height = page.getHeight();
        page.setLayoutParams(new LinearLayout.LayoutParams(
                Math.round(mainWidth * zoom),
                newHeight
        ));
        if (heightAccum != null && currentPage > pageIndex)
        {
            heightAccum.currentPageHeight += height;
            heightAccum.newPageHeight += newHeight;
        }
    }

    private void removeAnimation()
    {
        animationListener = null;
        getMainLayout().clearAnimation();
    }

    private void updateScrollWhileZoom(float newZoom, float oldZoom, HeightAccum heightAccum, float currentScrollY)
    {                                       
        final float ratio = newZoom / oldZoom;
        final float halfWidth = getWidth() / 2.0f;
        final float halfHeight = getHeight() / 2.0f;
        scrollTo(Math.round(ratio * (getScrollX() + halfWidth) - halfWidth),
                Math.round(ratio * (currentScrollY + halfHeight - heightAccum.currentPageHeight) - halfHeight + heightAccum.newPageHeight));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev)
    {
        zoomModel.bringUpZoomControls();

        final boolean b = super.onTouchEvent(ev);
        if (!zoomModel.isHorizontalScrollEnabled())
        {
            return b;
        }

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
                break;
            case MotionEvent.ACTION_MOVE:
                final int delta = (int) (lastX - ev.getX());
                scrollBy(delta, 0);
                lastX = ev.getX();
                break;
            case MotionEvent.ACTION_UP:
                velocityTracker.computeCurrentVelocity(1000);
                scroller.fling(getScrollX(), 0, (int) -velocityTracker.getXVelocity(), 0, 0, getMainLayout().getWidth(), 0, 0);
                velocityTracker.recycle();
                velocityTracker = null;
                break;
        }
        return true;
    }

    @Override
    public void computeScroll()
    {
        // save scrollX as it killed by scroller inside ScrollView.computeScroll()
        final int scrollX = getScrollX();
        super.computeScroll();
        if (scroller.computeScrollOffset())
        {
            scrollTo(scroller.getCurrX(), getScrollY());
        }
        else
        {
            scrollTo(scrollX, getScrollY());
        }
        if (lastUpdateScrollByZoom != null)
        {
            updateScrollWhileZoom(lastUpdateScrollByZoom.newZoom, lastUpdateScrollByZoom.oldZoom, lastUpdateScrollByZoom.heightAccum, lastUpdateScrollByZoom.currentScrollY);
            lastUpdateScrollByZoom = null;
        }
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec)
    {
        super.measureChild(child, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), parentHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed)
    {
        super.measureChildWithMargins(child, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), widthUsed, parentHeightMeasureSpec, heightUsed);
    }
}
