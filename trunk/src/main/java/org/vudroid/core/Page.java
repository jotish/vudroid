package org.vudroid.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

class Page
{
    final int index;
    RectF bounds;
    private PageTreeNode node;
    private DocumentView documentView;

    Page(DocumentView documentView, int index)
    {
        this.documentView = documentView;
        this.index = index;
        node = new PageTreeNode(documentView, new RectF(0, 0, 1, 1), this, 1, null);
    }

    private float aspectRatio;

    float getPageHeight(int mainWidth, float zoom)
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
            documentView.invalidatePageSizes();
        }
    }

    public boolean isVisible()
    {
        return RectF.intersects(documentView.getViewRect(), bounds);
    }

    public void setAspectRatio(int width, int height)
    {
        setAspectRatio(width * 1.0f / height);
    }

    public void removeInvisibleBitmaps()
    {
        node.removeInvisibleBitmaps();
    }

    void startDecodingVisibleNodes(boolean invalidate)
    {
        node.startDecodingVisibleNodes(invalidate);
    }

    void stopDecodingInvisibleNodes()
    {
        node.stopDecodingInvisibleNodes();
    }

    void stopDecoding()
    {
        node.stopDecoding();
    }

    void setBounds(RectF pageBounds)
    {
        bounds = pageBounds;
        node.invalidateNodeBounds();
    }

}
