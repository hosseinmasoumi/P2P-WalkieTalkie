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

public class DiscoveryFragment extends Fragment implements DeviceAdapter.OnDeviceClickListener, WifiDirectManager.ConnectionListener {

    private static final String TAG = "DiscoveryFragment";
    private WifiDirectManager wifiDirectManager;
    private DeviceAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable discoveryRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        wifiDirectManager = WifiDirectManager.getInstance(requireContext());
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView(view);

        view.findViewById(R.id.btn_rescan).setOnClickListener(v -> {
            if (checkWifiAndLocation()) {
                startDiscovery();
            }
        });

        discoveryRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;

                WifiDirectManager.ConnectionState state = wifiDirectManager.getCurrentState();
                Log.d(TAG, "Discovery runnable pulse. Current state: " + state);
                
                // Maintain discovery if we're not currently in the process of connecting or connected
                if (state == WifiDirectManager.ConnectionState.DISCONNECTED || state == WifiDirectManager.ConnectionState.DISCOVERING) {
                    wifiDirectManager.discoverPeers(peers -> {
                        if (isAdded()) {
                            adapter.updateDevices(peers);
                            updateSearchingUI(!peers.isEmpty());
                        }
                    });
                }
                
                // Reschedule to keep device discoverable (Listen Mode expires over time)
                handler.postDelayed(this, 10000);
            }
        };
    }

    private boolean checkWifiAndLocation() {
        if (!wifiDirectManager.isWifiEnabled()) {
            showError("Wi-Fi is disabled", "Enable", v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            return false;
        }
        if (!wifiDirectManager.isLocationEnabled()) {
            showError("Location is disabled", "Enable", v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return false;
        }
        if (!wifiDirectManager.hasPermissions()) {
            showError("Nearby permissions missing", "Settings", v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", requireContext().getPackageName(), null));
                startActivity(intent);
            });
            return false;
        }
        return true;
    }

    private void showError(String message, String actionText, View.OnClickListener listener) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_LONG)
                    .setAction(actionText, listener)
                    .show();
        }
    }

    private void setupRecyclerView(View view) {
        RecyclerView rv = view.findViewById(R.id.rv_devices);
        adapter = new DeviceAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
    }

    private void startDiscovery() {
        if (!isAdded()) return;
        
        // Clear list and show searching state
        adapter.updateDevices(new ArrayList<>());
        updateSearchingUI(false);
        
        handler.removeCallbacks(discoveryRunnable);
        handler.post(discoveryRunnable);
    }

    private void updateSearchingUI(boolean devicesFound) {
        if (getView() == null) return;
        
        TextView tvTitle = getView().findViewById(R.id.tv_searching_title);
        TextView tvDesc = getView().findViewById(R.id.tv_searching_desc);
        View progress = getView().findViewById(R.id.progress_indicator);
        View emptyState = getView().findViewById(R.id.tv_empty_state);
        
        // Progress indicator should stay visible while we are in discovery mode
        progress.setVisibility(View.VISIBLE);

        if (devicesFound) {
            tvTitle.setText(R.string.found_devices);
            tvDesc.setText(getString(R.string.devices_found_count, adapter.getItemCount()));
            emptyState.setVisibility(View.GONE);
        } else {
            tvTitle.setText(R.string.searching);
            tvDesc.setText(R.string.searching_desc);
            // Only show empty state if we actually have 0 devices in the adapter
            if (adapter.getItemCount() == 0) {
                emptyState.setVisibility(View.VISIBLE);
            } else {
                emptyState.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onDeviceClick(WifiP2pDevice device) {
        Toast.makeText(getContext(), "Connecting to " + device.deviceName, Toast.LENGTH_SHORT).show();
        
        // Stop the discovery loop while attempting to connect
        handler.removeCallbacks(discoveryRunnable);
        
        wifiDirectManager.connect(device, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connection initiation successful");
            }

            @Override
            public void onFailure(int reason) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Connection failed after retries: " + reason, Toast.LENGTH_SHORT).show();
                    // Resume discovery since connection failed
                    startDiscovery();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        wifiDirectManager.setConnectionListener(this);
        wifiDirectManager.registerReceiver();
        if (checkWifiAndLocation()) {
            startDiscovery();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(discoveryRunnable);
        wifiDirectManager.unregisterReceiver();
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (info.groupFormed && isAdded()) {
            Log.d(TAG, "Connected! Group formed. Navigating to talk screen.");
            // Determine if we should navigate based on current destination to avoid double navigation
            try {
                Navigation.findNavController(requireView()).navigate(R.id.nav_talk);
            } catch (Exception e) {
                Log.e(TAG, "Navigation failed", e);
            }
        }
    }

    @Override
    public void onDisconnected() {
        if (isAdded()) {
            Toast.makeText(getContext(), "Disconnected", Toast.LENGTH_SHORT).show();
            adapter.updateDevices(new ArrayList<>());
            startDiscovery();
        }
    }

    @Override
    public void onWifiP2pEnabled(boolean enabled) {
        if (isAdded()) {
            if (!enabled) {
                Toast.makeText(getContext(), "Wi-Fi Direct is disabled", Toast.LENGTH_SHORT).show();
                updateSearchingUI(false);
            } else {
                startDiscovery();
            }
        }
    }

    @Override
    public void onConnectionRetrying(int attempt, int max) {
        if (isAdded() && getView() != null) {
            String message = getString(R.string.retry_attempt, attempt, max);
            Snackbar.make(getView(), message + ". Retrying in 2 seconds...", Snackbar.LENGTH_SHORT).show();
            
            TextView tvDesc = getView().findViewById(R.id.tv_searching_desc);
            if (tvDesc != null) {
                tvDesc.setText(message);
            }
        }
    }

    @Override
    public void onConnectionFailed(int reason) {
        // Log final failure. UI is handled in onDeviceClick's onFailure or here if needed.
        Log.e(TAG, "Final connection failure: " + reason);
    }
}
