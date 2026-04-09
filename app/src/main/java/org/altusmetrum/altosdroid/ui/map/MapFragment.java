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

package org.altusmetrum.altosdroid.ui.map;

import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.altusmetrum.altosdroid.AltosDroidMapSourceListener;
import org.altusmetrum.altosdroid.AltosDroidPreferences;
import org.altusmetrum.altosdroid.AltosDroidSelectedSerialListener;
import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.AltosDroidMapInterface;
import org.altusmetrum.altosdroid.AltosDroidMapOnline;
import org.altusmetrum.altosdroid.AltosValue;
import org.altusmetrum.altosdroid.MainActivity;
import org.altusmetrum.altosdroid.R;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentMapBinding;
import org.altusmetrum.altoslib_14.AltosCalData;
import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLatLon;
import org.altusmetrum.altoslib_14.AltosLaunchSite;
import org.altusmetrum.altoslib_14.AltosLaunchSiteListener;
import org.altusmetrum.altoslib_14.AltosLaunchSites;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosMap;
import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.List;
import java.util.Locale;



public class MapFragment extends AltosFragment
    implements AltosDroidMapSourceListener,
    AltosLaunchSiteListener,
    AltosDroidSelectedSerialListener
{

    private boolean mapLoaded;
    private boolean launch_sites_set;

    private AltosLatLon my_position = null;
    private AltosLatLon target_position = null;
    private AltosLatLon center_position = null;
    private int center_serial = AltosDroidPreferences.SELECT_AUTO;     /* when not SELECT_AUTO, center on this the first time we can */
    public int target_serial = AltosDroidPreferences.SELECT_AUTO;
    private FragmentMapBinding binding;

    private final static int CENTER_NONE = 0;
    private final static int CENTER_PASTURE = 1;
    private final static int CENTER_RECEIVER = 2;
    private final static int CENTER_TARGET = 3;

    private int center_priority = CENTER_NONE;

    private AltosDroidMapInterface mapInterface;
    private AltosDroidMapOnline mapOnline;
    public AltosLaunchSites launchSites;
    List<AltosLaunchSite> sites;

    public void check_permission() {
        if (altos_droid == null)
            return;
        if (altos_droid.perm_access_fine_location.have)
            position_permission();
        else
            altos_droid.tell_map_permission(this);
    }

    public void set_altos_droid(MainActivity altos_droid) {
        this.altos_droid = altos_droid;
        if (mapInterface != null) {
            mapInterface.set_altos_droid(altos_droid);
            check_permission();
        }
    }

    static int[] altos_map_types = { AltosMap.maptype_roadmap, AltosMap.maptype_satellite, AltosMap.maptype_hybrid, AltosMap.maptype_terrain};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        if (launchSites == null)
            launchSites = new AltosLaunchSites(this);

        return binding.getRoot();
    }

    boolean have_google_maps() {
        PackageManager pm = getContext().getPackageManager();
        try {
            pm.getPackageInfo("com.google.android.gms", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        check_permission();

        String[] map_types = getResources().getStringArray(R.array.map_types);

        ArrayAdapter adapter = new ArrayAdapter(getContext(), R.layout.spinner, map_types);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        binding.mapType.setAdapter(adapter);

        if (savedInstanceState == null) {
            int map_type = AltosPreferences.map_type();
            for (int map_id = 0; map_id < altos_map_types.length; map_id++)
                if (altos_map_types[map_id] == map_type) {
                    binding.mapType.setSelection(map_id);
                    break;
                }
        }

        binding.mapType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position < altos_map_types.length)
                        AltosPreferences.set_map_type(altos_map_types[position]);
                }
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        binding.mapSource.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    int source = isChecked ? AltosDroidPreferences.MAP_SOURCE_ONLINE : AltosDroidPreferences.MAP_SOURCE_OFFLINE;
                    AltosDroidPreferences.set_map_source(source);
                }
            });

        if (!have_google_maps())
        {
            if (AltosDroidPreferences.map_source() == AltosDroidPreferences.MAP_SOURCE_ONLINE)
                AltosDroidPreferences.set_map_source(AltosDroidPreferences.MAP_SOURCE_OFFLINE);
            binding.mapSource.setEnabled(false);
        }

        AltosDroidPreferences.register_map_source_listener(this);
        AltosDroidPreferences.register_selected_serial_listener(this);

        map_source_changed(AltosDroidPreferences.map_source());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapInterface != null)
            mapInterface.deactivate();
        AltosDroidPreferences.unregister_map_source_listener(this);
        AltosDroidPreferences.unregister_selected_serial_listener(this);
        mapLoaded = false;
        binding = null;
    }

    long last_user_input;

    public void user_motion() {
        last_user_input = System.currentTimeMillis();
    }

    public long time_since_user_motion() {
        return System.currentTimeMillis() - last_user_input;
    }

    public void position_permission() {
        if (mapInterface != null)
            mapInterface.position_permission();
    }

    public void selected_serial_changed(int serial, long time) {
        center_priority = CENTER_NONE;
    }

    public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
        AltosLatLon new_center = null;
        boolean reset_zoom = true;

        if (telem_state != null && mapInterface != null) {
            mapInterface.set_telem_state(telem_state);
        }

        if (state != null) {
            AltosCalData cal_data = state.cal_data();

            if (state.pad_lat != AltosLib.MISSING && mapInterface != null)
                mapInterface.set_pad_position(state.pad_lat, state.pad_lon);

            if (cal_data != null) {
                target_serial = cal_data.serial;
                if (state.gps != null && state.gps.locked && state.gps.nsat >= 4 && state.gps.lat != AltosLib.MISSING) {
                    long time_since_motion = time_since_user_motion();
                    if (center_priority < CENTER_TARGET || center_serial != target_serial) {
                        new_center = new AltosLatLon(state.gps.lat, state.gps.lon);
                        center_priority = CENTER_TARGET;
                        center_serial = target_serial;
                    } else if (time_since_motion > 30000) {
                        reset_zoom = false;
                        new_center = new AltosLatLon(state.gps.lat, state.gps.lon);
                    }
                }
            }

            if (state.gps != null && state.gps.lat != AltosLib.MISSING) {
                if (binding != null) {
                    String lat_text = AltosValue.pos(state.gps.lat, "N", "S");
                    String lon_text = AltosValue.pos(state.gps.lon, "E", "W");
                    binding.mapTargetPosition.setText(lat_text + "\n" + lon_text);
                }
                target_position = new AltosLatLon(state.gps.lat, state.gps.lon);
            }
        }

        if (receiver_location != null) {
            double accuracy;

            if (receiver_location.hasAccuracy())
                accuracy = receiver_location.getAccuracy();
            else
                accuracy = 1000;

            my_position = new AltosLatLon(receiver_location.getLatitude(), receiver_location.getLongitude());

            if (binding != null) {
                String lat_text = AltosValue.pos(receiver_location.getLatitude(), "N", "S");
                String lon_text = AltosValue.pos(receiver_location.getLongitude(), "E", "W");
                binding.mapReceiverPosition.setText(lat_text + "\n" + lon_text);
            }

            if (center_priority < CENTER_RECEIVER) {
                center_priority = CENTER_RECEIVER;
                new_center = my_position;
            }
        }

        if (center_priority < CENTER_PASTURE) {
            center_priority = CENTER_PASTURE;
            new_center = new AltosLatLon(37.167833333, -97.73975);
        }

        if (new_center != null && mapInterface != null) {
            mapInterface.center(new_center.lat, new_center.lon, reset_zoom);
            center_position = new_center;
            user_motion();
        }

        if (my_position != null && mapInterface != null)
            mapInterface.set_here_position(my_position.lat, my_position.lon);

        if (target_position != null && mapInterface != null)
            mapInterface.set_there_position(target_position.lat, target_position.lon);

        if (from_receiver != null && binding != null) {
            binding.mapBearing.setText(String.format(Locale.getDefault(), "%1.0f°", from_receiver.bearing));
            set_value(binding.mapDistance, AltosConvert.distance, 1, from_receiver.distance);
        }
        if (launch_sites_set && mapInterface != null) {
            mapInterface.set_launch_sites(sites);
            launch_sites_set = false;
        }
    }

    public void map_source_changed(int map_source) {
        if (mapInterface != null)
            mapInterface.deactivate();
        mapInterface = null;
        int child = 0;
        if (AltosDroidPreferences.map_source() == AltosDroidPreferences.MAP_SOURCE_ONLINE) {
            if (mapOnline == null)
                mapOnline = new AltosDroidMapOnline(this, getContext());
            mapInterface = mapOnline;
            child = 0;
        } else {
            mapInterface = binding.mapOffline;
            child = 1;
        }
        mapInterface.activate(this);
        center_serial = AltosDroidPreferences.SELECT_AUTO;
        binding.mapSource.setChecked(map_source == AltosDroidPreferences.MAP_SOURCE_ONLINE);
        binding.mapType.setEnabled(map_source == AltosDroidPreferences.MAP_SOURCE_ONLINE);
        mapInterface.set_altos_droid(altos_droid);
        if (sites != null)
            mapInterface.set_launch_sites(sites);
        binding.mapView.setDisplayedChild(child);
        if (altos_droid != null)
            altos_droid.update_state(null);
    }

    public void notify_launch_sites(List<AltosLaunchSite> sites) {
        this.sites = sites;
        launch_sites_set = true;
    }

    @Override
    public String name() { return MainActivity.map_name; }
}
