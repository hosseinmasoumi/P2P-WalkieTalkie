package com.tfajfar.walkietalkie.ui.recording;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.tfajfar.walkietalkie.R;

import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;

import android.widget.ImageView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class RecordingDetailsFragment extends Fragment {

    private MediaPlayer mediaPlayer;
    private FloatingActionButton btnPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvTotalDuration;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recording_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        btnPlayPause = view.findViewById(R.id.fab_play_pause);
        seekBar = view.findViewById(R.id.play_seekbar);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalDuration = view.findViewById(R.id.tv_total_duration);

        view.findViewById(R.id.toolbar).setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        if (getArguments() != null) {
            String filePath = getArguments().getString("filePath");
            if (filePath != null) {
                File file = new File(filePath);
                updateUI(view, file);
                setupMediaPlayer(file);
                
                view.findViewById(R.id.btn_share).setOnClickListener(v -> shareFile(file));
                view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                    if (file.delete()) {
                        Toast.makeText(getContext(), R.string.deleted_success, Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(v).popBackStack();
                    }
                });
            }
        }

        btnPlayPause.setOnClickListener(v -> togglePlayback());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void setupMediaPlayer(File file) {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            int duration = mediaPlayer.getDuration();
            seekBar.setMax(duration);
            tvTotalDuration.setText(formatTime(duration));
            
            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(0);
                tvCurrentTime.setText(formatTime(0));
            });
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error loading audio file", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayback() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            updateSeekBar();
        }
    }

    private void updateSeekBar() {
        if (mediaPlayer != null && mediaPlayer.isPlaying() && !isUserSeeking) {
            int currentPos = mediaPlayer.getCurrentPosition();
            seekBar.setProgress(currentPos);
            tvCurrentTime.setText(formatTime(currentPos));
            handler.postDelayed(this::updateSeekBar, 100);
        }
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
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
            ivDir.setImageResource(R.drawable.ic_refresh);
            ivDir.setRotation(225);
        } else {
            tvDir.setText(R.string.status_outgoing);
            ivDir.setImageResource(R.drawable.ic_mic);
            ivDir.setRotation(0);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
