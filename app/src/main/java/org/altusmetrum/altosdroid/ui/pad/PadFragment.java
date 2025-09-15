package org.altusmetrum.altosdroid.ui.pad;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.databinding.FragmentPadBinding;
import org.altusmetrum.altosdroid.ui.pad.PadViewModel;

public class PadFragment extends Fragment {

    private FragmentPadBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        PadViewModel padViewModel =
                new ViewModelProvider(this).get(PadViewModel.class);

        binding = FragmentPadBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

//        final TextView textView = binding.textPad;
//        padViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

