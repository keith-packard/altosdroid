package org.altusmetrum.altosdroid.ui.recover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.altusmetrum.altosdroid.databinding.FragmentRecoverBinding;

public class RecoverFragment extends Fragment {

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
}