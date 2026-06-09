package com.tfajfar.walkietalkie.ui.discovery;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tfajfar.walkietalkie.R;

/**
 * DiscoveryActivity
 * صفحه جستجو و کشف دستگاه‌های نزدیک با استفاده از WiFi Direct P2P
 * این Activity میزبان DiscoveryFragment است که رابط جستجو را مدیریت می‌کند
 */
public class DiscoveryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        // Fragment را در شرایط اولیه بار می‌کنیم
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new DiscoveryFragment())
                    .commit();
        }
    }
}
