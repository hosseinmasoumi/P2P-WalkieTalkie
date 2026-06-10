package com.tfajfar.walkietalkie.ui.main;

import android.annotation.SuppressLint;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.core.AudioEngine;
import com.tfajfar.walkietalkie.core.WifiDirectManager;

import java.io.File;
import java.util.Random;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import com.google.android.material.switchmaterial.SwitchMaterial;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

public class MainFragment extends Fragment implements WifiDirectManager.ConnectionListener {
    private AudioEngine audioEngine;
    private WifiDirectManager wifiDirectManager;
    private MainViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SwitchMaterial recordSwitch;
    private View pttButton;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        audioEngine = new AudioEngine();
        wifiDirectManager = WifiDirectManager.getInstance(requireContext());
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        pttButton = view.findViewById(R.id.btn_ptt);
        recordSwitch = view.findViewById(R.id.recordTransmissionsSwitch);
        
        // Sync switch with prefs
        recordSwitch.setChecked(prefs.getBoolean("record_transmissions", false));
        recordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("record_transmissions", isChecked).apply();
        });

        pttButton.setOnTouchListener((v, event) -> {
            if (!hasPermissions()) {
                Toast.makeText(getContext(), "Permissions missing", Toast.LENGTH_SHORT).show();
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    viewModel.setIsTalking(true);
                    startTalking();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    viewModel.setIsTalking(false);
                    audioEngine.stopTalking();
                    return true;
            }
            return false;
        });

        view.findViewById(R.id.btn_close_snack).setOnClickListener(v -> 
            view.findViewById(R.id.snackbar_container).setVisibility(View.GONE));
        
        view.findViewById(R.id.card_devices).setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.nav_devices));

        setupObservers(view);
        audioEngine.startListening();
        checkPermissionsAndEnableUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        wifiDirectManager.setConnectionListener(this);
        wifiDirectManager.registerReceiver();
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        wifiDirectManager.unregisterReceiver();
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::updateUI);
        }
    }

    @Override
    public void onDisconnected() {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::updateUI);
        }
    }

    private void setupObservers(View view) {
        TextView tvStatusTitle = view.findViewById(R.id.tv_connection_status_title);
        TextView tvStatusDesc = view.findViewById(R.id.tv_connection_status_desc);
        TextView tvPeersCount = view.findViewById(R.id.tv_devices_count);
        TextView tvSnackText = view.findViewById(R.id.tv_snack_text);
        android.widget.ImageView ivWifi = view.findViewById(R.id.iv_wifi_status);
        View volumeBar = view.findViewById(R.id.volume_meter_bar);

        viewModel.getConnectionStatus().observe(getViewLifecycleOwner(), status -> {
            tvStatusTitle.setText(status);
            int color;
            if (status.equals(getString(R.string.connected))) {
                color = ContextCompat.getColor(requireContext(), R.color.greenAccent);
            } else if (status.equals(getString(R.string.searching))) {
                color = ContextCompat.getColor(requireContext(), R.color.dotYellow);
            } else if (status.equals(getString(R.string.status_connecting_title))) {
                color = ContextCompat.getColor(requireContext(), R.color.orangeAccent);
            } else {
                color = ContextCompat.getColor(requireContext(), R.color.dotGrey);
            }
            tvStatusTitle.setTextColor(color);
            ivWifi.setColorFilter(color);
        });

        viewModel.getConnectionDesc().observe(getViewLifecycleOwner(), tvStatusDesc::setText);

        viewModel.getConnectedPeersCount().observe(getViewLifecycleOwner(), count -> {
            if (count > 0) {
                tvPeersCount.setText(getString(R.string.devices_connected_count, count));
                tvSnackText.setText(R.string.connection_established);
            } else {
                tvPeersCount.setText(R.string.no_devices_found);
                tvSnackText.setText(R.string.not_connected);
            }
        });

        viewModel.getAudioLevel().observe(getViewLifecycleOwner(), level -> {
            ViewGroup.LayoutParams params = volumeBar.getLayoutParams();
            params.height = level;
            volumeBar.setLayoutParams(params);
        });

        viewModel.getIsTalking().observe(getViewLifecycleOwner(), talking -> {
            pttButton.setPressed(talking);
            if (talking) {
                startVolumeAnimation();
            } else {
                stopVolumeAnimation();
            }
        });
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionsAndEnableUI() {
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        boolean connected = info != null && info.groupFormed;
        boolean hasPerms = hasPermissions();

        if (!hasPerms || !connected) {
            pttButton.setEnabled(false);
            pttButton.setAlpha(0.5f);
        } else {
            pttButton.setEnabled(true);
            pttButton.setAlpha(1.0f);
        }
    }

    private void startVolumeAnimation() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Boolean talking = viewModel.getIsTalking().getValue();
                if (talking != null && talking) {
                    // Max height 220dp
                    float maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 220, getResources().getDisplayMetrics());
                    int height = new Random().nextInt((int) maxPx - 50) + 50;
                    viewModel.setAudioLevel(height);
                    handler.postDelayed(this, 100);
                }
            }
        });
    }

    private void stopVolumeAnimation() {
        viewModel.setAudioLevel(0);
    }

    private void updateUI() {
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        WifiDirectManager.ConnectionState state = wifiDirectManager.getCurrentState();

        if (info != null && info.groupFormed) {
            viewModel.setConnectionStatus(getString(R.string.connected));
            viewModel.setConnectionDesc(getString(info.isGroupOwner ? R.string.status_connected_owner_title : R.string.status_connected_client_title));
            viewModel.setConnectedPeersCount(2); // Still mocked for UI demo
        } else {
            switch (state) {
                case DISCOVERING:
                    viewModel.setConnectionStatus(getString(R.string.searching));
                    viewModel.setConnectionDesc(getString(R.string.status_searching_desc));
                    break;
                case CONNECTING:
                    viewModel.setConnectionStatus(getString(R.string.status_connecting_title));
                    viewModel.setConnectionDesc(getString(R.string.status_connecting_desc));
                    break;
                case DISCONNECTED:
                default:
                    viewModel.setConnectionStatus(getString(R.string.status_disconnected_title));
                    viewModel.setConnectionDesc(getString(R.string.status_disconnected_desc));
                    break;
            }
            viewModel.setConnectedPeersCount(0);
        }
        checkPermissionsAndEnableUI();
    }

    private void startTalking() {
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        if (info == null || !info.groupFormed) {
            Toast.makeText(getContext(), "Not connected to any peer", Toast.LENGTH_SHORT).show();
            return;
        }

        String targetIp = info.isGroupOwner ? "192.168.49.255" : info.groupOwnerAddress.getHostAddress();
        
        String recordPath = null;
        if (recordSwitch != null && recordSwitch.isChecked()) {
            File dir = requireActivity().getExternalFilesDir("recordings");
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
                String timestamp = sdf.format(new java.util.Date());
                String fileName = timestamp + "_OUT.amr";
                recordPath = new File(dir, fileName).getAbsolutePath();
            }
        }
        
        audioEngine.startTalking(targetIp, recordPath);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (audioEngine != null) {
            audioEngine.stopTalking();
            audioEngine.release();
            audioEngine = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
