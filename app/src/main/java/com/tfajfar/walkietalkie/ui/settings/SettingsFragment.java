package com.tfajfar.walkietalkie.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tfajfar.walkietalkie.BuildConfig;
import com.tfajfar.walkietalkie.R;

public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        TextView tvVersion = view.findViewById(R.id.tv_app_version);
        tvVersion.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME));

        SwitchMaterial switchRecord = view.findViewById(R.id.switch_record);
        switchRecord.setChecked(viewModel.isRecordTransmissionsEnabled());
        switchRecord.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setRecordTransmissionsEnabled(isChecked));
    }
}
