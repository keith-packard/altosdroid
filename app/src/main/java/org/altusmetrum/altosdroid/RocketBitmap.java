package org.altusmetrum.altosdroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

public class RocketBitmap {
    public static Bitmap create (Context context, String text, int marker_size) {
        Paint paint = new Paint();
        paint.setTextSize(40);
        paint.setAntiAlias(true);

        Rect bounds = new Rect();
        int size = marker_size;

        Drawable drawable = VectorDrawableCompat.create(context.getResources(), R.drawable.flight_orange, context.getTheme());
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        paint.getTextBounds(text, 0, text.length(), bounds);

        int	width = bounds.right - bounds.left;
        int	height = bounds.bottom - bounds.top;

        float x = bitmap.getWidth() / 2.0f - width / 2.0f;
        float y = bitmap.getHeight() / 2.0f - height / 2.0f;

        paint.setColor(0xffffffff);
        int offset = marker_size / 40;
        for (int yoffset = -offset; yoffset <= offset; yoffset += offset)
            for (int xoffset = -offset; xoffset <= offset; xoffset += offset)
                canvas.drawText(text, 0, text.length(), x+xoffset, y+yoffset, paint);
        paint.setColor(0xff000000);
        canvas.drawText(text, 0, text.length(), x, y, paint);
        return bitmap;
    }
}
