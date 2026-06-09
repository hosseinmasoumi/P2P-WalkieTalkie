package com.tfajfar.walkietalkie.ui.discovery;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import com.tfajfar.walkietalkie.R;
import com.tfajfar.walkietalkie.ui.status.ConnectionStatusActivity;

/**
 * DiscoveryFragment
 * نمایش لیست دستگاه‌های یافت‌شده و امکان اتصال به آن‌ها
 * کاربر می‌تواند دستگاه‌ها را جستجو کند یا دوباره جستجو کند
 */
public class DiscoveryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Layout فرگمنت را inflate می‌کنیم
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Toolbar را راه‌اندازی می‌کنیم
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.permissions_menu); // بازاستفاده از منو برای الآن

        // دکمه جستجوی مجدد را تنظیم می‌کنیم
        view.findViewById(R.id.btn_rescan).setOnClickListener(v -> {
            // به صفحه وضعیت اتصال منتقل می‌شویم
            Intent intent = new Intent(getActivity(), ConnectionStatusActivity.class);
            startActivity(intent);
        });
    }
}
