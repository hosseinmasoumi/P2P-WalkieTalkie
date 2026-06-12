package com.tfajfar.walkietalkie.ui.recording;

import android.content.Intent;
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

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer = requireActivity().findViewById(R.id.drawer_layout);
            if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
        });

        loadRecordings();
    }

    private File getRecordingDir() {
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        return new File(musicDir, "P2PWalkieTalkie");
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

    private void showMessage(String message) {
        View view = getView();
        if (view != null) Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }
}