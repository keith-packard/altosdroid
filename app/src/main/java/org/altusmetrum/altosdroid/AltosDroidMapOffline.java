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
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;

import org.altusmetrum.altosdroid.ui.map.MapFragment;
import org.altusmetrum.altoslib_14.AltosImage;
import org.altusmetrum.altoslib_14.AltosLatLon;
import org.altusmetrum.altoslib_14.AltosMap;
import org.altusmetrum.altoslib_14.AltosMapCache;
import org.altusmetrum.altoslib_14.AltosMapInterface;
import org.altusmetrum.altoslib_14.AltosMapLine;
import org.altusmetrum.altoslib_14.AltosMapMark;
import org.altusmetrum.altoslib_14.AltosMapPath;
import org.altusmetrum.altoslib_14.AltosMapTile;
import org.altusmetrum.altoslib_14.AltosMapTransform;
import org.altusmetrum.altoslib_14.AltosPointDouble;
import org.altusmetrum.altoslib_14.AltosPointInt;
import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosRectangle;
import org.altusmetrum.altoslib_14.AltosState;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

class RocketOffline implements Comparable {
    AltosLatLon position;
    int serial;
    int size;
    long last_packet;
    boolean active;
    AltosDroidMapOffline map_offline;
    Bitmap bitmap;

    RocketOffline(Context context, int serial, AltosDroidMapOffline map_offline, int marker_size) {
        this.serial = serial;
        String name = String.format(Locale.ROOT, "%d", serial);
        this.map_offline = map_offline;
        this.bitmap = RocketBitmap.create(context, name, marker_size);
        this.size = marker_size;
    }

    void paint() {
        int off_x = bitmap.getWidth() / 2;
        int off_y = bitmap.getHeight() / 2;
        map_offline.draw_bitmap(position, bitmap, off_x, off_y);
    }

    void set_position(AltosLatLon position, long last_packet) {
        this.position = position;
        this.last_packet = last_packet;
    }

    void set_active(boolean active) {
        this.active = active;
    }

    public int compareTo(Object o) {
        RocketOffline other = (RocketOffline) o;

        if (active && !other.active) return 1;
        if (other.active && !active) return -1;

        long diff = last_packet - other.last_packet;

        if (diff > 0) return 1;
        if (diff < 0) return -1;
        return 0;
    }
}



public class AltosDroidMapOffline extends View implements ScaleGestureDetector.OnScaleGestureListener, AltosDroidMapInterface, AltosMapInterface {

    static int scale = 1;
    ScaleGestureDetector scale_detector;
    boolean scaling;
    AltosMap map;
    MapFragment map_fragment;
    Context context;
    AltosLatLon here;
    AltosLatLon there;
    AltosLatLon pad;

    Canvas canvas;
    Paint paint;

    Bitmap pad_bitmap;
    int pad_off_x, pad_off_y;
    Bitmap here_bitmap;
    int here_off_x, here_off_y;
    Line line = new Line();
    int stroke_width = 20;
    HashMap<Integer, RocketOffline> rockets = new HashMap<>();
    private MainActivity altos_droid;
    class MapTile extends AltosMapTile {

        public MapTile(AltosMapCache cache, AltosLatLon upper_left, AltosLatLon center, int zoom, int map_type, int px_size, int scale) {
            super(cache, upper_left, center, zoom, map_type, px_size, scale);
        }

        public void paint(AltosMapTransform t) {
            AltosPointInt pt = new AltosPointInt(t.screen(upper_left));

            if (canvas.quickReject(pt.x, pt.y, pt.x + px_size, pt.y + px_size)) {
                return;
            }

            AltosImage altos_image = this.get_image();

            AltosDroidMapOffline.MapImage map_image = (AltosDroidMapOffline.MapImage) altos_image;

            Bitmap bitmap = null;

            if (map_image != null) bitmap = map_image.bitmap;

            if (bitmap != null) {
                canvas.drawBitmap(bitmap, pt.x, pt.y, paint);
            } else {
                paint.setColor(0xff808080);
                canvas.drawRect(pt.x, pt.y, pt.x + px_size, pt.y + px_size, paint);
                if (t.has_location()) {
                    String message = null;
                    switch (status) {
                        case AltosMapTile.fetching:
                            message = "Fetching...";
                            break;
                        case AltosMapTile.bad_request:
                            message = "Internal error";
                            break;
                        case AltosMapTile.failed:
                            message = "Network error";
                            break;
                        case AltosMapTile.forbidden:
                            message = "Outside of known launch areas";
                            break;
                    }
                    if (message != null) {
                        Rect bounds = new Rect();
                        paint.getTextBounds(message, 0, message.length(), bounds);

                        int width = bounds.right - bounds.left;
                        int height = bounds.bottom - bounds.top;

                        float x = pt.x + px_size / 2.0f;
                        float y = pt.y + px_size / 2.0f;
                        x = x - width / 2.0f;
                        y = y + height / 2.0f;
                        paint.setColor(0xff000000);
                        canvas.drawText(message, 0, message.length(), x, y, paint);
                    }
                }
            }
        }

    }

    public AltosMapTile new_tile(AltosMapCache cache, AltosLatLon upper_left, AltosLatLon center, int zoom, int map_type, int px_size, int scale) {
        return new MapTile(cache, upper_left, center, zoom, map_type, px_size, scale);
    }

    public AltosMapPath new_path() {
        return null;
    }

    public AltosMapLine new_line() {
        return null;
    }

    public AltosImage load_image(File file) throws Exception {
        return new MapImage(file);
    }

    public AltosMapMark new_mark(double lat, double lon, int state) {
        return new MapMark(lat, lon, state);
    }

    public AltosMapMark new_mark(double lat, double lon, int state, String label) {
        return new MapMark(lat, lon, state, label);
    }

    public int width() {
        return getWidth();
    }

    public int height() {
        return getHeight();
    }

    public void repaint() {
        postInvalidate();
    }

    public void repaint(AltosRectangle damage) {
        postInvalidate(damage.x, damage.y, damage.x + damage.width, damage.y + damage.height);
    }

    public void set_zoom_label(String label) {
    }

    @Override
    public void debug(String format, Object... arguments) {

    }

    public void select_object(AltosLatLon latlon) {
        if (map.transform == null) return;
        ArrayList<Integer> near = new ArrayList<Integer>();

        for (RocketOffline rocket : sorted_rockets()) {
            if (rocket.position == null) {
                debug("rocket %d has no position\n", rocket.serial);
                continue;
            }
            double distance = map.transform.hypot(latlon, rocket.position);
            debug("check select %d distance %g width %d\n", rocket.serial, distance, rocket.size);
            if (distance < rocket.size * 2.0) {
                debug("selecting %d\n", rocket.serial);
                near.add(rocket.serial);
            }
        }
        if (!near.isEmpty())
            altos_droid.touch_trackers(near.toArray(new Integer[0]));
    }

    void draw_text(AltosLatLon lat_lon, String text, int off_x, int off_y) {
        if (lat_lon != null && map != null && map.transform != null) {
            AltosPointInt pt = new AltosPointInt(map.transform.screen(lat_lon));

            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);

            int width = bounds.right - bounds.left;
            int height = bounds.bottom - bounds.top;

            float x = pt.x;
            float y = pt.y;
            x = x - width / 2.0f - off_x;
            y = y + height / 2.0f - off_y;
            paint.setColor(0xff000000);
            canvas.drawText(text, 0, text.length(), x, y, paint);
        }
    }

    void draw_bitmap(AltosLatLon lat_lon, Bitmap bitmap, int off_x, int off_y) {
        if (lat_lon != null && map != null && map.transform != null) {
            AltosPointInt pt = new AltosPointInt(map.transform.screen(lat_lon));

            canvas.drawBitmap(bitmap, pt.x - off_x, pt.y - off_y, paint);
        }
    }

    private RocketOffline[] sorted_rockets() {
        RocketOffline[] rocket_array = rockets.values().toArray(new RocketOffline[0]);

        Arrays.sort(rocket_array);
        return rocket_array;
    }

    private void draw_positions() {
        line.set_a(there);
        line.set_b(here);
        line.paint();
        draw_bitmap(pad, pad_bitmap, pad_off_x, pad_off_y);

        for (RocketOffline rocket : sorted_rockets())
            rocket.paint();
        draw_bitmap(here, here_bitmap, here_off_x, here_off_y);
    }

    @Override
    protected void onDraw(@NonNull Canvas view_canvas) {
        super.onDraw(view_canvas);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(40);
        String text = "Hello, world";
        view_canvas.drawText(text, 0, text.length(), 200, 200, paint);
        if (map == null) {
            debug("MapView draw without map\n");
            return;
        }
        if (map.transform == null) {
            debug("MapView draw without transform\n");
            return;
        }
        canvas = view_canvas;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(stroke_width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setTextSize(40);
        map.paint();
        draw_positions();
        canvas = null;
    }

    public boolean onScale(ScaleGestureDetector detector) {
        float f = detector.getScaleFactor();

        if (f <= 0.8) {
            map.set_zoom_centre(map.get_zoom() - 1, new AltosPointInt((int) detector.getFocusX(), (int) detector.getFocusY()));
            return true;
        }
        if (f >= 1.2) {
            map.set_zoom_centre(map.get_zoom() + 1, new AltosPointInt((int) detector.getFocusX(), (int) detector.getFocusY()));
            return true;
        }
        return false;
    }

    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        return true;
    }

    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        scale_detector.onTouchEvent(event);

        if (scale_detector.isInProgress()) {
            scaling = true;
        }

        if (scaling) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                scaling = false;
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            map.touch_start((int) event.getX(), (int) event.getY(), true);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            map.touch_continue((int) event.getX(), (int) event.getY(), true);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            map.touch_stop((int) event.getX(), (int) event.getY(), true);
        }
        return true;
    }

    public void center(double lat, double lon, double accuracy) {
        if (map != null)
            map.maybe_centre(lat, lon);
    }

    public void set_visible(boolean visible) {
        if (visible) setVisibility(VISIBLE);
        else setVisibility(GONE);
    }

    class MapImage implements AltosImage {
        public Bitmap bitmap;

        public MapImage(File file) {
            bitmap = BitmapFactory.decodeFile(file.getPath());
        }

        public void flush() {
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        }
    }

    class MapMark extends AltosMapMark {
        MapMark(double lat, double lon, int state) {
            super(lat, lon, state);
        }

        MapMark(double lat, double lon, int state, String label) {
            super(lat, lon, state, label);
        }

        public void paint(AltosMapTransform t) {
        }
    }

    class Line {
        AltosLatLon a, b;

        Line() {
        }

        void paint() {
            if (a != null && b != null) {
                AltosPointDouble a_screen = map.transform.screen(a);
                AltosPointDouble b_screen = map.transform.screen(b);
                paint.setColor(0xff8080ff);
                canvas.drawLine((float) a_screen.x, (float) a_screen.y, (float) b_screen.x, (float) b_screen.y, paint);
            }
        }

        void set_a(AltosLatLon a) {
            this.a = a;
        }

        void set_b(AltosLatLon b) {
            this.b = b;
        }
    }

    public void set_altos_droid(MainActivity altos_droid) {
        this.altos_droid = altos_droid;
    }

    public void set_telem_state(TelemetryState telem_state) {
        synchronized(rockets) {
            if (telem_state != null) {
                Integer[] old_serial = rockets.keySet().toArray(new Integer[0]);
                Integer[] new_serial = telem_state.keySet().toArray(new Integer[0]);

                /* remove deleted keys */
                for (int serial : old_serial) {
                    if (!telem_state.containsKey(serial))
                        rockets.remove(serial);
                }

                /* set remaining keys */

                for (int serial : new_serial) {
                    RocketOffline rocket;
                    AltosState state = telem_state.get(serial);
                    if (rockets.containsKey(serial))
                        rocket = rockets.get(serial);
                    else {
                        rocket = new RocketOffline(context, serial, this, MapFragment.marker_size);
                        rockets.put(serial, rocket);
                    }
                    if (state.gps != null) {
                        AltosLatLon latlon = new AltosLatLon(state.gps.lat, state.gps.lon);
                        rocket.set_position(latlon, state.received_time);
                    }
                }
            }
        }
        repaint();
    }

    public void set_pad_position(double lat, double lon) {
        pad = new AltosLatLon(lat, lon);
        repaint();
    }

    public void set_track(AltosLatLon my_position, AltosLatLon target_position) {
        here = my_position;
        there = target_position;
        repaint();
    }

    public void position_permission() {
    }

    public void destroy() {
    }

    public void set_map_fragment(MapFragment map_fragment) {
        this.map_fragment = map_fragment;
    }

    public AltosDroidMapOffline(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        scale_detector = new ScaleGestureDetector(context, this);
        map = new AltosMap(this, scale);
        map.set_maptype(AltosPreferences.map_type());

        pad_bitmap = PadBitmap.create(context, MapFragment.marker_size);
        /* arrow at the bottom of the launchpad image */
        pad_off_x = pad_bitmap.getWidth() / 2;
        pad_off_y = pad_bitmap.getHeight();

        here_bitmap = HereBitmap.create(context, MapFragment.marker_size);
        /* Center of the dot */
        here_off_x = here_bitmap.getWidth() / 2;
        here_off_y = here_bitmap.getHeight() / 2;
    }
}

