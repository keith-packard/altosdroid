package org.altusmetrum.altosdroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

public class AltosMarker {
    public float off_x, off_y;
    public int width, height;
    public Bitmap bitmap;
    public Canvas canvas;


    public AltosMarker(Context context, int drawable_id, int size_id, float off_x, float off_y) {
        int size = context.getResources().getDimensionPixelSize(size_id);

        bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        width = bitmap.getWidth();
        height = bitmap.getHeight();
        this.off_x = off_x;
        this.off_y = off_y;
        canvas = new Canvas(bitmap);
        Drawable drawable = VectorDrawableCompat.create(context.getResources(), drawable_id, context.getTheme());
        if (drawable != null) {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
        } else {
            bitmap.eraseColor(Color.TRANSPARENT);
            MainActivity.draw_text(context, canvas, String.format("??%x??", drawable_id), width/2, height/2, Paint.Align.CENTER);
        }
    }
    public AltosMarker(Context context, int id, float off_x, float off_y) {
        this(context, id, R.dimen.map_marker_size, off_x, off_y);
    }
    public AltosMarker(Context context, int id) {
        this(context, id, 0.5f, 1.0f);
    }

    public void draw(Canvas canvas, float x, float y) {
        canvas.drawBitmap(bitmap, x - off_x * width, y - off_y * height, null);
    }
}
