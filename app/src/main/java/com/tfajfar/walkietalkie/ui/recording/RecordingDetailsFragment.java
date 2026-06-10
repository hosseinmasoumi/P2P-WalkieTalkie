package com.tfajfar.walkietalkie.ui.recording;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.tfajfar.walkietalkie.R;

import android.widget.Toast;
import android.widget.TextView;
import java.io.File;

import android.widget.ImageView;

public class RecordingDetailsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recording_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        view.findViewById(R.id.toolbar).setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        if (getArguments() != null) {
            String filePath = getArguments().getString("filePath");
            if (filePath != null) {
                File file = new File(filePath);
                updateUI(view, file);
                
                view.findViewById(R.id.btn_share).setOnClickListener(v -> shareFile(file));
                view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                    if (file.delete()) {
                        Toast.makeText(getContext(), R.string.deleted_success, Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(v).popBackStack();
                    }
                });
            }
        }

        // Add dummy logic for play/pause
        com.google.android.material.floatingactionbutton.FloatingActionButton btnPlayPause = view.findViewById(R.id.fab_play_pause);
        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                Toast.makeText(getContext(), R.string.playback_sim, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void shareFile(File file) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", file);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(intent, "Share Recording"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(View view, File file) {
        String name = file.getName();
        ((TextView) view.findViewById(R.id.tv_detail_filename)).setText(name);
        
        String dateStr = "";
        String direction = "OUT";
        if (name.contains("_")) {
            String[] parts = name.split("_");
            if (parts.length >= 3) {
                String d = parts[0];
                String t = parts[1];
                if (d.length() == 8 && t.length() == 6) {
                    dateStr = d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8) + " " +
                              t.substring(0, 2) + ":" + t.substring(2, 4);
                }
                direction = parts[2].split("\\.")[0];
            }
        }
        
        ((TextView) view.findViewById(R.id.tv_detail_date)).setText(dateStr.isEmpty() ? getString(R.string.unknown) : dateStr);
        
        TextView tvDir = view.findViewById(R.id.tv_detail_direction);
        ImageView ivDir = view.findViewById(R.id.iv_detail_direction_icon);
        
        if ("IN".equalsIgnoreCase(direction)) {
            tvDir.setText(R.string.status_incoming);
            ivDir.setImageResource(R.drawable.ic_refresh); // Placeholder for IN
            ivDir.setRotation(225);
        } else {
            tvDir.setText(R.string.status_outgoing);
            ivDir.setImageResource(R.drawable.ic_mic);
            ivDir.setRotation(0);
        }
    }
}
