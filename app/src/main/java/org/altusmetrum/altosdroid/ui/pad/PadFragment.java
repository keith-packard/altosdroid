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

package org.altusmetrum.altosdroid.ui.pad;

import static java.lang.Math.min;

import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.AltosDebug;
import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.AltosValue;
import org.altusmetrum.altosdroid.AltosVoltMeter;
import org.altusmetrum.altosdroid.GoNoGoLights;
import org.altusmetrum.altosdroid.GoNoGoLights;
import org.altusmetrum.altosdroid.MainActivity;
import org.altusmetrum.altosdroid.R;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentPadBinding;
import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.Locale;

public class PadFragment extends AltosFragment {

    private FragmentPadBinding binding;

    private AltosVoltMeter battery_meter;
    private AltosVoltMeter receiver_meter;
    private AltosVoltMeter  apogee_meter;
    private AltosVoltMeter  main_meter;
    private GoNoGoLights data_logging_lights;
    private GoNoGoLights gps_locked_lights;
    private GoNoGoLights gps_ready_lights;
    private AltosVoltMeter [] ignite_meters;

    private double target_altitude = AltosLib.MISSING;
    private TelemetryState last_telem_state;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        PadViewModel padViewModel =
            new ViewModelProvider(this).get(PadViewModel.class);

        binding = FragmentPadBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Resources resources = getResources();

        battery_meter = new AltosVoltMeter (AltosLib.ao_battery_good, binding.batteryView, binding.batteryRedled, binding.batteryGreenled, binding.batteryVoltageValue, resources);
        receiver_meter = new AltosVoltMeter (AltosLib.ao_battery_good, binding.receiverView, binding.receiverRedled, binding.receiverGreenled, binding.receiverVoltageValue,resources);
        apogee_meter = new AltosVoltMeter (AltosLib.ao_igniter_good, binding.apogeeView, binding.apogeeRedled, binding.apogeeGreenled, binding.apogeeVoltageValue, resources);
        main_meter = new AltosVoltMeter (AltosLib.ao_igniter_good, binding.mainView, binding.mainRedled, binding.mainGreenled, binding.mainVoltageValue, resources);
        data_logging_lights = new GoNoGoLights(binding.loggingRedled, binding.loggingGreenled, resources);
        gps_locked_lights = new GoNoGoLights (binding.gpsLockedRedled, binding.gpsLockedGreenled, resources);
        gps_ready_lights = new GoNoGoLights (binding.gpsReadyRedled, binding.gpsReadyGreenled, resources);

        AltosVoltMeter[] local_igniters = {
            new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteAView, binding.igniteARedled, binding.igniteAGreenled, binding.igniteAVoltageValue, resources),
            new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteBView, binding.igniteBRedled, binding.igniteBGreenled, binding.igniteBVoltageValue, resources),
            new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteCView, binding.igniteCRedled, binding.igniteCGreenled, binding.igniteCVoltageValue, resources),
            new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteDView, binding.igniteDRedled, binding.igniteDGreenled, binding.igniteDVoltageValue, resources),
        };

        ignite_meters = local_igniters;

        binding.setPadAltitude.setOnClickListener(v -> setPadAltitude());

        return root;
    }

    void setPadAltitude() {
        if (target_altitude != AltosLib.MISSING && last_telem_state != null)
            last_telem_state.set_gps_ground_altitude(target_altitude);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
        if (binding == null)
            return;
        if (state != null) {
            // battery voltage
            battery_meter.set(state.battery_voltage);
            // apogee voltage
            apogee_meter.set(state.apogee_voltage);
            // main voltage
            main_meter.set(state.main_voltage);
            // igniter voltages
            int num_igniter = state.igniter_voltage == null ? 0 : state.igniter_voltage.length;

            for (int i = 0; i < ignite_meters.length; i++) {
                double voltage = i < num_igniter ? state.igniter_voltage[i] : AltosLib.MISSING;
                ignite_meters[i].set(voltage);
            }
            String product = state.cal_data().product;
            String version = state.cal_data().firmware_version;
            binding.productValue.setText(product != null ? product : "");
            binding.versionValue.setText(version != null ? version : "");
            // recording status
            if (state.cal_data().flight != 0) {
                if (state.state() <= AltosLib.ao_flight_pad)
                    binding.loggingValue.setText("Ready to record");
                else if (state.state() < AltosLib.ao_flight_landed)
                    binding.loggingValue.setText("Recording data");
                else
                    binding.loggingValue.setText("Recorded data");
            } else {
                binding.loggingValue.setText("Storage full");
            }
            data_logging_lights.set(state.cal_data().flight != 0, state.cal_data().flight == AltosLib.MISSING);
            // gps status
            if (state.gps != null) {
                int soln = state.gps.nsat;
                int nsat = state.gps.cc_gps_sat != null ? state.gps.cc_gps_sat.length : 0;
                binding.gpsLockedValue.setText(String.format(Locale.getDefault(), "%d in soln, %d in view", soln, nsat));
                gps_locked_lights.set(state.gps.locked && state.gps.nsat >= 4, false);
                if (state.gps_ready)
                    binding.gpsReadyValue.setText("Ready");
                else
                    binding.gpsReadyValue.setText(AltosValue.integer("Waiting %d", state.gps_waiting));
            }
            binding.gpsLockedView.setVisibility(state.gps != null ? View.VISIBLE : View.GONE);
            gps_ready_lights.set(state.gps_ready, state.gps == null);
            binding.gpsReadyView.setVisibility(state.gps != null ? View.VISIBLE : View.GONE);

            double orient = state.orient();

            if (orient == AltosLib.MISSING) {
                binding.tiltView.setVisibility(View.GONE);
            } else {
                binding.tiltValue.setText(AltosValue.number("%1.0f°", orient));
                binding.tiltView.setVisibility(View.VISIBLE);
            }
        }

        // report receiver battery voltage
        if (telem_state != null) {
            receiver_meter.set(telem_state.receiver_battery);
        }

        // report our location if available
        if (receiver_location != null) {
            double altitude = AltosLib.MISSING;
            if (telem_state.gps_ground_altitude != AltosLib.MISSING)
                altitude = telem_state.gps_ground_altitude;
            else if (telem_state.receiver_location.hasAltitude())
                altitude = telem_state.receiver_location.getAltitude();
            String lat_text = AltosValue.pos(telem_state.receiver_location.getLatitude(), "N", "S");
            String lon_text = AltosValue.pos(telem_state.receiver_location.getLongitude(), "E", "W");
            binding.receiverLatValue.setText(lat_text);
            binding.receiverLonValue.setText(lon_text);
            set_value(binding.receiverAltValue, AltosConvert.height, 1, altitude);
        }

        // report target location if available
        if (state != null && state.gps != null && state.gps.lat != AltosLib.MISSING && state.gps.locked) {
            double altitude = state.gps.alt;
            String lat_text = AltosValue.pos(state.gps.lat, "N", "S");
            String lon_text = AltosValue.pos(state.gps.lon, "E", "W");
            binding.targetLatValue.setText(lat_text);
            binding.targetLonValue.setText(lon_text);
            set_value(binding.targetAltValue, AltosConvert.height, 1, altitude);
            binding.targetPosView.setVisibility(View.VISIBLE);

            /*
             * For TeleGPS, when using a ground station without GPS, provide a way to
             * set the pad altitude from the target GPS altitude instead of
             * letting the state machine pick some random altitude
             */
            if (state.state() == AltosLib.ao_flight_stateless &&
                telem_state != null &&
                !telem_state.receiver_has_gps && altitude != AltosLib.MISSING)
            {
                binding.setPadAltitude.setVisibility(View.VISIBLE);
                binding.targetAltLabel.setVisibility(View.GONE);
                last_telem_state = telem_state;
                target_altitude = altitude;
            } else {
                binding.setPadAltitude.setVisibility(View.GONE);
                binding.targetAltLabel.setVisibility(View.VISIBLE);
            }

        } else {
            binding.targetPosView.setVisibility(View.GONE);
        }
    }

    @Override
    public int view() { return TelemetryState.VIEW_PAD; }
}

