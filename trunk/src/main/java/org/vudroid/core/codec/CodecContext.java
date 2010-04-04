package org.vudroid.core.codec;

import android.content.ContentResolver;
import android.net.Uri;

public interface CodecContext
{
    CodecDocument openDocument(Uri uri);

    void setContentResolver(ContentResolver contentResolver);
}
