package com.tfajfar.walkietalkie.ui.discovery;

import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.core.WifiDirectManager;

import java.util.ArrayList;

public class DiscoveryFragment extends Fragment
        implements DeviceAdapter.OnDeviceClickListener, WifiDirectManager.ConnectionListener {

    private static final String TAG = "DiscoveryFragment";
    private WifiDirectManager wifiDirectManager;
    private DeviceAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // volatile flag: set false in onPause so the runnable stops rescheduling itself
    private volatile boolean discoveryActive = false;
    private final Runnable discoveryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded() || !discoveryActive) return;
            WifiDirectManager.ConnectionState state = wifiDirectManager.getCurrentState();
            Log.d(TAG, "Discovery pulse, state=" + state);
            if (state == WifiDirectManager.ConnectionState.DISCONNECTED
                    || state == WifiDirectManager.ConnectionState.DISCOVERING) {
                wifiDirectManager.discoverPeers(peers -> {
                    if (isAdded() && discoveryActive) {
                        adapter.updateDevices(peers);
                        updateSearchingUI(!peers.isEmpty());
                    }
                });
            }
            // Only reschedule if still active
            if (discoveryActive) handler.postDelayed(this, 10_000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        wifiDirectManager = WifiDirectManager.getInstance(requireContext());
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = view.findViewById(R.id.rv_devices);
        adapter = new DeviceAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
        view.findViewById(R.id.btn_rescan).setOnClickListener(v -> {
            if (checkWifiAndLocation()) startDiscovery();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        wifiDirectManager.addConnectionListener(this);
        wifiDirectManager.registerReceiver();
        if (checkWifiAndLocation()) startDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop the loop BEFORE removing callbacks so the check in the runnable sees false
        discoveryActive = false;
        handler.removeCallbacks(discoveryRunnable);
        wifiDirectManager.removeConnectionListener(this);
        wifiDirectManager.unregisterReceiver();
    }

    private void startDiscovery() {
        if (!isAdded()) return;
        adapter.updateDevices(new ArrayList<>());
        updateSearchingUI(false);
        discoveryActive = true;
        handler.removeCallbacks(discoveryRunnable);
        handler.post(discoveryRunnable);
    }

    private void stopDiscovery() {
        discoveryActive = false;
        handler.removeCallbacks(discoveryRunnable);
    }

    private boolean checkWifiAndLocation() {
        if (!wifiDirectManager.isWifiEnabled()) {
            showError("Wi-Fi is disabled", "Enable",
                    v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            return false;
        }
        if (!wifiDirectManager.isLocationEnabled()) {
            showError("Location is disabled", "Enable",
                    v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return false;
        }
        if (!wifiDirectManager.hasPermissions()) {
            showError("Nearby permissions missing", "Settings", v -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.fromParts("package",
                        requireContext().getPackageName(), null));
                startActivity(i);
            });
            return false;
        }
        return true;
    }

    private void showError(String msg, String action, View.OnClickListener l) {
        if (getView() != null)
            Snackbar.make(getView(), msg, Snackbar.LENGTH_LONG).setAction(action, l).show();
    }

    private void updateSearchingUI(boolean devicesFound) {
        if (getView() == null) return;
        TextView tvTitle = getView().findViewById(R.id.tv_searching_title);
        TextView tvDesc = getView().findViewById(R.id.tv_searching_desc);
        View emptyState = getView().findViewById(R.id.tv_empty_state);
        getView().findViewById(R.id.progress_indicator).setVisibility(View.VISIBLE);
        if (devicesFound) {
            tvTitle.setText(R.string.found_devices);
            tvDesc.setText(getString(R.string.devices_found_count, adapter.getItemCount()));
            emptyState.setVisibility(View.GONE);
        } else {
            tvTitle.setText(R.string.searching);
            tvDesc.setText(R.string.searching_desc);
            emptyState.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    // ── DeviceAdapter.OnDeviceClickListener ───────────────────────────────────

    @Override
    public void onDeviceClick(WifiP2pDevice device) {
        Toast.makeText(getContext(), "Connecting to " + device.deviceName, Toast.LENGTH_SHORT).show();
        stopDiscovery();
        wifiDirectManager.connect(device, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() { Log.d(TAG, "Connection initiated"); }
            @Override
            public void onFailure(int reason) {
                if (isAdded()) {
                    Toast.makeText(getContext(),
                            "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
                    startDiscovery();
                }
            }
        });
    }

    // ── ConnectionListener ────────────────────────────────────────────────────

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (!info.groupFormed || !isAdded()) return;
        Log.d(TAG, "Group formed, navigating to Talk");
        try {
            androidx.navigation.NavController nav = Navigation.findNavController(requireView());
            if (nav.getCurrentDestination() != null
                    && nav.getCurrentDestination().getId() == R.id.nav_devices) {
                nav.navigate(R.id.nav_talk);
            }
        } catch (Exception e) {
            Log.e(TAG, "Navigation failed", e);
        }
    }

    @Override
    public void onDisconnected() {
        if (!isAdded()) return;
        Toast.makeText(getContext(), "Disconnected", Toast.LENGTH_SHORT).show();
        adapter.updateDevices(new ArrayList<>());
        startDiscovery();
    }

    @Override
    public void onWifiP2pEnabled(boolean enabled) {
        if (!isAdded()) return;
        if (!enabled) {
            Toast.makeText(getContext(), "Wi-Fi Direct is disabled", Toast.LENGTH_SHORT).show();
            updateSearchingUI(false);
        } else {
            startDiscovery();
        }
    }

    @Override
    public void onConnectionRetrying(int attempt, int max) {
        if (!isAdded() || getView() == null) return;
        String msg = getString(R.string.retry_attempt, attempt, max);
        Snackbar.make(getView(), msg + ". Retrying in 2 seconds...", Snackbar.LENGTH_SHORT).show();
        TextView tvDesc = getView().findViewById(R.id.tv_searching_desc);
        if (tvDesc != null) tvDesc.setText(msg);
    }

    @Override
    public void onConnectionFailed(int reason) {
        Log.e(TAG, "Final connection failure: " + reason);
    }
}
