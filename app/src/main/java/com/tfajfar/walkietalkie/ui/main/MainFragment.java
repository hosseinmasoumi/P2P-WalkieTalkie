package com.tfajfar.walkietalkie.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.ui.guide.GuideActivity;
import com.tfajfar.walkietalkie.ui.recording.RecordingDetailsActivity;

public class MainFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Use the PTT button to go to Recording Details
        view.findViewById(R.id.btn_ptt).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), RecordingDetailsActivity.class));
        });

        // Use the Snackbar close button or some other view to go to Guide for now
        view.findViewById(R.id.btn_close_snack).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), GuideActivity.class));
        });
    }
}
