package com.tfajfar.walkietalkie.ui.discovery;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.core.WifiDirectManager;
import com.tfajfar.walkietalkie.ui.status.ConnectionStatusActivity;

public class DiscoveryFragment extends Fragment implements DeviceAdapter.OnDeviceClickListener {

    private WifiDirectManager wifiDirectManager;
    private DeviceAdapter adapter;

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

        view.findViewById(R.id.btn_rescan).setOnClickListener(v -> startDiscovery());
        
        startDiscovery();
    }

    private void setupRecyclerView(View view) {
        RecyclerView rv = view.findViewById(R.id.rv_devices);
        adapter = new DeviceAdapter(this);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);
    }

    private void startDiscovery() {
        updateSearchingUI(true, getString(R.string.searching), getString(R.string.searching_desc));
        wifiDirectManager.discoverPeers(peers -> {
            if (isAdded()) {
                adapter.updateDevices(peers);
                View emptyState = getView() != null ? getView().findViewById(R.id.tv_empty_state) : null;
                if (emptyState != null) {
                    emptyState.setVisibility(peers.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (!peers.isEmpty()) {
                    updateSearchingUI(false, getString(R.string.found_devices), peers.size() + " devices found nearby");
                }
            }
        });
    }

    private void updateSearchingUI(boolean isSearching, String title, String desc) {
        if (getView() == null) return;
        android.widget.TextView tvTitle = getView().findViewById(R.id.ll_searching).findViewById(R.id.tv_searching_title);
        android.widget.TextView tvDesc = getView().findViewById(R.id.ll_searching).findViewById(R.id.tv_searching_desc);
        View progress = getView().findViewById(R.id.ll_searching).findViewById(R.id.progress_indicator);
        
        if (tvTitle != null) tvTitle.setText(title);
        if (tvDesc != null) tvDesc.setText(desc);
        if (progress != null) progress.setVisibility(isSearching ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onDeviceClick(WifiP2pDevice device) {
        updateSearchingUI(true, "Connecting...", "Connecting to " + device.deviceName);
        wifiDirectManager.connect(device, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Connecting to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).navigate(R.id.action_discovery_to_status);
                }
            }

            @Override
            public void onFailure(int reason) {
                if (isAdded()) {
                    updateSearchingUI(false, "Connection Failed", "Reason: " + reason);
                    Toast.makeText(getContext(), "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        wifiDirectManager.registerReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        wifiDirectManager.unregisterReceiver();
    }
}
