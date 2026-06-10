package com.tfajfar.walkietalkie.ui.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("Disconnected");
    private final MutableLiveData<String> connectionDesc = new MutableLiveData<>("No active connection.");
    private final MutableLiveData<Integer> connectedPeersCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> audioLevel = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isTalking = new MutableLiveData<>(false);

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
}
