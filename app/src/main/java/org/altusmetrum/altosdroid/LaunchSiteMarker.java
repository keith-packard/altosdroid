/*
 * Copyright © 2026 Keith Packard <keithp@keithp.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.altusmetrum.altosdroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

public class LaunchSiteMarker extends AltosMarker {
    public LaunchSiteMarker(Context context) {
        super(context, R.drawable.launch_site, R.dimen.map_site_size, 0.5f, 0.5f);
    }

    public LaunchSiteMarker(Context context, String name) {
        super(context, R.drawable.launch_site, R.dimen.map_site_size, 0.5f, 0.5f);
        Rect text_bounds = new Rect();
        MainActivity.measure_text(context, name, R.dimen.map_text_missing_size, text_bounds);
        int off_x_int = (int) (off_x * width);
        int off_y_int = (int) (off_y * height);
        Rect bitmap_bounds = new Rect(-off_x_int, -off_y_int, width - off_x_int, height - off_y_int);
        bitmap_bounds.union(text_bounds);
        Bitmap new_bitmap = Bitmap.createBitmap(bitmap_bounds.width(), bitmap_bounds.height(), Bitmap.Config.ARGB_8888);
        Canvas new_canvas = new Canvas(new_bitmap);
        int new_width = new_bitmap.getWidth();
        int new_height = new_bitmap.getHeight();
        float old_off_x_pix = off_x * width;
        float old_off_y_pix = off_y * height;
        off_x = old_off_x_pix / new_width;
        off_y = old_off_y_pix / new_height;
        new_canvas.drawBitmap(bitmap, 0, 0, null);
        MainActivity.draw_text(context, new_canvas, name, old_off_x_pix, old_off_y_pix, R.dimen.map_text_missing_size, Paint.Align.LEFT);
        width = new_width;
        height = new_height;
        canvas = new_canvas;
        bitmap = new_bitmap;
    }
}
