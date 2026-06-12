package com.tfajfar.walkietalkie.ui.recording;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tfajfar.walkietalkie.data.RecordingRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RecordingsViewModel extends ViewModel {
    private final RecordingRepository recordingRepository = RecordingRepository.getInstance();
    private final MutableLiveData<List<File>> recordings = new MutableLiveData<List<File>>(new ArrayList<File>());

    public LiveData<List<File>> getRecordings() {
        return recordings;
    }

    public void loadRecordings() {
        recordings.setValue(recordingRepository.getRecordings());
    }
}
