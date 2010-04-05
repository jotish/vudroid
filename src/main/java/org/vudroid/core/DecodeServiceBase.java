package org.vudroid.core;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import org.vudroid.core.codec.CodecContext;
import org.vudroid.core.codec.CodecDocument;
import org.vudroid.core.codec.CodecPage;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DecodeServiceBase implements DecodeService
{
    private final CodecContext codecContext;

    private View containerView;
    private CodecDocument document;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    public static final String DECODE_SERVICE = "ViewDroidDecodeService";
    private final Map<Integer, Future<?>> decodingFutures = new ConcurrentHashMap<Integer, Future<?>>();

    public DecodeServiceBase(CodecContext codecContext)
    {
        this.codecContext = codecContext;
    }

    public void setContentResolver(ContentResolver contentResolver)
    {
        codecContext.setContentResolver(contentResolver);
    }

    public void setContainerView(View containerView)
    {
        this.containerView = containerView;
    }

    public void open(Uri fileUri)
    {
        document = codecContext.openDocument(fileUri);
    }

    public void decodePage(int pageNum, final DecodeCallback decodeCallback, float zoom)
    {
        final DecodeTask decodeTask = new DecodeTask(pageNum, decodeCallback, zoom);
        synchronized (decodingFutures)
        {
            final Future<?> future = executorService.submit(new Runnable()
            {
                public void run()
                {
                    try
                    {
                        performDecode(decodeTask);
                    }
                    catch (IOException e)
                    {
                        Log.e(DECODE_SERVICE, "Decode fail", e);
                    }
                }
            });
            final Future<?> removed = decodingFutures.put(pageNum, future);
            if (removed != null)
            {
                removed.cancel(false);
            }
        }
    }

    public void stopDecoding(int pageNum)
    {
        final Future<?> future = decodingFutures.remove(pageNum);
        if (future != null)
        {
            future.cancel(false);
        }
    }

    private void performDecode(DecodeTask currentDecodeTask)
            throws IOException
    {
        if (isTaskDead(currentDecodeTask))
        {
            Log.d(DECODE_SERVICE, "Skipping decode task for page " + currentDecodeTask.pageNumber);
            return;
        }
        Log.d(DECODE_SERVICE, "Starting decode of page: " + currentDecodeTask.pageNumber);
        CodecPage vuPage = document.getPage(currentDecodeTask.pageNumber);
        preloadNextPage(currentDecodeTask.pageNumber);

        while (vuPage.isDecoding())
        {
            if (isTaskDead(currentDecodeTask))
            {
                break;
            }
            waitForDecode(vuPage);
        }
        if (isTaskDead(currentDecodeTask))
        {
            return;
        }
        Log.d(DECODE_SERVICE, "Start converting map to bitmap");
        float scale = calculateScale(vuPage) * currentDecodeTask.zoom;
        final Bitmap bitmap = vuPage.renderBitmap(getScaledWidth(vuPage, scale), getScaledHeight(vuPage, scale));
        Log.d(DECODE_SERVICE, "Converting map to bitmap finished");
        if (isTaskDead(currentDecodeTask))
        {
            bitmap.recycle();
            return;
        }
        finishDecoding(currentDecodeTask, bitmap);
    }

    private int getScaledHeight(CodecPage vuPage, float scale)
    {
        return (int) (scale * vuPage.getHeight());
    }

    private int getScaledWidth(CodecPage vuPage, float scale)
    {
        return (int) (scale * vuPage.getWidth());
    }

    private float calculateScale(CodecPage codecPage)
    {
        return 1.0f * getTargetWidth() / codecPage.getWidth();
    }

    private void finishDecoding(DecodeTask currentDecodeTask, Bitmap bitmap)
    {
        updateImage(currentDecodeTask, bitmap);
        stopDecoding(currentDecodeTask.pageNumber);
    }

    private void preloadNextPage(int pageNumber) throws IOException
    {
        final int nextPage = pageNumber + 1;
        if (nextPage >= getPageCount())
        {
            return;
        }
        document.getPage(nextPage);
    }

    private void waitForDecode(CodecPage vuPage)
    {
        vuPage.waitForDecode();
    }

    private int getTargetWidth()
    {
        return containerView.getWidth();
    }

    public int getEffectivePagesWidth()
    {
        final CodecPage page = document.getPage(0);
        return getScaledWidth(page, calculateScale(page));
    }

    public int getEffectivePagesHeight()
    {
        final CodecPage page = document.getPage(0);
        return getScaledHeight(page, calculateScale(page));
    }

    private void updateImage(final DecodeTask currentDecodeTask, Bitmap bitmap)
    {
        currentDecodeTask.decodeCallback.decodeComplete(bitmap);
    }

    private boolean isTaskDead(DecodeTask currentDecodeTask)
    {
        synchronized (decodingFutures)
        {
            return !decodingFutures.containsKey(currentDecodeTask.pageNumber);
        }
    }

    public int getPageCount()
    {
        return document.getPageCount();
    }

    private class DecodeTask
    {
        private final int pageNumber;
        private final float zoom;
        private final DecodeCallback decodeCallback;

        private DecodeTask(int pageNumber, DecodeCallback decodeCallback, float zoom)
        {
            this.pageNumber = pageNumber;
            this.decodeCallback = decodeCallback;
            this.zoom = zoom;
        }
    }

}
