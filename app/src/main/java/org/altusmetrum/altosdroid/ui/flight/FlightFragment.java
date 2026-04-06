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

package org.altusmetrum.altosdroid.ui.flight;

import android.content.res.Resources;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.AltosDebug;
import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.AltosValue;
import org.altusmetrum.altosdroid.AltosVoltMeter;
import org.altusmetrum.altosdroid.GoNoGoLights;
import org.altusmetrum.altosdroid.MainActivity;
import org.altusmetrum.altosdroid.R;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentFlightBinding;
import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

public class FlightFragment extends AltosFragment {

	private FragmentFlightBinding binding;
	private AltosVoltMeter apogee_meter;
	private AltosVoltMeter  main_meter;
	private AltosVoltMeter [] ignite_meters;

	public View onCreateView(@NonNull LayoutInflater inflater,
				 ViewGroup container, Bundle savedInstanceState) {
		FlightViewModel flightViewModel =
			new ViewModelProvider(this).get(FlightViewModel.class);

		binding = FragmentFlightBinding.inflate(inflater, container, false);
		View root = binding.getRoot();
		Resources resources = getResources();

		apogee_meter = new AltosVoltMeter (AltosLib.ao_igniter_good, binding.apogeeView, binding.apogeeRedled, binding.apogeeGreenled, binding.apogeeVoltageValue, resources);
		main_meter = new AltosVoltMeter (AltosLib.ao_igniter_good, binding.mainView, binding.mainRedled, binding.mainGreenled, binding.mainVoltageValue, resources);

		AltosVoltMeter[] local_igniters = {
			new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteAView, binding.igniteARedled, binding.igniteAGreenled, binding.igniteAVoltageValue, resources),
			new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteBView, binding.igniteBRedled, binding.igniteBGreenled, binding.igniteBVoltageValue, resources),
			new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteCView, binding.igniteCRedled, binding.igniteCGreenled, binding.igniteCVoltageValue, resources),
			new AltosVoltMeter(AltosLib.ao_igniter_good, binding.igniteDView, binding.igniteDRedled, binding.igniteDGreenled, binding.igniteDVoltageValue, resources),
		};

		ignite_meters = local_igniters;
		return root;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}

	@Override
	public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
		if (state != null) {
			String state_name = null;
			if (state.state() == AltosLib.ao_flight_stateless) {
				binding.stateView.setVisibility(View.GONE);
			} else {
				state_name = state.state_name();
				binding.stateValue.setText(state_name);
				binding.stateView.setVisibility(View.VISIBLE);
			}
			set_value(binding.speedValue, AltosConvert.speed, 1, state.speed());
			set_value(binding.heightValue, AltosConvert.height, 1, state.height());
			set_value(binding.altitudeValue, AltosConvert.height, 1, state.altitude());
			if (state.gps == null) {
				binding.gpsSpeedView.setVisibility(View.GONE);
				binding.gpsHeightView.setVisibility(View.GONE);
				binding.gpsAltitudeView.setVisibility(View.GONE);
			} else {
				binding.gpsSpeedView.setVisibility(View.VISIBLE);
				binding.gpsHeightView.setVisibility(View.VISIBLE);
				binding.gpsAltitudeView.setVisibility(View.VISIBLE);
				double gps_altitude = AltosLib.MISSING;
				double gps_height = AltosLib.MISSING;
				double gps_speed = AltosLib.MISSING;
				gps_altitude = state.gps_altitude();
				gps_height = state.gps_height();
				gps_speed = state.gps_speed();
				if (state.gps.locked) {
					gps_altitude = state.gps_altitude();
					gps_height = state.gps_height();
					gps_speed = state.gps_speed();
				}
				set_value(binding.gpsSpeedValue, AltosConvert.speed, 1, gps_speed);
				set_value(binding.gpsHeightValue, AltosConvert.height, 1, gps_height);
				set_value(binding.gpsAltitudeValue, AltosConvert.height, 1, gps_altitude);
			}
			double orient = state.orient();
			if (orient == AltosLib.MISSING) {
				binding.tiltView.setVisibility(View.GONE);
			} else {
				binding.tiltValue.setText(AltosValue.number("%1.0f°", orient));
				binding.tiltView.setVisibility(View.VISIBLE);
			}
			/*
			set_value(binding.maxSpeedValue, AltosConvert.speed, 1, state.max_speed());
			set_value(binding.maxHeightValue, AltosConvert.height, 1, state.max_height());
			set_value(binding.maxAltitudeValue, AltosConvert.height, 1, state.max_altitude());
			*/
			if (from_receiver != null) {
				binding.elevationValue.setText(AltosValue.number("%1.0f°", from_receiver.elevation));
				set_value(binding.rangeValue, AltosConvert.distance, 1, from_receiver.range);
				binding.bearingValue.setText(AltosValue.number("%1.0f°", from_receiver.bearing));
				binding.compassValue.setText(from_receiver.bearing_words(AltosGreatCircle.BEARING_LONG));
				set_value(binding.distanceValue, AltosConvert.distance, 1, from_receiver.distance);
			} else {
				String unknown_value = getString(R.string.unknown_value);
				binding.elevationValue.setText(unknown_value);
				binding.elevationValue.setText(unknown_value);
				binding.rangeValue.setText(unknown_value);
				binding.bearingValue.setText(unknown_value);
				binding.compassValue.setText(unknown_value);
				binding.distanceValue.setText(unknown_value);
			}
			if (state.gps != null) {
				binding.latValue.setText(AltosValue.pos(state.gps.lat, "N", "S"));
				binding.lonValue.setText(AltosValue.pos(state.gps.lon, "E", "W"));
			}
			apogee_meter.set(state.apogee_voltage);
			// main voltage
			main_meter.set(state.main_voltage);
			// igniter voltages
			int num_igniter = state.igniter_voltage == null ? 0 : state.igniter_voltage.length;

			for (int i = 0; i < ignite_meters.length; i++) {
				double voltage = i < num_igniter ? state.igniter_voltage[i] : AltosLib.MISSING;
				ignite_meters[i].set(voltage);
			}
		}
	}

	@Override
	public String name() { return MainActivity.flight_name; }
}
