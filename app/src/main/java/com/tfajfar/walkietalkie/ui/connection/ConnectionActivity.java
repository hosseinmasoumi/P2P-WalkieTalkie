package com.tfajfar.walkietalkie.ui.connection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
        if (hasAllPermissions()) {
            navigateToMainScreen();
        } else {
            locationPermissionRequest.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.RECORD_AUDIO
            });
        }
    }

    private boolean hasAllPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public void navigateToMainScreen() {
        NavigationHelper.replaceFragment(this, new ConnectionFragment());
    }
}