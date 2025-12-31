package org.altusmetrum.altosdroid.ui.pad;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.AltosDebug;
import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.GoNoGoLights;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentPadBinding;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

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


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        PadViewModel padViewModel =
                new ViewModelProvider(this).get(PadViewModel.class);

        binding = FragmentPadBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        battery_lights = new GoNoGoLights(binding.batteryRedled, binding.batteryGreenled, getResources());
        receiver_voltage_lights = new GoNoGoLights(binding.receiverRedled, binding.receiverGreenled, getResources());
        apogee_lights = new GoNoGoLights(binding.apogeeRedled, binding.apogeeGreenled, getResources());
        main_lights = new GoNoGoLights(binding.mainRedled, binding.mainGreenled, getResources());
        data_logging_lights = new GoNoGoLights(binding.loggingRedled, binding.loggingGreenled, getResources());
        gps_locked_lights = new GoNoGoLights(binding.gpsLockedRedled, binding.gpsLockedGreenled, getResources());
        gps_ready_lights = new GoNoGoLights(binding.gpsReadyRedled, binding.gpsReadyGreenled, getResources());

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
            binding.batteryVoltageValue.setText(String.format("%1.2f V", state.battery_voltage));
            battery_lights.set(state.battery_voltage >= AltosLib.ao_battery_good, state.battery_voltage == AltosLib.MISSING);
            if (state.apogee_voltage != AltosLib.MISSING) {
                binding.apogeeVoltageValue.setText(String.format("%1.2f V", state.apogee_voltage));
                apogee_lights.set(state.apogee_voltage >= AltosLib.ao_igniter_good, state.apogee_voltage == AltosLib.MISSING);
                binding.apogeeRow.setVisibility(View.VISIBLE);
            } else {
                binding.apogeeRow.setVisibility(View.GONE);
            }
            if (state.main_voltage != AltosLib.MISSING) {
                binding.mainVoltageValue.setText(String.format("%1.2f V", state.main_voltage));
                main_lights.set(state.main_voltage >= AltosLib.ao_igniter_good, state.main_voltage == AltosLib.MISSING);
                binding.mainRow.setVisibility(View.VISIBLE);
            } else {
                binding.mainRow.setVisibility(View.GONE);
            }
        }
    }

}

