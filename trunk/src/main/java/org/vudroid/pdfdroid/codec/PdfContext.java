package org.vudroid.pdfdroid.codec;

import android.content.ContentResolver;
import android.net.Uri;
import org.vudroid.core.VuDroidLibraryLoader;
import org.vudroid.core.codec.CodecContext;
import org.vudroid.core.codec.CodecDocument;

public class PdfContext implements CodecContext
{
    static
    {
        VuDroidLibraryLoader.load();
    }

    public CodecDocument openDocument(Uri uri)
    {
        return PdfDocument.openDocument(uri.getPath(), "");
    }

    public void setContentResolver(ContentResolver contentResolver)
    {
        //TODO
    }
}
