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
import org.altusmetrum.altoslib_14.AltosState;

import java.util.Locale;

public class RecoverFragment extends AltosFragment {

    private FragmentRecoverBinding binding;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		RecoverViewModel recoverViewModel =
				new ViewModelProvider(this).get(RecoverViewModel.class);

		binding = FragmentRecoverBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}
    @Override
    public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
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

		if (state != null) {
			set_value(binding.maxHeightValue, AltosConvert.height, 1, state.max_height());
            set_value(binding.maxSpeedValue, AltosConvert.speed, 1, state.max_speed());
            set_value(binding.maxAccelValue, AltosConvert.accel, 1, state.max_acceleration());
		}
	}

	@Override
	public String name() { return MainActivity.recover_name; }
}
