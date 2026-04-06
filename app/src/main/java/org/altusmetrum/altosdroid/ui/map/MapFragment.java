package org.altusmetrum.altosdroid.ui.map;

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



public class MapFragment extends AltosFragment implements AltosDroidMapSourceListener, AltosLaunchSiteListener {

    private boolean mapLoaded;
    private boolean launch_sites_set;

    private AltosLatLon my_position = null;
    private AltosLatLon target_position = null;
    public int target_serial = -1;
    private FragmentMapBinding binding;

    private AltosDroidMapInterface mapInterface;
    private AltosDroidMapOnline mapOnline;
    public AltosLaunchSites launchSites;
    List<AltosLaunchSite> sites;

    public void check_permission() {
        if (altos_droid == null)
            return;
        if (altos_droid.have_location_permission)
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        check_permission();
        AltosDroidPreferences.register_map_source_listener(this);

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

        map_source_changed(AltosDroidPreferences.map_source());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapInterface != null)
            mapInterface.deactivate();
        AltosDroidPreferences.unregister_map_source_listener(this);
        mapLoaded = false;
        binding = null;
    }

    public void position_permission() {
        if (mapInterface != null)
            mapInterface.position_permission();
    }

    double mapAccuracy = -1;

    void center(double lat, double lon, double accuracy) {
        if (mapInterface != null) {
            if (mapAccuracy < 0 || accuracy < mapAccuracy/10) {
                mapInterface.center(lat, lon, accuracy);
                mapAccuracy = accuracy;
            }
        }
    }

    public void select_tracker(int serial) {
        mapAccuracy = -1;
    }

    public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
        if (telem_state != null && mapInterface != null) {
            mapInterface.set_telem_state(telem_state);
        }

        if (state != null) {
            AltosCalData cal_data = state.cal_data();

            if (cal_data != null && cal_data.serial != AltosLib.MISSING)
                target_serial = cal_data.serial;
            if (state.pad_lat != AltosLib.MISSING && mapInterface != null)
                mapInterface.set_pad_position(state.pad_lat, state.pad_lon);

            if (state.gps != null && state.gps.lat != AltosLib.MISSING) {
                if (binding != null) {
                    String lat_text = AltosValue.pos(state.gps.lat, "N", "S");
                    String lon_text = AltosValue.pos(state.gps.lon, "E", "W");
                    binding.mapTargetPosition.setText(lat_text + "\n" + lon_text);
                }
                target_position = new AltosLatLon(state.gps.lat, state.gps.lon);
                if (state.gps.locked && state.gps.nsat >= 4)
                    center (state.gps.lat, state.gps.lon, 10);
            }
        }

        if (receiver_location != null) {
            double accuracy;

            if (receiver_location.hasAccuracy())
                accuracy = receiver_location.getAccuracy();
            else
                accuracy = 1000;

            my_position = new AltosLatLon(receiver_location.getLatitude(), receiver_location.getLongitude());
            if (target_position == null)
                center (my_position.lat, my_position.lon, accuracy);

            if (binding != null) {
                String lat_text = AltosValue.pos(receiver_location.getLatitude(), "N", "S");
                String lon_text = AltosValue.pos(receiver_location.getLongitude(), "E", "W");
                binding.mapReceiverPosition.setText(lat_text + "\n" + lon_text);
            }
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
        mapAccuracy = -1;
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
