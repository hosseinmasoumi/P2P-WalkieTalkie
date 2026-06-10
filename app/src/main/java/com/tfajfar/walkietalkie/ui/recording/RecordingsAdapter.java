package com.tfajfar.walkietalkie.ui.recording;

import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tfajfar.walkietalkie.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.ViewHolder> {

    private List<File> recordings = new ArrayList<>();
    private final OnRecordingClickListener listener;

    public interface OnRecordingClickListener {
        void onRecordingClick(File file);
        void onRecordingLongClick(File file);
    }

    public RecordingsAdapter(OnRecordingClickListener listener) {
        this.listener = listener;
    }

    public void updateRecordings(List<File> newRecordings) {
        this.recordings = newRecordings;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = recordings.get(position);
        String name = file.getName();
        holder.tvFilename.setText(name);
        
        // Parse name: 20241215_143022_OUT.amr
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
        
        String duration = getDuration(file);
        holder.tvDateDuration.setText(String.format("%s • %s", 
                dateStr.isEmpty() ? holder.itemView.getContext().getString(R.string.unknown_date) : dateStr,
                duration));
        
        if ("IN".equalsIgnoreCase(direction)) {
            holder.ivDirection.setImageResource(R.drawable.ic_refresh);
            holder.ivDirection.setRotation(225);
            holder.ivDirection.setContentDescription(holder.itemView.getContext().getString(R.string.status_incoming));
        } else {
            holder.ivDirection.setImageResource(R.drawable.ic_mic);
            holder.ivDirection.setRotation(0);
            holder.ivDirection.setContentDescription(holder.itemView.getContext().getString(R.string.status_outgoing));
        }

        holder.itemView.setOnClickListener(v -> listener.onRecordingClick(file));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onRecordingLongClick(file);
            return true;
        });
        holder.btnPlay.setOnClickListener(v -> listener.onRecordingClick(file));
    }

    private String getDuration(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (time != null) {
                long timeInMillis = Long.parseLong(time);
                long seconds = (timeInMillis / 1000) % 60;
                long minutes = (timeInMillis / (1000 * 60)) % 60;
                return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            // Ignore
        }
        return "00:00";
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
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
