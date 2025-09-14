package org.altusmetrum.altosdroid.ui.pad;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PadViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public PadViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is pad fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}

