package com.tfajfar.walkietalkie.ui.discovery;

import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.tfajfar.walkietalkie.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private List<WifiP2pDevice> devices = new ArrayList<>();
    private final OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(WifiP2pDevice device);
    }

    public DeviceAdapter(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void updateDevices(List<WifiP2pDevice> newDevices) {
        final List<WifiP2pDevice> oldList = this.devices;
        final List<WifiP2pDevice> newList = new ArrayList<>(newDevices);

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return newList.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldList.get(oldPos).deviceAddress
                        .equals(newList.get(newPos).deviceAddress);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                WifiP2pDevice o = oldList.get(oldPos);
                WifiP2pDevice n = newList.get(newPos);
                return o.status == n.status
                        && strEqual(o.deviceName, n.deviceName);
            }

            private boolean strEqual(String a, String b) {
                if (a == null && b == null) return true;
                if (a == null || b == null) return false;
                return a.equals(b);
            }
        });

        this.devices = newList;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WifiP2pDevice device = devices.get(position);
        String name = (device.deviceName != null && !device.deviceName.isEmpty())
                ? device.deviceName : device.deviceAddress;
        holder.tvName.setText(name);
        holder.tvStatus.setText(getDeviceStatus(device.status));

        int dotColor;
        switch (device.status) {
            case WifiP2pDevice.AVAILABLE:
                dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.dotGreen);
                break;
            case WifiP2pDevice.CONNECTED:
                dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.greenAccent);
                break;
            case WifiP2pDevice.INVITED:
                dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.orangeAccent);
                break;
            default:
                dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.dotGrey);
                break;
        }
        holder.itemView.findViewById(R.id.view_status_dot)
                .getBackground().setTint(dotColor);

        holder.itemView.findViewById(R.id.btn_connect)
                .setOnClickListener(v -> listener.onDeviceClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    private String getDeviceStatus(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:   return "Available";
            case WifiP2pDevice.INVITED:     return "Invited";
            case WifiP2pDevice.CONNECTED:   return "Connected";
            case WifiP2pDevice.FAILED:      return "Failed";
            case WifiP2pDevice.UNAVAILABLE: return "Unavailable";
            default:                        return "Unknown";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus;

        ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tv_device_name);
            tvStatus = view.findViewById(R.id.tv_device_status);
        }
    }
}
