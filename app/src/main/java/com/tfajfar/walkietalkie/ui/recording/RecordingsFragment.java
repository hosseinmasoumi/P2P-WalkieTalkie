package com.tfajfar.walkietalkie.ui.recording;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingClickListener {

    private RecordingsAdapter adapter;
    private View emptyState;

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

        loadRecordings();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload every time we come back (a recording may have been deleted)
        loadRecordings();
    }

    private void loadRecordings() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File dir = new File(musicDir, "P2PWalkieTalkie");

        if (dir.exists()) {
            File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".amr"));
            if (files != null && files.length > 0) {
                List<File> fileList = Arrays.asList(files);
                Collections.sort(fileList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                adapter.updateRecordings(fileList);
                if (emptyState != null) emptyState.setVisibility(View.GONE);
                return;
            }
        }

        adapter.updateRecordings(Collections.emptyList());
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
            startActivity(Intent.createChooser(intent, "Share Recording"));
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "Error sharing file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
