package com.tfajfar.walkietalkie.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tfajfar.walkietalkie.BuildConfig;
import com.tfajfar.walkietalkie.R;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvVersion = view.findViewById(R.id.tv_app_version);
        tvVersion.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME));

        SharedPreferences prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);

        // Same setting used by the switch on the Talk screen.
        SwitchMaterial switchRecord = view.findViewById(R.id.switch_record);
        switchRecord.setChecked(prefs.getBoolean("record_transmissions", false));
        switchRecord.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("record_transmissions", isChecked).apply());
    }
}