package com.tfajfar.walkietalkie.ui.connection;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.tfajfar.walkietalkie.R;

public class ConnectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.establish_connection_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, ConnectionFragment.newInstance())
                    .commitNow();
        }
    }
}
