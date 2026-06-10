package com.tfajfar.walkietalkie.ui.recording;

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
        holder.tvFilename.setText(file.getName());
        holder.tvDateDuration.setText("Recording from " + file.getName()); // Mock duration/date

        holder.itemView.setOnClickListener(v -> listener.onRecordingClick(file));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onRecordingLongClick(file);
            return true;
        });
        holder.btnPlay.setOnClickListener(v -> listener.onRecordingClick(file));
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
