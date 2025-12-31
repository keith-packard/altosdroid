package org.altusmetrum.altosdroid.ui.pad;

import static java.lang.Math.min;

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
import org.altusmetrum.altosdroid.GoNoGoLights;
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

    private GoNoGoLights battery_lights;
    private GoNoGoLights receiver_voltage_lights;
    private GoNoGoLights apogee_lights;
    private GoNoGoLights main_lights;
    private GoNoGoLights data_logging_lights;
    private GoNoGoLights gps_locked_lights;
    private GoNoGoLights gps_ready_lights;
    private GoNoGoLights[] ignite_lights = new GoNoGoLights[4];

    static class Igniter {
        TableRow row;
        ImageView green;
        ImageView red;
        TextView value;
        GoNoGoLights lights;

        Igniter(TableRow row, ImageView green, ImageView red, TextView value) {
            this.row = row;
            this.green = green;
            this.red = red;
            this.value = value;
        }
    };

    Igniter[] igniters;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        PadViewModel padViewModel =
                new ViewModelProvider(this).get(PadViewModel.class);

        binding = FragmentPadBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Igniter[] local_igniters = {
                new Igniter(binding.igniteARow, binding.igniteAGreenled, binding.igniteARedled, binding.igniteAVoltageValue),
                new Igniter(binding.igniteBRow, binding.igniteBGreenled, binding.igniteBRedled, binding.igniteBVoltageValue),
                new Igniter(binding.igniteCRow, binding.igniteCGreenled, binding.igniteCRedled, binding.igniteCVoltageValue),
                new Igniter(binding.igniteDRow, binding.igniteDGreenled, binding.igniteDRedled, binding.igniteDVoltageValue),
        };

        igniters = local_igniters;

        battery_lights = new GoNoGoLights(binding.batteryRedled, binding.batteryGreenled, getResources());
        receiver_voltage_lights = new GoNoGoLights(binding.receiverRedled, binding.receiverGreenled, getResources());
        apogee_lights = new GoNoGoLights(binding.apogeeRedled, binding.apogeeGreenled, getResources());
        main_lights = new GoNoGoLights(binding.mainRedled, binding.mainGreenled, getResources());
        data_logging_lights = new GoNoGoLights(binding.loggingRedled, binding.loggingGreenled, getResources());
        gps_locked_lights = new GoNoGoLights(binding.gpsLockedRedled, binding.gpsLockedGreenled, getResources());
        gps_ready_lights = new GoNoGoLights(binding.gpsReadyRedled, binding.gpsReadyGreenled, getResources());

        for (int i = 0; i < igniters.length; i++) {
            igniters[i].lights = new GoNoGoLights(igniters[i].red, igniters[i].green, getResources());
        }

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
        AltosDebug.debug("Pad: update_ui()");
        if (state != null) {
            // battery voltage
            binding.batteryVoltageValue.setText(String.format("%1.2f V", state.battery_voltage));
            battery_lights.set(state.battery_voltage >= AltosLib.ao_battery_good, state.battery_voltage == AltosLib.MISSING);
            // apogee voltage
            if (state.apogee_voltage != AltosLib.MISSING) {
                binding.apogeeVoltageValue.setText(String.format("%1.2f V", state.apogee_voltage));
                apogee_lights.set(state.apogee_voltage >= AltosLib.ao_igniter_good, state.apogee_voltage == AltosLib.MISSING);
                binding.apogeeRow.setVisibility(View.VISIBLE);
            } else {
                binding.apogeeRow.setVisibility(View.GONE);
            }
            // main voltage
            if (state.main_voltage != AltosLib.MISSING) {
                binding.mainVoltageValue.setText(String.format("%1.2f V", state.main_voltage));
                main_lights.set(state.main_voltage >= AltosLib.ao_igniter_good, state.main_voltage == AltosLib.MISSING);
                binding.mainRow.setVisibility(View.VISIBLE);
            } else {
                binding.mainRow.setVisibility(View.GONE);
            }
            // igniter voltages
            int num_igniter = state.igniter_voltage == null ? 0 : state.igniter_voltage.length;

            for (int i = 0; i < igniters.length; i++) {
                double voltage = i < num_igniter ? state.igniter_voltage[i] : AltosLib.MISSING;
                if (voltage != AltosLib.MISSING) {
                    igniters[i].value.setText(String.format("%1.2f V", voltage));
                    igniters[i].lights.set(voltage >= AltosLib.ao_igniter_good, voltage == AltosLib.MISSING);
                    igniters[i].row.setVisibility(View.VISIBLE);
                } else {
                    igniters[i].row.setVisibility(View.GONE);
                }
            }
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
			} else
				gps_locked_lights.set(false, true);
			gps_ready_lights.set(state.gps_ready, state.gps == null);

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
            if (telem_state.receiver_battery != AltosLib.MISSING) {
                binding.receiverVoltageValue.setText(String.format("%1.2f V", telem_state.receiver_battery));
                binding.receiverRow.setVisibility(View.VISIBLE);
            } else {
                binding.receiverRow.setVisibility(View.GONE);
            }
            receiver_voltage_lights.set(telem_state.receiver_battery >= AltosLib.ao_battery_good, telem_state.receiver_battery == AltosLib.MISSING);
        }

        // report our location if available
        if (receiver_location != null) {
            double altitude = AltosLib.MISSING;
			if (receiver_location.hasAltitude())
				altitude = receiver_location.getAltitude();
			String lat_text = AltosValue.pos(receiver_location.getLatitude(), "N", "S");
			String lon_text = AltosValue.pos(receiver_location.getLongitude(), "E", "W");
			binding.receiverLatValue.setText(lat_text);
			binding.receiverLonValue.setText(lon_text);
			set_value(binding.receiverAltValue, AltosConvert.height, 1, altitude);
        }
    }
}

