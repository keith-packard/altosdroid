package org.altusmetrum.altosdroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import org.altusmetrum.altosdroid.ui.map.MapFragment;

public class PadBitmap  {

    public static Bitmap create(Context context, int marker_size) {
        Drawable drawable = VectorDrawableCompat.create(context.getResources(), R.drawable.pad_purple, context.getTheme());
        Bitmap bitmap = Bitmap.createBitmap(marker_size, marker_size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
