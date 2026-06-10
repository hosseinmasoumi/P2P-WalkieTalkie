package com.tfajfar.walkietalkie.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;
import android.util.Log;

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

    public void discoverPeers(PeerListListener listener) {
        this.peerListListener = listener;
        currentState = ConnectionState.DISCOVERING;
        try {
            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Discovery Started");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Discovery Failed: " + reason);
                    currentState = ConnectionState.DISCONNECTED;
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permissions for discoverPeers", e);
            currentState = ConnectionState.DISCONNECTED;
        }
    }

    public void connect(WifiP2pDevice device, WifiP2pManager.ActionListener listener) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        currentState = ConnectionState.CONNECTING;
        try {
            manager.connect(channel, config, listener);
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permissions for connect", e);
            currentState = ConnectionState.DISCONNECTED;
            if (listener != null) listener.onFailure(WifiP2pManager.P2P_UNSUPPORTED);
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
}
