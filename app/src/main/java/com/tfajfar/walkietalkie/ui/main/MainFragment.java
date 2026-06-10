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

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import com.google.android.material.switchmaterial.SwitchMaterial;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.os.Environment;

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
        updateAudioEngineRecording();
        recordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("record_transmissions", isChecked).apply();
            updateAudioEngineRecording();
        });

        pttButton.setOnTouchListener((v, event) -> {
            if (!pttButton.isEnabled()) return false;
            
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
        
        view.findViewById(R.id.toolbar).setOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer = requireActivity().findViewById(R.id.drawer_layout);
            if (drawer != null) {
                drawer.openDrawer(androidx.core.view.GravityCompat.START);
            }
        });

        view.findViewById(R.id.card_devices).setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.nav_devices));

        view.findViewById(R.id.card_bottom_status).setOnClickListener(v -> {
            wifiDirectManager.disconnect();
            Toast.makeText(getContext(), "Disconnected", Toast.LENGTH_SHORT).show();
        });

        setupObservers(view);
        audioEngine.startListening();
        checkPermissionsAndEnableUI();
    }

    private void updateAudioEngineRecording() {
        if (audioEngine != null) {
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File dir = new File(musicDir, "P2PWalkieTalkie");
            audioEngine.setRecordEnabled(recordSwitch.isChecked(), dir.getAbsolutePath());
        }
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
        View volumeIndicator = view.findViewById(R.id.volume_level_indicator);

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
            String countText = count + " " + (count == 1 ? "device connected" : "devices connected");
            tvPeersCount.setText(countText);
            if (count > 0) {
                tvSnackText.setText(getString(R.string.connected_to_devices_snackbar, count));
                view.findViewById(R.id.card_bottom_status).setVisibility(View.VISIBLE);
            } else {
                tvSnackText.setText(R.string.not_connected);
                view.findViewById(R.id.card_bottom_status).setVisibility(View.GONE);
            }
        });

        viewModel.getAudioLevel().observe(getViewLifecycleOwner(), level -> {
            if (volumeIndicator != null) {
                ViewGroup.LayoutParams params = volumeIndicator.getLayoutParams();
                params.height = level;
                volumeIndicator.setLayoutParams(params);
            }
        });

        viewModel.getIsTalking().observe(getViewLifecycleOwner(), talking -> {
            pttButton.setPressed(talking);
            if (talking) {
                startVolumeAnimation();
                pttButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.statusRed)));
            } else {
                stopVolumeAnimation();
                pttButton.setBackgroundTintList(null);
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
                    int amp = audioEngine.getAmplitude();
                    // Scale amplitude (max 32767) to pixels
                    int height = (int) ((amp / 32767.0) * maxPx);
                    if (height < 20 && amp > 0) height = 20; // Show something if there's sound
                    viewModel.setAudioLevel(height);
                    handler.postDelayed(this, 50);
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
            viewModel.setConnectedPeersCount(1); 
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
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File dir = new File(musicDir, "P2PWalkieTalkie");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
            String timestamp = sdf.format(new java.util.Date());
            String fileName = timestamp + "_OUT.amr";
            recordPath = new File(dir, fileName).getAbsolutePath();
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
