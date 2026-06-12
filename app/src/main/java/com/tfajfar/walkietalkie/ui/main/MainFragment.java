package com.tfajfar.walkietalkie.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.core.AudioEngine;
import com.tfajfar.walkietalkie.core.WifiDirectManager;

public class MainFragment extends Fragment implements WifiDirectManager.ConnectionListener {

    private AudioEngine audioEngine;
    private WifiDirectManager wifiDirectManager;
    private MainViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler snackHandler = new Handler(Looper.getMainLooper());
    private SwitchMaterial recordSwitch;
    private TextView tvRecordStatus;
    private View pttButton;
    private boolean connectedMessageShown = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        wifiDirectManager = WifiDirectManager.getInstance(requireContext());
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pttButton = view.findViewById(R.id.btn_ptt);
        recordSwitch = view.findViewById(R.id.recordTransmissionsSwitch);
        tvRecordStatus = view.findViewById(R.id.tv_record_status);
        view.findViewById(R.id.snackbar_container).setVisibility(View.GONE);

        refreshRecordSwitch();
        recordSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                viewModel.setRecordTransmissionsEnabled(isChecked);
                updateRecordStatusText(isChecked);
                updateAudioEngineRecording();
            }
        });

        pttButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!pttButton.isEnabled()) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        viewModel.setIsTalking(true);
                        startTalking();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        viewModel.setIsTalking(false);
                        if (audioEngine != null) audioEngine.stopTalking();
                        return true;
                }
                return false;
            }
        });

        view.findViewById(R.id.btn_close_snack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View snack = getView().findViewById(R.id.snackbar_container);
                if (snack != null) snack.setVisibility(View.GONE);
            }
        });

        view.findViewById(R.id.toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                androidx.drawerlayout.widget.DrawerLayout drawer = requireActivity().findViewById(R.id.drawer_layout);
                if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
            }
        });

        view.findViewById(R.id.card_devices).setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.nav_devices));
        view.findViewById(R.id.card_bottom_status).setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_main_to_status));
        setupObservers(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        wifiDirectManager.addConnectionListener(this);
        if (audioEngine == null) {
            audioEngine = new AudioEngine();
        }
        refreshRecordSwitch();
        audioEngine.startListening();
        checkPermissionsAndEnableUI();
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (audioEngine != null) audioEngine.stopTalking();
        wifiDirectManager.removeConnectionListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        snackHandler.removeCallbacksAndMessages(null);
        releaseAudioEngine();
    }

    private void releaseAudioEngine() {
        if (audioEngine != null) {
            audioEngine.stopTalking();
            audioEngine.release();
            audioEngine = null;
        }
    }

    private void refreshRecordSwitch() {
        boolean isRecordOn = viewModel.isRecordTransmissionsEnabled();
        if (recordSwitch != null) recordSwitch.setChecked(isRecordOn);
        updateRecordStatusText(isRecordOn);
        updateAudioEngineRecording();
    }

    private void updateAudioEngineRecording() {
        if (audioEngine == null) return;
        audioEngine.setRecordEnabled(
                viewModel.isRecordTransmissionsEnabled(),
                viewModel.getRecordingDirectoryPath());
    }

    private void startTalking() {
        if (audioEngine == null) return;
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        if (info == null || !info.groupFormed) {
            Toast.makeText(getContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            viewModel.setIsTalking(false);
            return;
        }
        String targetIp = info.isGroupOwner ? "192.168.49.255" : info.groupOwnerAddress.getHostAddress();
        String recordPath = null;
        if (viewModel.isRecordTransmissionsEnabled()) {
            recordPath = viewModel.createOutgoingRecordingPath();
        }
        audioEngine.startTalking(targetIp, recordPath);
    }

    private void setupObservers(final View view) {
        final TextView tvStatusTitle = view.findViewById(R.id.tv_connection_status_title);
        final TextView tvStatusDesc = view.findViewById(R.id.tv_connection_status_desc);
        final TextView tvPeersCount = view.findViewById(R.id.tv_devices_count);
        final TextView tvSnackText = view.findViewById(R.id.tv_snack_text);
        final ImageView ivWifi = view.findViewById(R.id.iv_wifi_status);
        final View volumeIndicator = view.findViewById(R.id.volume_level_indicator);
        final View snackbar = view.findViewById(R.id.snackbar_container);
        final View cardBottom = view.findViewById(R.id.card_bottom_status);

        viewModel.getConnectionStatus().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String status) {
                tvStatusTitle.setText(status);
                int color;
                if (status.equals(getString(R.string.connected))) color = ContextCompat.getColor(requireContext(), R.color.greenAccent);
                else if (status.equals(getString(R.string.searching))) color = ContextCompat.getColor(requireContext(), R.color.dotYellow);
                else if (status.equals(getString(R.string.status_connecting_title))) color = ContextCompat.getColor(requireContext(), R.color.orangeAccent);
                else color = ContextCompat.getColor(requireContext(), R.color.dotGrey);
                tvStatusTitle.setTextColor(color);
                ivWifi.setColorFilter(color);
            }
        });

        viewModel.getConnectionDesc().observe(getViewLifecycleOwner(), desc -> tvStatusDesc.setText(desc));
        viewModel.getConnectedPeersCount().observe(getViewLifecycleOwner(), count -> {
            tvPeersCount.setText(getString(R.string.devices_connected_count, count));
            if (count > 0) {
                cardBottom.setVisibility(View.VISIBLE);
                if (!connectedMessageShown) {
                    connectedMessageShown = true;
                    tvSnackText.setText(getString(R.string.connected_to_devices_snackbar, count));
                    snackbar.setVisibility(View.VISIBLE);
                    snackHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            snackbar.setVisibility(View.GONE);
                        }
                    }, 3000);
                }
            } else {
                connectedMessageShown = false;
                snackbar.setVisibility(View.GONE);
                cardBottom.setVisibility(View.GONE);
            }
        });

        viewModel.getAudioLevel().observe(getViewLifecycleOwner(), level -> {
            if (volumeIndicator != null) {
                ViewGroup.LayoutParams p = volumeIndicator.getLayoutParams();
                p.height = level;
                volumeIndicator.setLayoutParams(p);
            }
        });
        viewModel.getIsTalking().observe(getViewLifecycleOwner(), talking -> {
            if (talking) startVolumeAnimation(); else stopVolumeAnimation();
        });
    }

    private void checkPermissionsAndEnableUI() {
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        boolean connected = info != null && info.groupFormed;
        boolean hasMic = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean canTalk = hasMic && wifiDirectManager.hasPermissions() && connected;
        pttButton.setEnabled(canTalk);
        pttButton.setAlpha(canTalk ? 1.0f : 0.5f);
    }

    private void updateRecordStatusText(boolean isOn) {
        if (tvRecordStatus != null) {
            tvRecordStatus.setText(isOn ? R.string.status_on : R.string.status_off);
            tvRecordStatus.setTextColor(isOn
                    ? ContextCompat.getColor(requireContext(), R.color.greenAccent)
                    : ContextCompat.getColor(requireContext(), R.color.textSecondary));
        }
    }

    private void startVolumeAnimation() {
        handler.removeCallbacksAndMessages(null);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || getView() == null) return;
                Boolean talking = viewModel.getIsTalking().getValue();
                if (talking != null && talking && audioEngine != null) {
                    float maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 220, getResources().getDisplayMetrics());
                    int amp = audioEngine.getAmplitude();
                    int h = (int) ((amp / 32767.0) * maxPx);
                    if (h < 20 && amp > 0) h = 20;
                    viewModel.setAudioLevel(h);
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
            if (state == WifiDirectManager.ConnectionState.DISCOVERING) {
                viewModel.setConnectionStatus(getString(R.string.searching));
                viewModel.setConnectionDesc(getString(R.string.status_searching_desc));
            } else if (state == WifiDirectManager.ConnectionState.CONNECTING) {
                viewModel.setConnectionStatus(getString(R.string.status_connecting_title));
                viewModel.setConnectionDesc(getString(R.string.status_connecting_desc));
            } else {
                viewModel.setConnectionStatus(getString(R.string.status_disconnected_title));
                viewModel.setConnectionDesc(getString(R.string.status_disconnected_desc));
            }
            viewModel.setConnectedPeersCount(0);
        }
        checkPermissionsAndEnableUI();
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (isAdded()) requireActivity().runOnUiThread(this::updateUI);
    }

    @Override
    public void onDisconnected() {
        if (isAdded()) requireActivity().runOnUiThread(this::updateUI);
    }

    @Override
    public void onWifiP2pEnabled(boolean enabled) {
        if (isAdded()) requireActivity().runOnUiThread(this::updateUI);
    }

    @Override
    public void onConnectionRetrying(int attempt, int max) {
        if (isAdded()) requireActivity().runOnUiThread(this::updateUI);
    }

    @Override
    public void onConnectionFailed(final int reason) {
        if (isAdded()) requireActivity().runOnUiThread(() -> {
            updateUI();
            Toast.makeText(getContext(), getString(R.string.connection_failed, reason), Toast.LENGTH_SHORT).show();
        });
    }
}
