package org.altusmetrum.altosdroid.ui.recover;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class RecoverViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public RecoverViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is recover fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}