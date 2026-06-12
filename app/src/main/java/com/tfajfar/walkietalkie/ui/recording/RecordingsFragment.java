package com.tfajfar.walkietalkie.ui.recording;

import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingClickListener {

    private RecordingsAdapter adapter;
    private View emptyState;
    private ExtendedFloatingActionButton fabRecord;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recordings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emptyState = view.findViewById(R.id.tv_empty_recordings);

        RecyclerView rv = view.findViewById(R.id.rv_recordings);
        adapter = new RecordingsAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        // This button is only a small manual test recorder for the recordings screen.
        // The main test feature is still the Record Transmissions switch on the Talk screen.
        fabRecord = view.findViewById(R.id.fab_record_voice);
        fabRecord.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            else startRecording();
        });

        loadRecordings();
    }

    private File getRecordingDir() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return new File(musicDir, "P2PWalkieTalkie");
    }

    private void startRecording() {
        File dir = getRecordingDir();
        if (!dir.exists() && !dir.mkdirs()) {
            showMessage(getString(R.string.recording_failed));
            return;
        }

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, ts + "_OUT.amr");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        // Required by the test: 3GP container with AMR narrowband audio.
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(file.getAbsolutePath());

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            fabRecord.setText(R.string.btn_stop);
            fabRecord.setIconResource(R.drawable.ic_stop);
            showMessage(getString(R.string.recording_started));
        } catch (IOException | RuntimeException e) {
            releaseRecorder();
            showMessage(getString(R.string.recording_failed));
        }
    }

    private void stopRecording() {
        if (mediaRecorder == null) return;

        try {
            mediaRecorder.stop();
            showMessage(getString(R.string.recording_saved));
        } catch (RuntimeException e) {
            showMessage(getString(R.string.recording_failed));
        }

        releaseRecorder();
        fabRecord.setText(R.string.btn_record_voice);
        fabRecord.setIconResource(R.drawable.ic_mic);
        loadRecordings();
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        isRecording = false;
    }

    private void showMessage(String message) {
        View view = getView();
        if (view != null) Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isRecording) stopRecording();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecordings();
    }

    private void loadRecordings() {
        File dir = getRecordingDir();

        if (dir.exists()) {
            File[] files = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isFile() && f.getName().endsWith(".amr");
                }
            });

            if (files != null && files.length > 0) {
                List<File> fileList = new ArrayList<File>(Arrays.asList(files));
                Collections.sort(fileList, new Comparator<File>() {
                    @Override
                    public int compare(File f1, File f2) {
                        return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                    }
                });
                adapter.updateRecordings(fileList);
                if (emptyState != null) emptyState.setVisibility(View.GONE);
                return;
            }
        }

        adapter.updateRecordings(new ArrayList<File>());
        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRecordingClick(File file) {
        RecordingsFragmentDirections.ActionRecordingsToDetails action =
                RecordingsFragmentDirections.actionRecordingsToDetails(file.getAbsolutePath());
        Navigation.findNavController(requireView()).navigate(action);
    }

    @Override
    public void onRecordingLongClick(File file) {
        shareFile(file);
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {
            showMessage(getString(R.string.error_loading_audio));
        }
    }
}