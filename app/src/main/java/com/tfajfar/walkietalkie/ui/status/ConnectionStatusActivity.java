package com.tfajfar.walkietalkie.ui.status;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.tfajfar.walkietalkie.R;

/**
 * ConnectionStatusActivity
 * نمایش وضعیت اتصال فعلی و دستگاه‌های متصل شده
 * این Activity میزبان ConnectionStatusFragment است
 */
public class ConnectionStatusActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_status);

        // Fragment را در شرایط اولیه بار می‌کنیم
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, new ConnectionStatusFragment())
                    .commit();
        }
    }
}
