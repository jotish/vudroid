package org.vudroid.djvudroid.codec;

import android.graphics.Bitmap;
import android.graphics.RectF;
import org.vudroid.core.codec.CodecPage;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class DjvuPage implements CodecPage
{
    private long pageHandle;
    private final Object waitObject;

    DjvuPage(long pageHandle, Object waitObject)
    {
        this.pageHandle = pageHandle;
        this.waitObject = waitObject;
    }

    public boolean isDecoding()
    {
        return !isDecodingDone(pageHandle);
    }

    private static native int getWidth(long pageHandle);

    private static native int getHeight(long pageHandle);

    private static native boolean isDecodingDone(long pageHandle);

    private static native boolean renderPage(long pageHandle, int targetWidth, int targetHeight, float pageSliceX,
                                    float pageSliceY,
                                    float pageSliceWidth,
                                    float pageSliceHeight, Buffer buffer);

    private static native void free(long pageHandle);

    public void waitForDecode()
    {
        synchronized (waitObject)
        {
            try
            {
                waitObject.wait(200);
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public int getWidth()
    {
        for (;;)
        {
            synchronized (waitObject)
            {
                final int width = getWidth(pageHandle);
                if (width != 0)
                {
                    return width;
                }
                try
                {
                    waitObject.wait(200);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public int getHeight()
    {
        for (;;)
        {
            synchronized (waitObject)
            {
                final int height = getHeight(pageHandle);
                if (height != 0)
                {
                    return height;
                }
                try
                {
                    waitObject.wait(200);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public Bitmap renderBitmap(int width, int height, RectF pageSliceBounds)
    {
        final ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 2);
        renderPage(pageHandle, width, height, pageSliceBounds.left, pageSliceBounds.top, pageSliceBounds.width(), pageSliceBounds.height(), buffer);
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    @Override
    protected void finalize() throws Throwable
    {
        recycle();
        super.finalize();
    }

    public synchronized void recycle() {
        if (pageHandle == 0) {
            return;
        }
        free(pageHandle);
        pageHandle = 0;
    }
}
