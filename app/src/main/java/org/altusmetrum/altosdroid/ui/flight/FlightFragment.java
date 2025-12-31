package org.altusmetrum.altosdroid.ui.flight;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentFlightBinding;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosState;

public class FlightFragment extends AltosFragment {

    private FragmentFlightBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        FlightViewModel flightViewModel =
                new ViewModelProvider(this).get(FlightViewModel.class);

        binding = FragmentFlightBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

 //       final TextView textView = binding.textFlight;
   //     flightViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {

    }
}