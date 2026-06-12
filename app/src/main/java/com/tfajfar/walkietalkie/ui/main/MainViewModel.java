package com.tfajfar.walkietalkie.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tfajfar.walkietalkie.data.RecordingRepository;
import com.tfajfar.walkietalkie.data.SettingsRepository;

public class MainViewModel extends AndroidViewModel {
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<String>("Disconnected");
    private final MutableLiveData<String> connectionDesc = new MutableLiveData<String>("No active connection.");
    private final MutableLiveData<Integer> connectedPeersCount = new MutableLiveData<Integer>(0);
    private final MutableLiveData<Integer> audioLevel = new MutableLiveData<Integer>(0);
    private final MutableLiveData<Boolean> isTalking = new MutableLiveData<Boolean>(false);

    private final SettingsRepository settingsRepository;
    private final RecordingRepository recordingRepository;

    public MainViewModel(@NonNull Application application) {
        super(application);
        settingsRepository = SettingsRepository.getInstance(application);
        recordingRepository = RecordingRepository.getInstance();
    }

    public LiveData<String> getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String status) {
        connectionStatus.setValue(status);
    }

    public LiveData<String> getConnectionDesc() {
        return connectionDesc;
    }

    public void setConnectionDesc(String desc) {
        connectionDesc.setValue(desc);
    }

    public LiveData<Integer> getConnectedPeersCount() {
        return connectedPeersCount;
    }

    public void setConnectedPeersCount(int count) {
        connectedPeersCount.setValue(count);
    }

    public LiveData<Integer> getAudioLevel() {
        return audioLevel;
    }

    public void setAudioLevel(int level) {
        audioLevel.setValue(level);
    }

    public LiveData<Boolean> getIsTalking() {
        return isTalking;
    }

    public void setIsTalking(boolean talking) {
        isTalking.setValue(talking);
    }

    public boolean isRecordTransmissionsEnabled() {
        return settingsRepository.isRecordTransmissionsEnabled();
    }

    public void setRecordTransmissionsEnabled(boolean enabled) {
        settingsRepository.setRecordTransmissionsEnabled(enabled);
    }

    public String getRecordingDirectoryPath() {
        return recordingRepository.getRecordingDir().getAbsolutePath();
    }

    public String createOutgoingRecordingPath() {
        return recordingRepository.createOutgoingRecordingPath();
    }
}
