package com.tfajfar.walkietalkie.ui.discovery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.tfajfar.walkietalkie.R;

public class DiscoveryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.permissions_menu); // Reusing the same menu for now

        view.findViewById(R.id.btn_rescan).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), com.tfajfar.walkietalkie.ui.status.ConnectionStatusActivity.class);
            startActivity(intent);
        });
    }
}
