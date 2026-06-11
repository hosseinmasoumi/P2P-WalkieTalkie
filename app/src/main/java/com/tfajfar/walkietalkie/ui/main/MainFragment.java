package com.tfajfar.walkietalkie.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.core.AudioEngine;
import com.tfajfar.walkietalkie.core.WifiDirectManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
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
        view.findViewById(R.id.snackbar_container).setVisibility(View.GONE);
        recordSwitch.setChecked(prefs.getBoolean("record_transmissions", false));
        recordSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean("record_transmissions", checked).apply();
            updateAudioEngineRecording();
        });
        pttButton.setOnTouchListener((v, event) -> {
            if(!pttButton.isEnabled())return false;
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    viewModel.setIsTalking(true);
                    startTalking();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    viewModel.setIsTalking(false);
                    if(audioEngine!=null)audioEngine.stopTalking();
                    return true;
            }
            return false;
        });
        view.findViewById(R.id.btn_close_snack).setOnClickListener(
                v -> view.findViewById(R.id.snackbar_container).setVisibility(View.GONE));
        view.findViewById(R.id.toolbar).setOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer =
                    requireActivity().findViewById(R.id.drawer_layout);
            if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
        });
        view.findViewById(R.id.card_devices).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.nav_devices));
        view.findViewById(R.id.card_bottom_status).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_main_to_status));
        setupObservers(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        wifiDirectManager.addConnectionListener(this);
        wifiDirectManager.registerReceiver();
        // Create AudioEngine only once per view lifecycle; recreate if previously released
        if (audioEngine == null) {
            audioEngine = new AudioEngine();
            updateAudioEngineRecording();
            audioEngine.startListening();
        }
        checkPermissionsAndEnableUI();
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        wifiDirectManager.removeConnectionListener(this);
        wifiDirectManager.unregisterReceiver();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        releaseAudioEngine();
    }

    private void releaseAudioEngine() {
        if (audioEngine != null) {
            audioEngine.stopTalking();
            audioEngine.release();
            audioEngine = null;
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private void updateAudioEngineRecording() {
        if (audioEngine == null) return;
        audioEngine.setRecordEnabled(
                prefs.getBoolean("record_transmissions", false),
                getRecordingDir().getAbsolutePath());
    }

    private File getRecordingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "P2PWalkieTalkie");
    }

    private void startTalking() {
        if (audioEngine == null) return;
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        if (info == null || !info.groupFormed) {
            Toast.makeText(getContext(), "Not connected to any peer", Toast.LENGTH_SHORT).show();
            viewModel.setIsTalking(false);
            return;
        }
        String targetIp = info.isGroupOwner
                ? "192.168.49.255"
                : info.groupOwnerAddress.getHostAddress();
        String recordPath = null;
        if (prefs.getBoolean("record_transmissions", false)) {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            recordPath = new File(getRecordingDir(), ts + "_OUT.amr").getAbsolutePath();
        }
        audioEngine.startTalking(targetIp, recordPath);
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void setupObservers(View view) {
        TextView tvStatusTitle = view.findViewById(R.id.tv_connection_status_title);
        TextView tvStatusDesc = view.findViewById(R.id.tv_connection_status_desc);
        TextView tvPeersCount = view.findViewById(R.id.tv_devices_count);
        TextView tvSnackText = view.findViewById(R.id.tv_snack_text);
        ImageView ivWifi = view.findViewById(R.id.iv_wifi_status);
        View volumeIndicator = view.findViewById(R.id.volume_level_indicator);
        View snackbar = view.findViewById(R.id.snackbar_container);
        View cardBottom = view.findViewById(R.id.card_bottom_status);

        viewModel.getConnectionStatus().observe(getViewLifecycleOwner(), status -> {
            tvStatusTitle.setText(status);
            int color;
            if (status.equals(getString(R.string.connected)))
                color = ContextCompat.getColor(requireContext(), R.color.greenAccent);
            else if (status.equals(getString(R.string.searching)))
                color = ContextCompat.getColor(requireContext(), R.color.dotYellow);
            else if (status.equals(getString(R.string.status_connecting_title)))
                color = ContextCompat.getColor(requireContext(), R.color.orangeAccent);
            else
                color = ContextCompat.getColor(requireContext(), R.color.dotGrey);
            tvStatusTitle.setTextColor(color);
            ivWifi.setColorFilter(color);
        });

        viewModel.getConnectionDesc().observe(getViewLifecycleOwner(), tvStatusDesc::setText);

        viewModel.getConnectedPeersCount().observe(getViewLifecycleOwner(), count -> {
            tvPeersCount.setText(count + " " + (count == 1 ? "device connected" : "devices connected"));
            if (count > 0) {
                tvSnackText.setText(getString(R.string.connected_to_devices_snackbar, count));
                snackbar.setVisibility(View.VISIBLE);
                cardBottom.setVisibility(View.VISIBLE);
            } else {
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
            if(talking){
                startVolumeAnimation();
            }else{
                stopVolumeAnimation();
            }
        });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void checkPermissionsAndEnableUI() {
        WifiP2pInfo info = wifiDirectManager.getConnectionInfo();
        boolean connected = info != null && info.groupFormed;
        boolean hasPerm = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean canTalk = hasPerm && connected;
        pttButton.setEnabled(canTalk);
        pttButton.setAlpha(canTalk ? 1.0f : 0.5f);
    }

    private void startVolumeAnimation() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Boolean talking = viewModel.getIsTalking().getValue();
                if (talking != null && talking && audioEngine != null) {
                    float maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 220,
                            getResources().getDisplayMetrics());
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
            viewModel.setConnectionDesc(getString(info.isGroupOwner
                    ? R.string.status_connected_owner_title : R.string.status_connected_client_title));
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
                default:
                    viewModel.setConnectionStatus(getString(R.string.status_disconnected_title));
                    viewModel.setConnectionDesc(getString(R.string.status_disconnected_desc));
                    break;
            }
            viewModel.setConnectedPeersCount(0);
        }
        checkPermissionsAndEnableUI();
    }

    // ── ConnectionListener ────────────────────────────────────────────────────

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
    public void onConnectionFailed(int reason) {
        if (isAdded()) requireActivity().runOnUiThread(() -> {
            updateUI();
            Toast.makeText(getContext(),
                    getString(R.string.connection_failed_reason, reason), Toast.LENGTH_SHORT).show();
        });
    }
}
