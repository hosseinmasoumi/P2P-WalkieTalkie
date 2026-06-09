package com.tfajfar.walkietalkie.ui.connection;

import android.Manifest;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.ui.connection.permissions.PermissionHelper;

public class ConnectionActivity extends AppCompatActivity implements SplashFragment.SplashCallback {

    private ActivityResultLauncher<String[]> locationPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.establish_connection_activity);

        NetworkHelper.checkInternetConnection(this);

        locationPermissionRequest = PermissionHelper.createLocationPermissionLauncher(this);

        if (savedInstanceState == null) {
            NavigationHelper.replaceFragment(this, new SplashFragment());
        }
    }

    @Override
    public void onSplashFinished() {
        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
        });

        navigateToMainScreen();
    }

    public void navigateToMainScreen() {
        NavigationHelper.replaceFragment(this, new ConnectionFragment());
    }
}