package com.tfajfar.walkietalkie.ui.recording;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tfajfar.walkietalkie.R;

public class RecordingDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_details);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new RecordingDetailsFragment())
                    .commit();
        }
    }
}
