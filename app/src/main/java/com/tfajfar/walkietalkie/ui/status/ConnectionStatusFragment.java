package com.tfajfar.walkietalkie.ui.status;

import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import androidx.core.content.ContextCompat;

import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.core.WifiDirectManager;

public class ConnectionStatusFragment extends Fragment implements WifiDirectManager.ConnectionListener {

    private WifiDirectManager wifiDirectManager;
    private TextView tvStatus, tvRole, tvConnectionStatus, tvConnectionRole, tvRetryAttempt, tvNextRetry;
    private View viewWifiGlow;
    private android.widget.ImageView ivWifiStatus;
    private boolean isNavigating = false;
    private int retryCount = 0;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        wifiDirectManager = WifiDirectManager.getInstance(requireContext());
        return inflater.inflate(R.layout.fragment_connection_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        tvStatus = view.findViewById(R.id.tv_devices_count);
        tvRole = view.findViewById(R.id.tv_devices_desc);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        tvConnectionRole = view.findViewById(R.id.tv_connection_role);
        viewWifiGlow = view.findViewById(R.id.view_wifi_glow);
        ivWifiStatus = view.findViewById(R.id.iv_wifi_status);
        tvRetryAttempt = view.findViewById(R.id.tv_retry_attempt);
        tvNextRetry = view.findViewById(R.id.tv_next_retry);

        view.findViewById(R.id.toolbar).setOnClickListener(v -> {
             Navigation.findNavController(v).popBackStack();
        });
        
        updateUI();
    }

    private void updateUI() {
        WifiDirectManager.ConnectionState state = wifiDirectManager.getCurrentState();
        
        switch (state) {
            case CONNECTED_OWNER:
            case CONNECTED_CLIENT:
                stopRetrySimulation();
                tvConnectionStatus.setText(R.string.connected);
                tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.greenAccent));
                tvConnectionRole.setText(state == WifiDirectManager.ConnectionState.CONNECTED_OWNER ? 
                        R.string.status_connected_owner_title : R.string.status_connected_client_title);
                tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.greenAccent));
                ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.greenAccent));
                viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.wifiGlow));
                tvStatus.setText(R.string.connection_established);
                tvRole.setText(state == WifiDirectManager.ConnectionState.CONNECTED_OWNER ? 
                        R.string.status_connected_owner_desc : R.string.status_connected_client_desc);
                navigateToMainDelayed();
                break;
            case DISCOVERING:
                stopRetrySimulation();
                tvConnectionStatus.setText(R.string.searching);
                tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.dotYellow));
                tvConnectionRole.setText(R.string.status_searching_desc);
                tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
                ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.dotYellow));
                viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.dotYellow) & 0x33FFFFFF);
                tvStatus.setText(R.string.status_searching_title);
                tvRole.setText(R.string.status_searching_desc);
                break;
            case CONNECTING:
                stopRetrySimulation();
                tvConnectionStatus.setText(R.string.status_connecting_title);
                tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orangeAccent));
                tvConnectionRole.setText(R.string.status_connecting_desc);
                tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
                ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.orangeAccent));
                viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.orangeAccent) & 0x33FFFFFF);
                tvStatus.setText(R.string.status_connecting_title);
                tvRole.setText(R.string.status_connecting_desc);
                break;
            case DISCONNECTED:
            default:
                tvConnectionStatus.setText(R.string.status_disconnected_title);
                tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.dotGrey));
                tvConnectionRole.setText(R.string.status_disconnected_desc);
                tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
                ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.dotGrey));
                viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.dotGrey) & 0x33FFFFFF);
                tvStatus.setText(R.string.status_disconnected_title);
                tvRole.setText(R.string.status_disconnected_desc);
                startRetrySimulation();
                break;
        }
    }

    private void startRetrySimulation() {
        if (retryCount >= 3) return;
        retryCount++;
        tvRetryAttempt.setText(getString(R.string.retry_attempt, retryCount, 3));
        tvNextRetry.setText(getString(R.string.next_retry, 2));
        retryHandler.postDelayed(this::updateUI, 2000);
    }

    private void stopRetrySimulation() {
        retryCount = 0;
        retryHandler.removeCallbacksAndMessages(null);
    }

    private void navigateToMainDelayed() {
        if (isNavigating) return;
        isNavigating = true;
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.nav_talk);
            }
        }, 2000);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateUI);
        }
    }

    @Override
    public void onDisconnected() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateUI);
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
}
