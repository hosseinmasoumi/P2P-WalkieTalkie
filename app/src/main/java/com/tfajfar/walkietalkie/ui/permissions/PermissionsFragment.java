package com.tfajfar.walkietalkie.ui.permissions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.tfajfar.walkietalkie.R;

public class PermissionsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_permissions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup Toolbar
        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.permissions_menu);

        // Initialize views and click listeners here
        view.findViewById(R.id.btn_grant_permissions).setOnClickListener(v -> {
            // TODO: Request permissions
        });

        view.findViewById(R.id.btn_retry).setOnClickListener(v -> {
            // TODO: Retry permission check
        });
    }
}
