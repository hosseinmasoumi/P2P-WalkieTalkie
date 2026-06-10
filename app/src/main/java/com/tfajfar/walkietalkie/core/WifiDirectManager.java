package com.tfajfar.walkietalkie.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class WifiDirectManager {
    private static final String TAG = "WifiDirectManager";
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final List<WifiP2pDevice> peers = new ArrayList<>();

    public interface PeerListListener {
        void onPeersAvailable(List<WifiP2pDevice> peers);
    }

    public WifiDirectManager(Context context) {
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, Looper.getMainLooper(), null);
    }

    @SuppressLint("MissingPermission")
    public void discoverPeers(PeerListListener listener) {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery Started");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Discovery Failed: " + reason);
            }
        });

        manager.requestPeers(channel, peersList -> {
            peers.clear();
            peers.addAll(peersList.getDeviceList());
            if (listener != null) listener.onPeersAvailable(peers);
        });
    }

    @SuppressLint("MissingPermission")
    public void connect(WifiP2pDevice device, WifiP2pManager.ActionListener listener) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, listener);
    }
}
