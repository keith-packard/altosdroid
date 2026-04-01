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
import android.graphics.Color;
import android.graphics.Point;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.altusmetrum.altosdroid.ui.map.MapFragment;
import org.altusmetrum.altoslib_14.AltosLatLon;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosMap;
import org.altusmetrum.altoslib_14.AltosMapTypeListener;
import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

class RocketOnline implements Comparable {
    Marker marker;
    int		serial;
    long		last_packet;
    int		size;

    void set_position(AltosLatLon position, long last_packet) {
        marker.setPosition(new LatLng(position.lat, position.lon));
        this.last_packet = last_packet;
    }

    public void remove() {
        marker.remove();
    }

    public int compareTo(Object o) {
        RocketOnline other = (RocketOnline) o;

        long	diff = last_packet - other.last_packet;

        if (diff > 0)
            return 1;
        if (diff < 0)
            return -1;
        return 0;
    }

    RocketOnline(Context context, int serial, GoogleMap map, double lat, double lon, long last_packet, int marker_size) {
        this.serial = serial;
        this.size = marker_size;
        String name = String.format(Locale.ROOT, "%d", serial);
        this.marker = map.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(RocketBitmap.create(context, name, marker_size)))
                .position(new LatLng(lat, lon))
                .visible(true));
        this.last_packet = last_packet;
    }
}
public class AltosDroidMapOnline implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, AltosDroidMapInterface, AltosMapTypeListener {
    private final HashMap<Integer,RocketOnline> rockets = new HashMap<Integer,RocketOnline>();

    private GoogleMap mMap;
    private final MapFragment map_fragment;
    private MainActivity altos_droid;
    private final Context context;
    private LatLng center;
    private LatLng pad_position;
    private Marker mPadMarker;
    private Polyline mPolyline;
    private double mapAccuracy = -1;

    public void map_type_changed(int map_type) {
        if (mMap != null) {
            if (map_type == AltosMap.maptype_hybrid)
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            else if (map_type == AltosMap.maptype_satellite)
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            else if (map_type == AltosMap.maptype_terrain)
                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
            else
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    private final OnMapReadyCallback callback = new OnMapReadyCallback() {

        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        @Override
        public void onMapReady(GoogleMap googleMap) {
            mMap = googleMap;
            int map_type = AltosPreferences.map_type();

            mMap.getUiSettings().setTiltGesturesEnabled(false);
            mMap.getUiSettings().setZoomControlsEnabled(false);
            mMap.setOnMarkerClickListener(AltosDroidMapOnline.this);
            mMap.setOnMapClickListener(AltosDroidMapOnline.this);
            Bitmap pad_bitmap = PadBitmap.create(context, MapFragment.marker_size);

            mPadMarker = mMap.addMarker(
                    new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(pad_bitmap))
                            .position(new LatLng(0,0))
                            .visible(false)
            );

            mPolyline = mMap.addPolyline(
                    new PolylineOptions().add(new LatLng(0,0), new LatLng(0,0))
                            .width(20)
                            .color(Color.BLUE)
                            .visible(false)
            );
            if (center != null)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center,14));
            if (pad_position != null) {
                mPadMarker.setPosition(pad_position);
                mPadMarker.setVisible(true);
            }
            AltosDroidMapOnline.this.map_type_changed(AltosPreferences.map_type());
            map_fragment.check_permission();

        }
    };

    private double pixel_distance(LatLng a, LatLng b) {
        Projection projection = mMap.getProjection();

        Point a_pt = projection.toScreenLocation(a);
        Point	b_pt = projection.toScreenLocation(b);

        return Math.hypot(a_pt.x - b_pt.x, a_pt.y - b_pt.y);
    }

    private RocketOnline[] sorted_rockets() {
        synchronized(rockets) {
            RocketOnline[]	rocket_array = rockets.values().toArray(new RocketOnline[0]);

            Arrays.sort(rocket_array);
            return rocket_array;
        }
    }

    public void onMapClick(LatLng lat_lng) {
        ArrayList<Integer> near = new ArrayList<Integer>();

        for (RocketOnline rocket : sorted_rockets()) {
            LatLng	pos = rocket.marker.getPosition();

            if (pos == null)
                continue;

            double distance = pixel_distance(lat_lng, pos);
            if (distance <= rocket.size * 2)
                near.add(rocket.serial);
        }

        if (near.size() != 0)
            altos_droid.touch_trackers(near.toArray(new Integer[0]));
    }

    public boolean onMarkerClick(Marker marker) {
        onMapClick(marker.getPosition());
        return true;
    }

    public void set_altos_droid(MainActivity altos_droid) {
        this.altos_droid = altos_droid;
    }

    public void center(double lat, double lon, double accuracy) {
        center = new LatLng(lat, lon);
        if (mMap == null) {
            return;
        }

        if (mapAccuracy < 0 || accuracy < mapAccuracy/10) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center,14));
            mapAccuracy = accuracy;
        }
    }

    private void set_rocket(int serial, AltosState state) {
        RocketOnline	rocket;

        if (state.gps == null || state.gps.lat == AltosLib.MISSING)
            return;

        if (mMap == null)
            return;

        synchronized(rockets) {
            if (rockets.containsKey(serial)) {
                rocket = rockets.get(serial);
                rocket.set_position(new AltosLatLon(state.gps.lat, state.gps.lon), state.received_time);
            } else {
                rocket = new RocketOnline(altos_droid,
                        serial,
                        mMap, state.gps.lat, state.gps.lon,
                        state.received_time,
                        MapFragment.marker_size);
                rockets.put(serial, rocket);
            }
        }
    }

    private void remove_rocket(int serial) {
        synchronized(rockets) {
            RocketOnline rocket = rockets.get(serial);
            rocket.remove();
            rockets.remove(serial);
        }
    }

    public void set_telem_state(TelemetryState telem_state) {
        synchronized(rockets) {
            for (int serial : rockets.keySet()) {
                if (!telem_state.containsKey(serial))
                    remove_rocket(serial);
            }

            for (int serial : telem_state.keySet()) {
                set_rocket(serial, telem_state.get(serial));
            }
        }
    }

    public void set_pad_position(double lat, double lon) {
        pad_position = new LatLng(lat, lon);
        if (mPadMarker != null) {
            mPadMarker.setPosition(pad_position);
            mPadMarker.setVisible(true);
        }
    }

    public void set_track(AltosLatLon my_position, AltosLatLon target_position) {
        if (mPolyline != null) {
            mPolyline.setPoints(Arrays.asList(new LatLng(my_position.lat, my_position.lon), new LatLng(target_position.lat, target_position.lon)));
            mPolyline.setVisible(true);
        }
    }

    public void position_permission() {
        if (mMap != null) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
            }
        }
    }

    public void destroy() {
        AltosPreferences.unregister_map_type_listener(this);
    }

    public AltosDroidMapOnline(MapFragment map_fragment, Context context) {
        this.map_fragment = map_fragment;
        this.context = context;
        AltosPreferences.register_map_type_listener(this);
        SupportMapFragment mapFragment = (SupportMapFragment) map_fragment.getChildFragmentManager().findFragmentById(R.id.map_online);
        mapFragment.getMapAsync(callback);
    }
}
