package com.tfajfar.walkietalkie.ui.connection;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

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
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // سه ثانیه صبر میکنیم و بعد میریم مرحله بعد
        new Handler().postDelayed(() -> {
            if (getActivity() instanceof SplashCallback) {
                ((SplashCallback) getActivity()).onSplashFinished();
            }
        }, 2000);
    }
}