package com.tfajfar.walkietalkie.ui.status;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import com.tfajfar.walkietalkie.R;

/**
 * ConnectionStatusFragment
 * نمایش جزئیات اتصال WiFi Direct P2P
 * اطلاعات مربوط به Group Owner، دستگاه‌های متصل، و تلاش‌های بازاتصال را نشان می‌دهد
 */
public class ConnectionStatusFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Layout فرگمنت را inflate می‌کنیم
        return inflater.inflate(R.layout.fragment_connection_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Toolbar را راه‌اندازی می‌کنیم
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.permissions_menu);
        
        // توصیه: در آینده می‌توان رویدادهای Toolbar را برای بازگشت یا تنظیمات اضافه کرد
    }
}
