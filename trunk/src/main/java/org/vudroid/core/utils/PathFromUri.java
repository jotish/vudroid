package org.vudroid.core.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class PathFromUri
{
    public static String retrieve(ContentResolver resolver, Uri uri)
    {
        if (uri.getScheme().equals("file"))
        {
            return uri.getPath();
        }
        final Cursor cursor = resolver.query(uri, new String[]{"_data"}, null, null, null);
        return cursor.getString(0);
    }
}
