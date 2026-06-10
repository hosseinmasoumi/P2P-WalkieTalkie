package com.tfajfar.walkietalkie.core;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class WifiDirectManager {
    private static final String TAG = "WifiDirectManager";
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final Context context;
    private BroadcastReceiver receiver;
    
    private PeerListListener peerListListener;
    private ConnectionListener connectionListener;

    public interface PeerListListener {
        void onPeersAvailable(List<WifiP2pDevice> peers);
    }
    
    public interface ConnectionListener {
        void onConnectionInfoAvailable(WifiP2pInfo info);
        void onDisconnected();
        void onWifiP2pEnabled(boolean enabled);
        void onConnectionRetrying(int attempt, int max);
        void onConnectionFailed(int reason);
    }

    public enum ConnectionState {
        DISCONNECTED, DISCOVERING, CONNECTING, CONNECTED_OWNER, CONNECTED_CLIENT
    }

    private static WifiDirectManager instance;
    private WifiP2pInfo connectionInfo;
    private ConnectionState currentState = ConnectionState.DISCONNECTED;

    public static WifiDirectManager getInstance(Context context) {
        if (instance == null) {
            instance = new WifiDirectManager(context.getApplicationContext());
        }
        return instance;
    }

    public WifiDirectManager(Context context) {
        this.context = context;
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, Looper.getMainLooper(), null);
    }

    public void registerReceiver() {
        if (receiver != null) return;
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        receiver = new WifiDirectBroadcastReceiver(manager, channel, this);
        context.registerReceiver(receiver, intentFilter);
    }

    public void unregisterReceiver() {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    public ConnectionState getCurrentState() {
        return currentState;
    }

    public boolean isWifiEnabled() {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) 
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    public boolean hasPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public void discoverPeers(PeerListListener listener) {
        this.peerListListener = listener;
        if (!isWifiEnabled() || !isLocationEnabled() || !hasPermissions()) {
            Log.e(TAG, "Cannot start discovery: Wifi/Location disabled or permissions missing");
            return;
        }

        currentState = ConnectionState.DISCOVERING;
        
        // Stop any ongoing discovery first to ensure a fresh start
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                startPeerDiscovery();
            }

            @Override
            public void onFailure(int reason) {
                // If stop fails, still try to start
                startPeerDiscovery();
            }
        });
    }

    private void startPeerDiscovery() {
        try {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Discovery Started Successfully");
                    currentState = ConnectionState.DISCOVERING;
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Discovery Failed: " + reason);
                    if (reason != WifiP2pManager.BUSY) {
                        currentState = ConnectionState.DISCONNECTED;
                    } else {
                        // If busy, it might already be discovering, so we keep the state
                        currentState = ConnectionState.DISCOVERING;
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permissions for discoverPeers", e);
            currentState = ConnectionState.DISCONNECTED;
        }
    }

    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void connect(WifiP2pDevice device, WifiP2pManager.ActionListener listener) {
        retryCount = 0;
        performConnect(device, listener);
    }

    private void performConnect(WifiP2pDevice device, WifiP2pManager.ActionListener listener) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        currentState = ConnectionState.CONNECTING;
        try {
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    if (listener != null) listener.onSuccess();
                }

                @Override
                public void onFailure(int reason) {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        Log.d(TAG, "Connection failed, retrying " + retryCount + "/" + MAX_RETRIES);
                        if (connectionListener != null) {
                            connectionListener.onConnectionRetrying(retryCount, MAX_RETRIES);
                        }
                        handler.postDelayed(() -> performConnect(device, listener), 2000);
                    } else {
                        currentState = ConnectionState.DISCONNECTED;
                        if (connectionListener != null) {
                            connectionListener.onConnectionFailed(reason);
                        }
                        if (listener != null) listener.onFailure(reason);
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permissions for connect", e);
            currentState = ConnectionState.DISCONNECTED;
            if (listener != null) listener.onFailure(WifiP2pManager.P2P_UNSUPPORTED);
        }
    }

    public void disconnect() {
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Disconnected successfully");
                    onDisconnected();
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Disconnect failed: " + reason);
                }
            });
        }
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    protected WifiP2pManager.PeerListListener getPeerListListener() {
        return peersList -> {
            if (peerListListener != null) {
                peerListListener.onPeersAvailable(new ArrayList<>(peersList.getDeviceList()));
            }
        };
    }

    protected WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return info -> {
            this.connectionInfo = info;
            if (info.groupFormed) {
                currentState = info.isGroupOwner ? ConnectionState.CONNECTED_OWNER : ConnectionState.CONNECTED_CLIENT;
            } else {
                currentState = ConnectionState.DISCONNECTED;
            }
            if (connectionListener != null) {
                connectionListener.onConnectionInfoAvailable(info);
            }
        };
    }

    public WifiP2pInfo getConnectionInfo() {
        return connectionInfo;
    }

    protected void onDisconnected() {
        currentState = ConnectionState.DISCONNECTED;
        this.connectionInfo = null;
        if (connectionListener != null) {
            connectionListener.onDisconnected();
        }
    }

    protected void setWifiP2pEnabled(boolean enabled) {
        if (connectionListener != null) {
            connectionListener.onWifiP2pEnabled(enabled);
        }
    }
}
