package com.tfajfar.walkietalkie.ui.connection.permissions;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.tfajfar.walkietalkie.ui.connection.ConnectionActivity;

public class PermissionHelper {

    // تابعی برای ساخت لانچر درخواست مجوز
    public static ActivityResultLauncher<String[]> createLocationPermissionLauncher(final ConnectionActivity activity) {
        return activity.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            // بعد از گرفتن جواب، به صفحه اصلی میریم
            activity.navigateToMainScreen();
        });
    }
}