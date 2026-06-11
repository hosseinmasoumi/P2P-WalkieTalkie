package com.tfajfar.walkietalkie.ui.recording;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.io.IOException;

public class RecordingDetailsFragment extends Fragment {

    private MediaPlayer mediaPlayer;
    private boolean playerReady = false;
    private FloatingActionButton btnPlayPause;
    private SeekBar seekBar;
    private TextView tvCurrentTime, tvTotalDuration;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recording_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnPlayPause = view.findViewById(R.id.fab_play_pause);
        seekBar = view.findViewById(R.id.play_seekbar);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalDuration = view.findViewById(R.id.tv_total_duration);
        
        view.findViewById(R.id.toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Navigation.findNavController(v).popBackStack();
            }
        });

        RecordingDetailsFragmentArgs args = RecordingDetailsFragmentArgs.fromBundle(getArguments());
        final String filePath = args.getFilePath();
        if (filePath == null) {
            Toast.makeText(getContext(), R.string.no_file_specified, Toast.LENGTH_SHORT).show();
            return;
        }
        final File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(getContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show();
            setPlayerControlsEnabled(false);
            return;
        }
        
        populateFileInfo(view, file);
        setupMediaPlayer(file);
        
        view.findViewById(R.id.btn_share).setOnClickListener(v -> shareFile(file));
        
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            releasePlayer();
            if (file.delete()) {
                Toast.makeText(getContext(), R.string.deleted_success, Toast.LENGTH_SHORT).show();
                Navigation.findNavController(v).popBackStack();
            }
        });

        btnPlayPause.setOnClickListener(v -> togglePlayback());
        
        view.findViewById(R.id.btn_rewind_10).setOnClickListener(v -> seekBy(-10_000));
        
        view.findViewById(R.id.btn_forward_10).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBy(10_000);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) tvCurrentTime.setText(formatTime(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) { isUserSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                if (playerReady) mediaPlayer.seekTo(sb.getProgress());
            }
        });
    }

    private void setupMediaPlayer(File file) {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (!isAdded()) return;
                    playerReady = true;
                    int duration = mp.getDuration();
                    seekBar.setMax(duration);
                    tvTotalDuration.setText(formatTime(duration));
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (!isAdded()) return true;
                    playerReady = false;
                    Toast.makeText(getContext(), R.string.error_loading_audio, Toast.LENGTH_SHORT).show();
                    setPlayerControlsEnabled(false);
                    return true;
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                    seekBar.setProgress(0);
                    tvCurrentTime.setText(formatTime(0));
                }
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            playerReady = false;
            setPlayerControlsEnabled(false);
        }
    }

    private void togglePlayback() {
        if (!playerReady) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            scheduleSeekBarUpdate();
        }
    }

    private void seekBy(int deltaMs) {
        if (!playerReady) return;
        int pos = mediaPlayer.getCurrentPosition() + deltaMs;
        pos = Math.max(0, Math.min(mediaPlayer.getDuration(), pos));
        mediaPlayer.seekTo(pos);
        seekBar.setProgress(pos);
        tvCurrentTime.setText(formatTime(pos));
    }

    private void scheduleSeekBarUpdate() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (playerReady && mediaPlayer.isPlaying() && !isUserSeeking) {
                    int pos = mediaPlayer.getCurrentPosition();
                    seekBar.setProgress(pos);
                    tvCurrentTime.setText(formatTime(pos));
                    handler.postDelayed(this, 100);
                }
            }
        });
    }

    private void setPlayerControlsEnabled(boolean enabled) {
        if (btnPlayPause != null) btnPlayPause.setEnabled(enabled);
        if (seekBar != null) seekBar.setEnabled(enabled);
        View v = getView();
        if (v != null) {
            v.findViewById(R.id.btn_rewind_10).setEnabled(enabled);
            v.findViewById(R.id.btn_forward_10).setEnabled(enabled);
        }
    }

    private String formatTime(int ms) {
        int sec = (ms / 1000) % 60;
        int min = (ms / 60_000) % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void populateFileInfo(View view, File file) {
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
                    dateStr = d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8)
                            + " " + t.substring(0, 2) + ":" + t.substring(2, 4);
                }
                direction = parts[2].split("\\.")[0];
            }
        }
        ((TextView) view.findViewById(R.id.tv_detail_date)).setText(dateStr.isEmpty() ? getString(R.string.unknown) : dateStr);
        TextView tvDir = view.findViewById(R.id.tv_detail_direction);
        ImageView ivDir = view.findViewById(R.id.iv_detail_direction_icon);
        if ("IN".equalsIgnoreCase(direction)) {
            tvDir.setText(R.string.incoming);
            ivDir.setImageResource(R.drawable.ic_refresh);
            ivDir.setRotation(225);
        } else {
            tvDir.setText(R.string.outgoing);
            ivDir.setImageResource(R.drawable.ic_mic);
            ivDir.setRotation(0);
        }
    }

    private void shareFile(File file) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".provider", file);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {}
    }

    private void releasePlayer() {
        playerReady = false;
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (playerReady && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }
}
