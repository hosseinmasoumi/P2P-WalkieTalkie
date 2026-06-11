package com.tfajfar.walkietalkie.ui.recording;

import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {

    private List<File> recordings = new ArrayList<>();
    private final OnRecordingClickListener listener;
    private final Map<String, String> durationCache = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnRecordingClickListener {
        void onRecordingClick(File file);
        void onRecordingLongClick(File file);
    }

    public RecordingsAdapter(OnRecordingClickListener listener) {
        this.listener = listener;
    }

    public void updateRecordings(List<File> newRecordings) {
        final List<File> oldList = this.recordings;
        final List<File> newList = new ArrayList<>(newRecordings);

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return newList.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldList.get(oldPos).getAbsolutePath().equals(newList.get(newPos).getAbsolutePath());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                File o = oldList.get(oldPos);
                File n = newList.get(newPos);
                return o.lastModified() == n.lastModified() && o.length() == n.length();
            }
        });

        this.recordings = newList;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final File file = recordings.get(position);
        String name = file.getName();
        holder.tvFilename.setText(name);

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

        String duration = durationCache.get(file.getAbsolutePath());
        if (duration == null) {
            holder.tvDateDuration.setText(String.format("%s • %s", dateStr.isEmpty() ? "Unknown" : dateStr, "..."));
            
            // Simple thread for background work
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String d = getDuration(file);
                    durationCache.put(file.getAbsolutePath(), d);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            int currentPos = holder.getAdapterPosition();
                            if (currentPos != RecyclerView.NO_POSITION) {
                                notifyItemChanged(currentPos);
                            }
                        }
                    });
                }
            }).start();
        } else {
            holder.tvDateDuration.setText(String.format("%s • %s", dateStr.isEmpty() ? "Unknown" : dateStr, duration));
        }

        if ("IN".equalsIgnoreCase(direction)) {
            holder.ivDirection.setImageResource(R.drawable.ic_refresh);
            holder.ivDirection.setRotation(225);
        } else {
            holder.ivDirection.setImageResource(R.drawable.ic_mic);
            holder.ivDirection.setRotation(0);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onRecordingClick(file);
            }
        });
        
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onRecordingLongClick(file);
                return true;
            }
        });
    }

    private String getDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (time != null) {
                long timeInMillis = Long.parseLong(time);
                long seconds = (timeInMillis / 1000) % 60;
                long minutes = (timeInMillis / (1000 * 60)) % 60;
                return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
            }
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
        return "00:00";
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFilename, tvDateDuration;
        ImageView btnPlay, btnMore, ivDirection;

        ViewHolder(View itemView) {
            super(itemView);
            tvFilename = itemView.findViewById(R.id.tv_filename);
            tvDateDuration = itemView.findViewById(R.id.tv_date_duration);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnMore = itemView.findViewById(R.id.btn_more);
            ivDirection = itemView.findViewById(R.id.iv_direction_icon);
        }
    }
}
