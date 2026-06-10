package com.tfajfar.walkietalkie.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

public class WifiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WifiP2pReceiver";
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final WifiDirectManager wifiDirectManager;

    public WifiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiDirectManager wifiDirectManager) {
        this.manager = manager;
        this.channel = channel;
        this.wifiDirectManager = wifiDirectManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            wifiDirectManager.setWifiP2pEnabled(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
            Log.d(TAG, "P2P state changed: " + (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED ? "ENABLED" : "DISABLED"));
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            if (manager != null) {
                manager.requestPeers(channel, wifiDirectManager.getPeerListListener());
            }
            Log.d(TAG, "Peers changed, requesting new list");
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (manager == null) return;
            manager.requestConnectionInfo(channel, wifiDirectManager.getConnectionInfoListener());
            Log.d(TAG, "Connection changed, requesting info");
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "This device changed");
        }
    }
}
