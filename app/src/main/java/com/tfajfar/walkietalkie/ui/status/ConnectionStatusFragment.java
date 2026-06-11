package com.tfajfar.walkietalkie.ui.status;

import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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

        view.findViewById(R.id.toolbar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Navigation.findNavController(v).popBackStack();
            }
        });
        
        updateUI();
    }

    private void updateUI() {
        if (!isAdded()) return;
        WifiDirectManager.ConnectionState state = wifiDirectManager.getCurrentState();
        
        tvRetryAttempt.setVisibility(View.GONE);
        tvNextRetry.setVisibility(View.GONE);

        if (state == WifiDirectManager.ConnectionState.CONNECTED_OWNER || state == WifiDirectManager.ConnectionState.CONNECTED_CLIENT) {
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
        } else if (state == WifiDirectManager.ConnectionState.DISCOVERING) {
            tvConnectionStatus.setText(R.string.searching);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.dotYellow));
            tvConnectionRole.setText(R.string.status_searching_desc);
            tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.dotYellow));
            viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.dotYellow) & 0x33FFFFFF);
            tvStatus.setText(R.string.status_searching_title);
            tvRole.setText(R.string.status_searching_desc);
        } else if (state == WifiDirectManager.ConnectionState.CONNECTING) {
            tvConnectionStatus.setText(R.string.status_connecting_title);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.orangeAccent));
            tvConnectionRole.setText(R.string.status_connecting_desc);
            tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.orangeAccent));
            viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.orangeAccent) & 0x33FFFFFF);
            tvStatus.setText(R.string.status_connecting_title);
            tvRole.setText(R.string.status_connecting_desc);
        } else {
            tvConnectionStatus.setText(R.string.status_disconnected_title);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.dotGrey));
            tvConnectionRole.setText(R.string.status_disconnected_desc);
            tvConnectionRole.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            ivWifiStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.dotGrey));
            viewWifiGlow.getBackground().setTint(ContextCompat.getColor(requireContext(), R.color.dotGrey) & 0x33FFFFFF);
            tvStatus.setText(R.string.status_disconnected_title);
            tvRole.setText(R.string.status_disconnected_desc);
        }
    }

    private void navigateToMainDelayed() {
        if (isNavigating) return;
        isNavigating = true;

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || getView() == null) return;
                try {
                    androidx.navigation.NavController nav = Navigation.findNavController(getView());
                    if (nav.getCurrentDestination() != null
                            && nav.getCurrentDestination().getId() == R.id.connectionStatusFragment) {
                        nav.navigate(R.id.nav_talk);
                    }
                } catch (Exception e) {
                }
            }
        }, 1500);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() { updateUI(); }
            });
        }
    }

    @Override
    public void onDisconnected() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() { updateUI(); }
            });
        }
    }

    @Override
    public void onWifiP2pEnabled(boolean enabled) {
        if (!enabled && isAdded()) {
            Toast.makeText(getContext(), "Wi-Fi Direct is disabled", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConnectionRetrying(final int attempt, final int max) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvRetryAttempt.setVisibility(View.VISIBLE);
                    tvNextRetry.setVisibility(View.VISIBLE);
                    tvRetryAttempt.setText(getString(R.string.retry_attempt, attempt, max));
                    tvNextRetry.setText(getString(R.string.next_retry, 2));
                }
            });
        }
    }

    @Override
    public void onConnectionFailed(int reason) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI();
                    Toast.makeText(getContext(), "Connection failed", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isNavigating = false;
        wifiDirectManager.addConnectionListener(this);
        wifiDirectManager.registerReceiver();
        updateUI();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isNavigating = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        wifiDirectManager.removeConnectionListener(this);
        wifiDirectManager.unregisterReceiver();
    }
}
