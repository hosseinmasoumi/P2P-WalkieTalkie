package com.tfajfar.walkietalkie.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.tfajfar.walkietalkie.data.SettingsRepository;

public class SettingsViewModel extends AndroidViewModel {
    private final SettingsRepository settingsRepository;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        settingsRepository = SettingsRepository.getInstance(application);
    }

    public boolean isRecordTransmissionsEnabled() {
        return settingsRepository.isRecordTransmissionsEnabled();
    }

    public void setRecordTransmissionsEnabled(boolean enabled) {
        settingsRepository.setRecordTransmissionsEnabled(enabled);
    }
}
