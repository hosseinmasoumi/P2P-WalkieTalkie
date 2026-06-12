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
    private final List<ConnectionListener> connectionListeners = new ArrayList<ConnectionListener>();

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
    private int receiverRefCount = 0;

    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Keep the last selected device so the app can try to recover after a disconnect.
    private WifiP2pDevice lastDevice;
    private WifiP2pManager.ActionListener lastConnectListener;
    private boolean manualDisconnect = false;

    public static synchronized WifiDirectManager getInstance(Context context) {
        if (instance == null) {
            instance = new WifiDirectManager(context.getApplicationContext());
        }
        return instance;
    }

    private WifiDirectManager(Context context) {
        this.context = context;
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, Looper.getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.e(TAG, "Channel disconnected");
                scheduleReconnect();
            }
        });
    }

    public synchronized void registerReceiver() {
        receiverRefCount++;
        if (receiver != null) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        receiver = new WifiDirectBroadcastReceiver(manager, channel, this);
        context.registerReceiver(receiver, filter);
    }

    public synchronized void unregisterReceiver() {
        receiverRefCount--;
        if (receiverRefCount <= 0) {
            receiverRefCount = 0;
            if (receiver != null) {
                try { context.unregisterReceiver(receiver); } catch (Exception e) {
                    Log.e(TAG, "Error unregistering receiver", e);
                }
                receiver = null;
            }
        }
    }

    public ConnectionState getCurrentState() { return currentState; }

    public boolean isWifiEnabled() {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    public boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    public boolean hasPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public synchronized void addConnectionListener(ConnectionListener listener) {
        if (listener != null && !connectionListeners.contains(listener))
            connectionListeners.add(listener);
    }

    public synchronized void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    public void setConnectionListener(ConnectionListener listener) {
        addConnectionListener(listener);
    }

    public void discoverPeers(PeerListListener listener) {
        this.peerListListener = listener;
        if (!isWifiEnabled() || !isLocationEnabled() || !hasPermissions()) {
            Log.e(TAG, "Cannot start discovery");
            return;
        }
        currentState = ConnectionState.DISCOVERING;
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { startPeerDiscovery(); }
            @Override public void onFailure(int r) { startPeerDiscovery(); }
        });
    }

    private void startPeerDiscovery() {
        try {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { currentState = ConnectionState.DISCOVERING; }
                @Override public void onFailure(int reason) {
                    currentState = (reason == WifiP2pManager.BUSY)
                            ? ConnectionState.DISCOVERING : ConnectionState.DISCONNECTED;
                }
            });
        } catch (SecurityException e) {
            currentState = ConnectionState.DISCONNECTED;
        }
    }

    public void connect(WifiP2pDevice device, WifiP2pManager.ActionListener listener) {
        retryCount = 0;
        manualDisconnect = false;
        lastDevice = device;
        lastConnectListener = listener;
        performConnect(device, listener);
    }

    private void performConnect(final WifiP2pDevice device, final WifiP2pManager.ActionListener listener) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        currentState = ConnectionState.CONNECTING;
        try {
            manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    if (listener != null) listener.onSuccess();
                }
                @Override public void onFailure(final int reason) {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        notifyRetrying();
                        handler.postDelayed(new Runnable() {
                            @Override public void run() { performConnect(device, listener); }
                        }, 2000);
                    } else {
                        currentState = ConnectionState.DISCONNECTED;
                        notifyFailed(reason);
                        if (listener != null) listener.onFailure(reason);
                    }
                }
            });
        } catch (SecurityException e) {
            currentState = ConnectionState.DISCONNECTED;
            if (listener != null) listener.onFailure(WifiP2pManager.P2P_UNSUPPORTED);
        }
    }

    public void disconnect() {
        manualDisconnect = true;
        handler.removeCallbacksAndMessages(null);
        if (manager == null || channel == null) return;
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { onDisconnected(); }
            @Override public void onFailure(int r) { }
        });
    }

    private void scheduleReconnect() {
        if (manualDisconnect || lastDevice == null) return;
        if (!isWifiEnabled() || !isLocationEnabled() || !hasPermissions()) return;

        retryCount = 0;
        notifyRetrying(1, MAX_RETRIES);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!manualDisconnect && lastDevice != null) {
                    performConnect(lastDevice, lastConnectListener);
                }
            }
        }, 2000);
    }

    private void notifyRetrying() {
        notifyRetrying(retryCount, MAX_RETRIES);
    }

    private synchronized void notifyRetrying(int attempt, int max) {
        for (ConnectionListener cl : connectionListeners) cl.onConnectionRetrying(attempt, max);
    }

    private synchronized void notifyFailed(int reason) {
        for (ConnectionListener cl : connectionListeners) cl.onConnectionFailed(reason);
    }

    protected WifiP2pManager.PeerListListener getPeerListListener() {
        return new WifiP2pManager.PeerListListener() {
            @Override public void onPeersAvailable(android.net.wifi.p2p.WifiP2pDeviceList list) {
                if (peerListListener != null)
                    peerListListener.onPeersAvailable(new ArrayList<WifiP2pDevice>(list.getDeviceList()));
            }
        };
    }

    protected WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return new WifiP2pManager.ConnectionInfoListener() {
            @Override public void onConnectionInfoAvailable(WifiP2pInfo info) {
                connectionInfo = info;
                if (info.groupFormed) {
                    currentState = info.isGroupOwner
                            ? ConnectionState.CONNECTED_OWNER : ConnectionState.CONNECTED_CLIENT;
                } else {
                    currentState = ConnectionState.DISCONNECTED;
                }
                synchronized (WifiDirectManager.this) {
                    for (ConnectionListener cl : connectionListeners)
                        cl.onConnectionInfoAvailable(info);
                }
            }
        };
    }

    public WifiP2pInfo getConnectionInfo() { return connectionInfo; }

    protected void onDisconnected() {
        currentState = ConnectionState.DISCONNECTED;
        connectionInfo = null;
        synchronized (this) {
            for (ConnectionListener cl : connectionListeners) cl.onDisconnected();
        }
        scheduleReconnect();
    }

    protected void setWifiP2pEnabled(boolean enabled) {
        synchronized (this) {
            for (ConnectionListener cl : connectionListeners) cl.onWifiP2pEnabled(enabled);
        }
    }
}