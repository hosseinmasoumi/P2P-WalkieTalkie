package com.tfajfar.walkietalkie.ui.connection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.tfajfar.walkietalkie.R;

public class NavigationHelper {

    public static void replaceFragment(AppCompatActivity activity, Fragment fragment) {
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}