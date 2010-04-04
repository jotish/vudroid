package org.vudroid.core;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

public interface DecodeService
{
    void setContentResolver(ContentResolver contentResolver);

    void setContainerView(View containerView);

    void open(Uri fileUri);

    void decodePage(int pageNum, DecodeCallback decodeCallback, float zoom);

    void stopDecoding(int pageNum);

    int getEffectivePagesWidth();

    int getEffectivePagesHeight();

    int getPageCount();

    public interface DecodeCallback
    {
        void decodeComplete(Bitmap bitmap);
    }
}
