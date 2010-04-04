package org.vudroid.core.codec;

import android.graphics.Bitmap;

public interface CodecPage
{
    boolean isDecoding();

    void waitForDecode();

    int getWidth();

    int getHeight();

    Bitmap renderBitmap(int width, int height);
}
