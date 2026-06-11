package com.tfajfar.walkietalkie.ui.connection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.tfajfar.walkietalkie.R;

public class SplashFragment extends Fragment {

    public interface SplashCallback {
        void onSplashFinished();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    if (getActivity() instanceof SplashCallback) {
                        ((SplashCallback) getActivity()).onSplashFinished();
                    } else {
                        try {
                            if (hasAllPermissions()) {
                                Navigation.findNavController(view).navigate(R.id.action_splash_to_main);
                            } else {
                                Navigation.findNavController(view).navigate(R.id.action_splash_to_permissions);
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }, 2000);
    }

    private boolean hasAllPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return false;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return false;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(requireContext(), "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
