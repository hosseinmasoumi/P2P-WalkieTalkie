package com.tfajfar.walkietalkie.ui.recording;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingClickListener {

    private RecordingsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recordings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        RecyclerView rv = view.findViewById(R.id.rv_recordings);
        adapter = new RecordingsAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        loadRecordings();
    }

    private void loadRecordings() {
        File dir = requireActivity().getExternalFilesDir("recordings");
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                List<File> fileList = Arrays.asList(files);
                Collections.sort(fileList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                adapter.updateRecordings(fileList);
            }
        }
    }

    @Override
    public void onRecordingClick(File file) {
        Bundle bundle = new Bundle();
        bundle.putString("filePath", file.getAbsolutePath());
        Navigation.findNavController(requireView()).navigate(R.id.action_recordings_to_details, bundle);
    }

    @Override
    public void onRecordingLongClick(File file) {
        shareFile(file);
    }

    private void shareFile(File file) {
        Uri uri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Recording"));
    }
}
