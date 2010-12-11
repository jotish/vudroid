package org.vudroid.core;

import android.graphics.*;

import java.lang.ref.SoftReference;

class PageTreeNode {
    private Bitmap bitmap;
    private SoftReference<Bitmap> bitmapWeakReference;
    private boolean decodingNow;
    private final RectF pageSliceBounds;
    private final Page page;
    private RectF targetRect;
    private PageTreeNode[] children;
    private final float childrenZoomThreshold;
    private Matrix matrix = new Matrix();
    private final Paint bitmapPaint = new Paint();
    private DocumentView documentView;

    PageTreeNode(DocumentView documentView, RectF localPageSliceBounds, Page page, float childrenZoomThreshold, PageTreeNode parent) {
        this.documentView = documentView;
        this.pageSliceBounds = evaluatePageSliceBounds(localPageSliceBounds, parent);
        this.page = page;
        this.childrenZoomThreshold = childrenZoomThreshold;
    }

    void startDecodingVisibleNodes(boolean invalidate) {
        if (!isVisible()) {
            return;
        }
        invalidateChildren();
        if (thresholdHit()) {
            for (PageTreeNode child : children) {
                child.startDecodingVisibleNodes(invalidate);
            }
        } else {
            if (getBitmap() != null && !invalidate) {
                restoreBitmapReference();
                return;
            }
            decodePageTreeNode();
        }
    }

    void stopDecodingInvisibleNodes() {
        invalidateChildren();
        if (children != null) {
            for (PageTreeNode child : children) {
                child.stopDecodingInvisibleNodes();
            }
        }
        if (isVisibleAndNotHiddenByChildren()) {
            return;
        }
        stopDecodingThisNode();
    }

    void stopDecoding() {
        invalidateChildren();
        if (children != null) {
            for (PageTreeNode child : children) {
                child.stopDecoding();
            }
        }
        stopDecodingThisNode();
    }

    void invalidateNodeBounds() {
        targetRect = null;
        if (children != null) {
            for (PageTreeNode child : children) {
                child.invalidateNodeBounds();
            }
        }
    }

    void removeInvisibleBitmaps() {
        invalidateChildren();
        if (children != null) {
            for (PageTreeNode child : children) {
                child.removeInvisibleBitmaps();
            }
        }
        if (isVisibleAndNotHiddenByChildren()) {
            return;
        }
        setBitmap(null);
    }


    void draw(Canvas canvas) {
        if (getBitmap() != null) {
            canvas.drawBitmap(getBitmap(), new Rect(0, 0, getBitmap().getWidth(), getBitmap().getHeight()), getTargetRect(), bitmapPaint);
        }
        if (children == null) {
            return;
        }
        for (PageTreeNode child : children) {
            child.draw(canvas);
        }
    }

    private boolean isVisible() {
        return RectF.intersects(documentView.getViewRect(), getTargetRect());
    }

    private void invalidateChildren() {
        if (thresholdHit() && children == null && isVisible()) {
            final float newThreshold = childrenZoomThreshold * 2;
            children = new PageTreeNode[]
                    {
                            new PageTreeNode(documentView, new RectF(0, 0, 0.5f, 0.5f), page, newThreshold, this),
                            new PageTreeNode(documentView, new RectF(0.5f, 0, 1.0f, 0.5f), page, newThreshold, this),
                            new PageTreeNode(documentView, new RectF(0, 0.5f, 0.5f, 1.0f), page, newThreshold, this),
                            new PageTreeNode(documentView, new RectF(0.5f, 0.5f, 1.0f, 1.0f), page, newThreshold, this)
                    };
        }
        if (!thresholdHit() && getBitmap() != null || !isVisible()) {
            recycleChildren();
        }
    }

    private boolean thresholdHit() {
        return documentView.zoomModel.getZoom() >= childrenZoomThreshold;
    }

    public Bitmap getBitmap() {
        return bitmapWeakReference != null ? bitmapWeakReference.get() : null;
    }

    private void restoreBitmapReference() {
        setBitmap(getBitmap());
    }

    private void decodePageTreeNode() {
        if (isDecodingNow()) {
            return;
        }
        setDecodingNow(true);
        documentView.decodeService.decodePage(this, page.index, new DecodeService.DecodeCallback() {
            public void decodeComplete(final Bitmap bitmap) {
                // TODO: this code doesn't support concurrency
                setBitmap(bitmap);
                setDecodingNow(false);
                page.setAspectRatio(documentView.decodeService.getPageWidth(page.index), documentView.decodeService.getPageHeight(page.index));
                invalidateChildren();
            }
        }, documentView.zoomModel.getZoom(), pageSliceBounds);
    }

    private RectF evaluatePageSliceBounds(RectF localPageSliceBounds, PageTreeNode parent) {
        if (parent == null) {
            return localPageSliceBounds;
        }
        final Matrix matrix = new Matrix();
        matrix.postScale(parent.pageSliceBounds.width(), parent.pageSliceBounds.height());
        matrix.postTranslate(parent.pageSliceBounds.left, parent.pageSliceBounds.top);
        final RectF sliceBounds = new RectF();
        matrix.mapRect(sliceBounds, localPageSliceBounds);
        return sliceBounds;
    }

    private void setBitmap(Bitmap bitmap) {
        if (bitmap != null && bitmap.getWidth() == -1 && bitmap.getHeight() == -1) {
            return;
        }
        if (this.bitmap != bitmap) {
            if (bitmap != null) {
                if (this.bitmap != null) {
                    this.bitmap.recycle();
                }
                bitmapWeakReference = new SoftReference<Bitmap>(bitmap);
                documentView.postInvalidate();
            }
            this.bitmap = bitmap;
        }
    }

    private boolean isDecodingNow() {
        return decodingNow;
    }

    private void setDecodingNow(boolean decodingNow) {
        if (this.decodingNow != decodingNow) {
            this.decodingNow = decodingNow;
            if (decodingNow) {
                documentView.progressModel.increase();
            } else {
                documentView.progressModel.decrease();
            }
        }
    }

    private RectF getTargetRect() {
        if (targetRect == null) {
            matrix.reset();
            matrix.postScale(page.bounds.width(), page.bounds.height());
            matrix.postTranslate(page.bounds.left, page.bounds.top);
            targetRect = new RectF();
            matrix.mapRect(targetRect, pageSliceBounds);
        }
        return targetRect;
    }

    private void stopDecodingThisNode() {
        if (!isDecodingNow()) {
            return;
        }
        documentView.decodeService.stopDecoding(this);
        setDecodingNow(false);
    }

    private boolean isHiddenByChildren() {
        if (children == null) {
            return false;
        }
        for (PageTreeNode child : children) {
            if (child.getBitmap() == null) {
                return false;
            }
        }
        return true;
    }

    private void recycleChildren() {
        if (children == null) {
            return;
        }
        for (PageTreeNode child : children) {
            child.recycle();
        }
        if (!childrenContainBitmaps()) {
            children = null;
        }
    }

    private boolean containsBitmaps() {
        return getBitmap() != null || childrenContainBitmaps();
    }

    private boolean childrenContainBitmaps() {
        if (children == null) {
            return false;
        }
        for (PageTreeNode child : children) {
            if (child.containsBitmaps()) {
                return true;
            }
        }
        return false;
    }

    private void recycle() {
        stopDecodingThisNode();
        setBitmap(null);
        if (children != null) {
            for (PageTreeNode child : children) {
                child.recycle();
            }
        }
    }

    private boolean isVisibleAndNotHiddenByChildren() {
        return isVisible() && !isHiddenByChildren();
    }

}
