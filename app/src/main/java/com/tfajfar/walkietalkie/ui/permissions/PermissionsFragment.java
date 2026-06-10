package com.tfajfar.walkietalkie.ui.permissions;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.tfajfar.walkietalkie.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PermissionsFragment extends Fragment {

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResult
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_permissions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_grant_permissions).setOnClickListener(v -> requestRequiredPermissions());
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> requestRequiredPermissions());
        
        checkExistingPermissions();
        updatePermissionStatus(view);
    }

    private void updatePermissionStatus(View view) {
        boolean locationGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean micGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        android.widget.TextView tvLocStatus = view.findViewById(R.id.tv_location_status_label);
        android.widget.TextView tvLocMandatory = view.findViewById(R.id.tv_location_mandatory_label);
        if (locationGranted) {
            tvLocStatus.setText(R.string.status_granted);
            tvLocStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.greenAccent));
            tvLocMandatory.setVisibility(View.GONE);
        } else {
            tvLocStatus.setText(R.string.status_disabled);
            tvLocStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusRed));
            tvLocMandatory.setVisibility(View.VISIBLE);
        }

        android.widget.TextView tvMicStatus = view.findViewById(R.id.tv_mic_status_label);
        android.widget.TextView tvMicMandatory = view.findViewById(R.id.tv_mic_mandatory_label);
        if (micGranted) {
            tvMicStatus.setText(R.string.status_granted);
            tvMicStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.greenAccent));
            tvMicMandatory.setVisibility(View.GONE);
        } else {
            tvMicStatus.setText(R.string.status_disabled);
            tvMicStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusRed));
            tvMicMandatory.setVisibility(View.VISIBLE);
        }
    }

    private void checkExistingPermissions() {
        if (hasAllPermissions()) {
            navigateToMain();
        }
    }

    private boolean hasAllPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void handlePermissionResult(Map<String, Boolean> result) {
        if (getView() != null) {
            updatePermissionStatus(getView());
        }
        boolean allGranted = true;
        for (Boolean isGranted : result.values()) {
            if (!isGranted) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            navigateToMain();
        } else {
            Toast.makeText(getContext(), "All permissions are required for the app to function", Toast.LENGTH_LONG).show();
        }
    }

    private void navigateToMain() {
        if (getView() != null) {
            Navigation.findNavController(getView()).navigate(R.id.action_permissions_to_main);
        }
    }
}
