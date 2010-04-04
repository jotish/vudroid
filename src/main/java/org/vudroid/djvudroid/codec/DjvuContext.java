package org.vudroid.djvudroid.codec;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;
import org.vudroid.core.VuDroidLibraryLoader;
import org.vudroid.core.codec.CodecContext;
import org.vudroid.core.utils.MD5StringUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class DjvuContext implements Runnable, CodecContext
{
    static
    {
        VuDroidLibraryLoader.load();        
    }

    private final long contextHandle;
    private ContentResolver contentResolver;
    private static final int BUFFER_SIZE = 32768;
    private static final String DJVU_DROID_CODEC_LIBRARY = "DjvuDroidCodecLibrary";
    private final HashMap<String, Semaphore> urlToSemaphore = new HashMap<String, Semaphore>();
    private final Object waitObject = new Object();
    private final HashMap<String, Uri> hashToUri = new HashMap<String, Uri>();

    public DjvuContext()
    {
        this.contextHandle = create();
        new Thread(this).start();
    }

    public DjvuDocument openDocument(Uri uri)
    {
        final Semaphore semaphore = new Semaphore(0);
        final String uriHash = "hash://" + MD5StringUtil.md5StringFor(uri.toString());
        hashToUri.put(uriHash, uri);
        urlToSemaphore.put(uriHash, semaphore);
        return DjvuDocument.openDocument(uriHash, this, semaphore, waitObject);
    }

    long getContextHandle()
    {
        return contextHandle;
    }

    public void run()
    {
        for(;;)
        {
            try
            {
                handleMessage(contextHandle);
                synchronized (waitObject)
                {
                    waitObject.notifyAll();
                }
            }
            catch (Exception e)
            {
                Log.e(DJVU_DROID_CODEC_LIBRARY, "Codec error", e);
            }
        }
    }

    /**
     * Called from JNI
     * @param uriHash uriHash to load from
     * @param streamId inner stream id
     * @param docHandle document handle to submit data to
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private void handleNewStream(final String uriHash, final int streamId, final long docHandle)
    {
        final Uri uri = hashToUri.get(uriHash);
        Log.d(DJVU_DROID_CODEC_LIBRARY, "Starting data submit for: " + uriHash + "@" + uri);
        InputStream inputStream = null;
        try
        {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            inputStream = contentResolver.openInputStream(uri);
            if (inputStream instanceof FileInputStream)
            {
                fileStreamWrite(streamId, docHandle, buffer, (FileInputStream) inputStream);
            }
            else
            {
                genericStreamWrite(streamId, docHandle, inputStream, buffer);
            }
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (inputStream != null)
            {
                try
                {
                    streamClose(docHandle, streamId, false);
                    inputStream.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        Log.d(DJVU_DROID_CODEC_LIBRARY, "Data submit finished for: " + uriHash + "@" + uri);
        urlToSemaphore.remove(uriHash).release();
    }

    private void fileStreamWrite(int streamId, long docHandle, ByteBuffer buffer, FileInputStream fileInputStream)
            throws IOException
    {
        final FileChannel channel = fileInputStream.getChannel();
        int c;
        while ((c = channel.read(buffer)) != -1)
        {
            streamWrite(docHandle, streamId, buffer, c);
            buffer.rewind();
        }
    }

    private void genericStreamWrite(int streamId, long docHandle, InputStream inputStream, ByteBuffer buffer)
            throws IOException
    {
        int c;
        final byte[] bytes = new byte[BUFFER_SIZE];
        while ((c = inputStream.read(bytes)) != -1)
        {
            buffer.rewind();
            buffer.put(bytes, 0, c);
            streamWrite(docHandle, streamId, buffer, c);
        }
    }

    public void setContentResolver(ContentResolver contentResolver)
    {
        this.contentResolver = contentResolver;
    }

    @Override
    protected void finalize() throws Throwable
    {
        free(contextHandle);
        super.finalize();
    }

    private static native long create();
    private static native void free(long contextHandle);
    private native void handleMessage(long contextHandle);
    private static native void streamWrite(long docHandle, int streamId, Buffer buffer, int dataLen);
    private static native void streamClose(long docHandle, int streamId, boolean stop);
}
