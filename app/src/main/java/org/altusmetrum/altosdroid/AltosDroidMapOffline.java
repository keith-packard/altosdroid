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
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.altusmetrum.altosdroid.ui.map.MapFragment;
import org.altusmetrum.altoslib_14.AltosImage;
import org.altusmetrum.altoslib_14.AltosLatLon;
import org.altusmetrum.altoslib_14.AltosLaunchSite;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

class RocketOffline implements Comparable {
    AltosLatLon position;
    int serial;
    int width, height;
    long last_packet;
    boolean active;
    AltosDroidMapOffline map_offline;
    AltosMarker marker;


    RocketOffline(Context context, int serial, AltosDroidMapOffline map_offline) {
        this.serial = serial;
        String name = String.format(Locale.ROOT, "%d", serial);
        map_offline = map_offline;
        marker = new RocketMarker(context, serial);
        width = marker.width;
        height = marker.height;
        this.map_offline = map_offline;
    }

    void paint() {
        if (map_offline != null)
            map_offline.draw_marker(position, marker);
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
    int line_color;

    Canvas canvas;
    Paint paint;
    AltosMarker pad_marker;
    AltosMarker here_marker;
    AltosMarker launch_site_marker;
    Line line = new Line();
    int stroke_width = 20;
    HashMap<Integer, RocketOffline> rockets = new HashMap<>();
    List<AltosLaunchSite> launch_sites;
    private MainActivity altos_droid;
    class MapTile extends AltosMapTile {

        public MapTile(AltosMapCache cache, AltosLatLon upper_left, AltosLatLon center, int zoom, int map_type, int px_size, int scale) {
            super(cache, upper_left, center, zoom, map_type, px_size, scale);
        }

        public void paint(AltosMapTransform t) {
            AltosPointInt pt = new AltosPointInt(t.screen(upper_left));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (canvas.quickReject(pt.x, pt.y, pt.x + px_size, pt.y + px_size)) {
                    return;
                }
            }

            AltosImage altos_image = this.get_image();

            AltosDroidMapOffline.MapImage map_image = (AltosDroidMapOffline.MapImage) altos_image;

            Bitmap bitmap = null;

            if (map_image != null) bitmap = map_image.bitmap;

            if (bitmap != null) {
                canvas.drawBitmap(bitmap, pt.x, pt.y, null);
            } else {
                paint.setColor(Color.GRAY);
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
                        float x = pt.x + px_size / 2.0f;
                        float y = pt.y + px_size / 2.0f;

                        MainActivity.draw_text(context, canvas,
                                               String.format("%.6f,%.6f %s", center.lat, center.lon, message),
                                               x, y, R.dimen.map_text_missing_size, Paint.Align.CENTER);
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

        for (RocketOffline rocket : sorted_rockets(true)) {
            if (rocket.position == null) {
                debug("rocket %d has no position\n", rocket.serial);
                continue;
            }
            double distance = map.transform.hypot(latlon, rocket.position);
            debug("check select %d distance %g width %d\n", rocket.serial, distance, rocket.width);
            if (distance < rocket.width * 2.0) {
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
            MainActivity.draw_text(context, canvas, text, (float) pt.x, (float) pt.y, Paint.Align.CENTER);
        }
    }

    void draw_marker(AltosLatLon lat_lon, AltosMarker marker) {
        if (lat_lon != null && map != null && map.transform != null) {
            AltosPointInt pt = new AltosPointInt(map.transform.screen(lat_lon));
            marker.draw(canvas, (float) pt.x, (float) pt.y);
        }
    }

    private RocketOffline[] sorted_rockets(boolean forward) {
        RocketOffline[] rocket_array = rockets.values().toArray(new RocketOffline[0]);

        if (forward)
            Arrays.sort(rocket_array);
        else
            Arrays.sort(rocket_array, Collections.reverseOrder());
        return rocket_array;
    }

    private void draw_positions() {
        if (here != null && there != null) {
            line.set_a(there);
            line.set_b(here);
            line.paint();
        }
        if (pad != null)
            draw_marker(pad, pad_marker);

        RocketOffline target_rocket = null;

        for (RocketOffline rocket : Arrays.asList(sorted_rockets(false))) {
            if (rocket.serial == map_fragment.target_serial)
                target_rocket = rocket;
            else
                rocket.paint();
        }
        if (target_rocket != null)
            target_rocket.paint();
        if (here != null)
            draw_marker(here, here_marker);
    }

    private void draw_launch_sites() {
        if (launch_sites != null) {
            for (AltosLaunchSite site : launch_sites) {
                AltosLatLon lat_lon = new AltosLatLon(site.latitude, site.longitude);
                AltosPointInt pt = new AltosPointInt(map.transform.screen(lat_lon));
                launch_site_marker.draw(canvas, (float) pt.x, (float) pt.y);
                MainActivity.draw_text(context, canvas, site.name, (float) pt.x, (float) pt.y, R.dimen.map_text_missing_size, Paint.Align.LEFT);
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas view_canvas) {
        super.onDraw(view_canvas);
        if (map == null) {
            debug("MapView draw without map\n");
            return;
        }
        if (map.transform == null) {
            debug("MapView draw without transform\n");
            return;
        }
        canvas = view_canvas;

        map.paint();
        float y = getResources().getDimension(R.dimen.map_text_size);
        MainActivity.draw_text(context, canvas, String.format("%d", map.get_zoom()), width() - y / 10, y, Paint.Align.RIGHT);
        draw_launch_sites();
        draw_positions();
        canvas = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        map.set_transform();
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

        if (map_fragment != null)
            map_fragment.user_motion();

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

    public void center(double lat, double lon, boolean reset_zoom) {
        if (map != null) {
            map.centre(lat, lon);
            if (reset_zoom)
                map.set_zoom(14);
        }
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
                paint.setColor(line_color);
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
                        rocket = new RocketOffline(context, serial, this);
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

    public void set_here_position(double lat, double lon) {
        here = new AltosLatLon(lat, lon);
        repaint();
    }

    public void set_there_position(double lat, double lon) {
        there = new AltosLatLon(lat, lon);
        repaint();
    }

    public void position_permission() {
    }

    public void set_launch_sites(List<AltosLaunchSite> sites) {
        launch_sites = sites;
        repaint();
    }

    public void activate(MapFragment map_fragment) {
        this.map_fragment = map_fragment;
    }

    public void deactivate() {}


    public AltosDroidMapOffline(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        scale_detector = new ScaleGestureDetector(context, this);
        map = new AltosMap(this, scale);
        map.set_maptype(AltosPreferences.map_type());

        pad_marker = new PadMarker(context);

        here_marker = new HereMarker(context);

        launch_site_marker = new LaunchSiteMarker(context);

        line_color = ContextCompat.getColor(context, R.color.altus_purple);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStrokeWidth(stroke_width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }
}

