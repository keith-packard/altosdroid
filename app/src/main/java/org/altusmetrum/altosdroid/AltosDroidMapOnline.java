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
import android.graphics.Color;
import android.graphics.Point;

import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener;
import com.google.android.gms.maps.GoogleMap.OnCameraMoveCanceledListener;
import com.google.android.gms.maps.GoogleMap.OnCameraMoveListener;
import com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapColorScheme;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import org.altusmetrum.altosdroid.ui.map.MapFragment;
import org.altusmetrum.altoslib_14.AltosLatLon;
import org.altusmetrum.altoslib_14.AltosLaunchSite;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosMap;
import org.altusmetrum.altoslib_14.AltosMapTypeListener;
import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

class RocketOnline implements Comparable {
    private Marker      marker;
    private AltosMarker altos_marker;
    private int		serial;
    private long	last_packet;
    private int		width, height;
    private double      lat, lon;
    private boolean     is_target;

    float z_index() {
        return is_target ? 3 : 2;
    }

    synchronized void set_position(double lat, double lon, long last_packet, boolean is_target) {
        this.lat = lat;
        this.lon = lon;
        this.is_target = is_target;
        this.last_packet = last_packet;
        if (marker != null) {
            marker.setPosition(new LatLng(lat, lon));
            marker.setZIndex(z_index());
        }
    }

    synchronized LatLng get_position() {
        return new LatLng(lat, lon);
    }

    public synchronized void remove() {
        if (marker != null)
            marker.remove();
    }

    public synchronized int width() {
        return width;
    }

    public synchronized int serial() {
        return serial;
    }

    synchronized void set_map(Context context, GoogleMap map) {
        if (marker == null && map != null) {
            this.marker = map.addMarker(new MarkerOptions()
                                        .icon(BitmapDescriptorFactory.fromBitmap(altos_marker.bitmap))
                                        .position(new LatLng(lat, lon))
                                        .anchor(altos_marker.off_x, altos_marker.off_y)
                                        .zIndex(z_index())
                                        .visible(true));
        }
    }

    synchronized long last_packet() {
        return last_packet;
    }

    public int compareTo(Object o) {
        RocketOnline other = (RocketOnline) o;

        long	diff = last_packet() - other.last_packet();

        if (diff > 0)
            return 1;
        if (diff < 0)
            return -1;
        return 0;
    }

    RocketOnline(Context context, int serial, GoogleMap map, double lat, double lon, long last_packet, boolean is_target) {
        this.serial = serial;
        this.altos_marker = new RocketMarker(context, serial);
        this.width = altos_marker.width;
        this.height = altos_marker.height;
        set_position(lat, lon, last_packet, is_target);
        set_map(context, map);
    }
}

public class AltosDroidMapOnline implements
                                 OnMarkerClickListener,
                                 OnMapClickListener,
                                 OnCameraMoveStartedListener,
                                 OnCameraIdleListener,
                                 AltosDroidMapInterface,
                                 AltosMapTypeListener {

    private final HashMap<Integer,RocketOnline> rockets = new HashMap<Integer,RocketOnline>();

    private GoogleMap mMap;
    private final MapFragment map_fragment;
    private MainActivity altos_droid;
    private final Context context;
    private LatLng center;
    private LatLng pad_position, here, there;
    private Marker mPadMarker;
    private Marker mHereMarker;
    private Polyline mPolyline;
    private List<AltosLaunchSite> launch_sites;

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

    private int move_reason;

    public void onCameraMoveStarted(int reason) {
        move_reason = reason;
    }

    public void onCameraIdle() {
        if (move_reason == OnCameraMoveStartedListener.REASON_GESTURE)
            if (map_fragment != null)
                map_fragment.user_motion();
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
            mMap.getUiSettings().setTiltGesturesEnabled(false);
            mMap.getUiSettings().setZoomControlsEnabled(false);
            mMap.setOnMarkerClickListener(AltosDroidMapOnline.this);
            mMap.setOnMapClickListener(AltosDroidMapOnline.this);
            mMap.setOnCameraMoveStartedListener(AltosDroidMapOnline.this);
            mMap.setOnCameraIdleListener(AltosDroidMapOnline.this);
            mMap.setMapColorScheme(MapColorScheme.FOLLOW_SYSTEM);

            AltosMarker pad_marker = new PadMarker(context);

            mPadMarker = mMap.addMarker(
                    new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(pad_marker.bitmap))
                            .position(new LatLng(0,0))
                            .anchor(pad_marker.off_x, pad_marker.off_y)
                            .zIndex(1)
                            .visible(false)
            );

            AltosMarker here_marker = new HereMarker(context);

            mHereMarker = mMap.addMarker(
                    new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(here_marker.bitmap))
                            .position(new LatLng(0, 0))
                            .anchor(here_marker.off_x, here_marker.off_y)
                            .zIndex(1)
                            .visible(false)
            );

            mPolyline = mMap.addPolyline(
                    new PolylineOptions().add(new LatLng(0,0), new LatLng(0,0))
                            .width(20)
                            .endCap(new RoundCap())
                            .color(ContextCompat.getColor(context, R.color.altus_purple))
                            .visible(false)
            );

            if (center != null)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center,14));

            set_pad();
            set_here();
            set_polyline();
            add_launch_sites();
            add_rockets();

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

    /* Snapshot current rocket list and return it sorted by last update time */
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
            LatLng	pos = rocket.get_position();

            if (pos == null)
                continue;

            double distance = pixel_distance(lat_lng, pos);
            if (distance <= rocket.width() * 2)
                near.add(rocket.serial());
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

    public void center(double lat, double lon, boolean reset_zoom) {
        center = new LatLng(lat, lon);
        if (mMap != null) {
            if (reset_zoom)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center,14));
            else
                mMap.animateCamera(CameraUpdateFactory.newLatLng(center));
        }
    }

    /* Only called from set_telem_state, must hold rockets locked */
    private void set_rocket(int serial, AltosState state, boolean is_target) {
        RocketOnline	rocket;

        if (state.gps == null || state.gps.lat == AltosLib.MISSING)
            return;

        if (mMap == null)
            return;

        if (rockets.containsKey(serial)) {
            rocket = rockets.get(serial);
            rocket.set_position(state.gps.lat, state.gps.lon, state.received_time, is_target);
        } else {
            rocket = new RocketOnline(altos_droid,
                                      serial,
                                      mMap, state.gps.lat, state.gps.lon,
                                      state.received_time, is_target);
            rockets.put(serial, rocket);
        }
    }

    /* Only called from set_telem_state, must hold rockets locked */
    private void remove_rocket(int serial) {
        RocketOnline rocket = rockets.get(serial);
        rocket.remove();
        rockets.remove(serial);
    }

    public void set_telem_state(TelemetryState telem_state) {
        synchronized(rockets) {
            for (int serial : rockets.keySet()) {
                if (!telem_state.containsKey(serial))
                    remove_rocket(serial);
            }

            for (int serial : telem_state.keySet()) {
                set_rocket(serial, telem_state.get(serial), serial == map_fragment.target_serial);
            }
        }
    }

    private void set_pad() {
        if (pad_position != null && mPadMarker != null) {
            mPadMarker.setPosition(pad_position);
            mPadMarker.setVisible(true);
        }
    }

    private void set_here() {
        if (here != null && mHereMarker != null) {
            mHereMarker.setPosition(here);
            mHereMarker.setVisible(true);
        }
    }

    private void set_polyline() {
        if (here != null && there != null && mPolyline != null) {
            mPolyline.setPoints(Arrays.asList(here, there));
            mPolyline.setVisible(true);
        }
    }

    private void add_launch_sites() {
        if (launch_sites != null && mMap != null) {
            for (AltosLaunchSite site : launch_sites) {
                AltosMarker launch_site_marker = new LaunchSiteMarker(context, site.name);
                Marker marker = mMap.addMarker(
                    new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(launch_site_marker.bitmap))
                        .position(new LatLng(site.latitude, site.longitude))
                        .anchor(launch_site_marker.off_x, launch_site_marker.off_y)
                        .zIndex(0)
                        .visible(true));
            }
        }
    }

    private void add_rockets() {
        if (mMap != null) {
            for (RocketOnline rocket : sorted_rockets())
                rocket.set_map(context, mMap);
        }
    }

    public void set_pad_position(double lat, double lon) {
        pad_position = new LatLng(lat, lon);
        set_pad();
    }

    public void set_here_position(double lat, double lon) {
        here = new LatLng(lat, lon);
        set_here();
        set_polyline();
    }

    public void set_there_position(double lat, double lon) {
        there = new LatLng(lat, lon);
        set_polyline();
    }

    public void set_launch_sites(List<AltosLaunchSite> sites) {
        if (launch_sites == null) {
            launch_sites = sites;
            add_launch_sites();
        }
    }

    public void position_permission() {
        if (mMap != null) {
            try {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } catch (SecurityException e) {
            }
        }
    }

    public void activate(MapFragment map_fragment) {
        AltosPreferences.register_map_type_listener(this);
    }

    public void deactivate() {
        AltosPreferences.unregister_map_type_listener(this);
    }

    public AltosDroidMapOnline(MapFragment map_fragment, Context context) {
        this.map_fragment = map_fragment;
        this.context = context;
        SupportMapFragment mapFragment = (SupportMapFragment) map_fragment.getChildFragmentManager().findFragmentById(R.id.map_online);
        mapFragment.getMapAsync(callback);
    }
}
