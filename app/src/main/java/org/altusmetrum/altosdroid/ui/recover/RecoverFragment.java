package org.altusmetrum.altosdroid.ui.recover;

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
import org.altusmetrum.altosdroid.databinding.FragmentRecoverBinding;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosState;

public class RecoverFragment extends AltosFragment {

    private FragmentRecoverBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        RecoverViewModel recoverViewModel =
                new ViewModelProvider(this).get(RecoverViewModel.class);

        binding = FragmentRecoverBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

//        final TextView textView = binding.textRecover;
//        recoverViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
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