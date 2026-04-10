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

package org.altusmetrum.altosdroid.ui.recover;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.AltosDebug;
import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.AltosValue;
import org.altusmetrum.altosdroid.MainActivity;
import org.altusmetrum.altosdroid.R;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentRecoverBinding;
import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.Locale;

public class RecoverFragment extends AltosFragment {

    private FragmentRecoverBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        RecoverViewModel recoverViewModel =
            new ViewModelProvider(this).get(RecoverViewModel.class);

        binding = FragmentRecoverBinding.inflate(inflater, container, false);
        return binding.getRoot();
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
        if (from_receiver != null) {
            binding.bearingValue.setText(String.format(Locale.getDefault(), "%1.0f°", from_receiver.bearing));
            set_value(binding.distanceValue, AltosConvert.distance, 1, from_receiver.distance);
            String direction = AltosValue.direction(from_receiver, receiver_location, getResources());
            if (direction == null)
                binding.directionValue.setText("");
            else
                binding.directionValue.setText(direction);
        }
        if (state != null && state.gps != null) {
            binding.targetLatValue.setText(AltosValue.pos(state.gps.lat, "N", "S"));
            binding.targetLonValue.setText(AltosValue.pos(state.gps.lon, "E", "W"));
        }

        if (receiver_location != null) {
            binding.receiverLatValue.setText(AltosValue.pos(receiver_location.getLatitude(), "N", "S"));
            binding.receiverLonValue.setText(AltosValue.pos(receiver_location.getLongitude(), "E", "W"));
        }

        binding.maxGpsSpeedView.setVisibility(View.GONE);
        binding.maxGpsHeightView.setVisibility(View.GONE);
        binding.maxGpsAltitudeView.setVisibility(View.GONE);
        if (state != null) {
            set_value(binding.maxHeightValue, AltosConvert.height, 1, state.max_height());
            set_value(binding.maxSpeedValue, AltosConvert.speed, 1, state.max_speed());
            set_value(binding.maxAccelValue, AltosConvert.accel, 1, state.max_acceleration());
            if (state.gps != null) {
                binding.maxGpsSpeedView.setVisibility(View.VISIBLE);
                binding.maxGpsHeightView.setVisibility(View.VISIBLE);
                binding.maxGpsAltitudeView.setVisibility(View.VISIBLE);
                double max_gps_altitude = AltosLib.MISSING;
                double max_gps_height = AltosLib.MISSING;
                double max_gps_speed = AltosLib.MISSING;
                max_gps_altitude = state.max_gps_altitude();
                max_gps_height = state.max_gps_height();
                max_gps_speed = state.max_gps_speed();
                if (state.gps.locked) {
                    max_gps_altitude = state.max_gps_altitude();
                    max_gps_height = state.max_gps_height();
                    max_gps_speed = state.max_gps_speed();
                }
                set_value(binding.maxGpsSpeedValue, AltosConvert.speed, 1, max_gps_speed);
                set_value(binding.maxGpsHeightValue, AltosConvert.height, 1, max_gps_height);
                set_value(binding.maxGpsAltitudeValue, AltosConvert.height, 1, max_gps_altitude);
            }
        }
    }

    @Override
    public String name() { return MainActivity.recover_name; }
}
