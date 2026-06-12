package com.tfajfar.walkietalkie.data;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsRepository {
    private static final String PREFS_NAME = "settings";
    private static final String KEY_RECORD_TRANSMISSIONS = "record_transmissions";

    private static SettingsRepository instance;
    private final SharedPreferences prefs;

    public static synchronized SettingsRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsRepository(context.getApplicationContext());
        }
        return instance;
    }

    private SettingsRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isRecordTransmissionsEnabled() {
        return prefs.getBoolean(KEY_RECORD_TRANSMISSIONS, false);
    }

    public void setRecordTransmissionsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RECORD_TRANSMISSIONS, enabled).apply();
    }
}
