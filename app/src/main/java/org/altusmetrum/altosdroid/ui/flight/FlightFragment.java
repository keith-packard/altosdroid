package org.altusmetrum.altosdroid.ui.flight;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.databinding.FragmentFlightBinding;

public class FlightFragment extends Fragment {

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
}