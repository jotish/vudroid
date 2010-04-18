package org.vudroid.djvudroid.codec;

import org.vudroid.core.codec.CodecDocument;

import java.util.concurrent.Semaphore;

public class DjvuDocument implements CodecDocument
{
    private final long documentHandle;
    private final Semaphore pagesSemaphore;
    private final Object waitObject;

    private DjvuDocument(long documentHandle, Semaphore pagesSemaphore, Object waitObject)
    {
        this.documentHandle = documentHandle;
        this.pagesSemaphore = pagesSemaphore;
        this.waitObject = waitObject;
    }

    static DjvuDocument openDocument(String uriHash, DjvuContext djvuContext, Semaphore pagesSemaphore, Object waitObject)
    {
        return new DjvuDocument(open(djvuContext.getContextHandle(), uriHash), pagesSemaphore, waitObject);
    }

    private native static long open(long contextHandle, String uri);
    private native static long getPage(long docHandle, int pageNumber);
    private native static int getPageCount(long docHandle);
    private native static void free(long pageHandle);

    public DjvuPage getPage(int pageNumber)
    {
        try
        {
            final DjvuPage value;
            pagesSemaphore.acquire();
            try
            {
                value = new DjvuPage(getPage(documentHandle, pageNumber), waitObject);
            }
            finally
            {
                pagesSemaphore.release();
            }
            return value;
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    public int getPageCount()
    {
        return getPageCount(documentHandle);
    }

    @Override
    protected void finalize() throws Throwable
    {
        free(documentHandle);
        super.finalize();
    }
}
