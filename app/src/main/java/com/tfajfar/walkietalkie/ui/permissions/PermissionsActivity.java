package com.tfajfar.walkietalkie.ui.permissions;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tfajfar.walkietalkie.R;

public class PermissionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new PermissionsFragment())
                    .commit();
        }
    }
}
