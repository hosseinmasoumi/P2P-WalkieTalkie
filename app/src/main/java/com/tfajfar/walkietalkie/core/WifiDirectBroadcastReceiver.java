package com.tfajfar.walkietalkie.core;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

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
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                wifiDirectManager.setWifiP2pEnabled(true);
            } else {
                wifiDirectManager.setWifiP2pEnabled(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            if (manager != null) {
                boolean hasLoc = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean hasNear = true;
                if (Build.VERSION.SDK_INT >= 33) {
                    hasNear = ActivityCompat.checkSelfPermission(context, "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED;
                }

                if (hasLoc && hasNear) {
                    manager.requestPeers(channel, wifiDirectManager.getPeerListListener());
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (manager != null) {
                manager.requestConnectionInfo(channel, wifiDirectManager.getConnectionInfoListener());
            }
        }
    }
}
