package com.mapper.imuslam;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREFS_NAME = "MapSessionPrefs";
    private static final String KEY_LAST_MAP = "last_map_path";

    public static void saveLastMapPath(Context context, String path) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_LAST_MAP, path);
        editor.apply();
    }

    public static String getLastMapPath(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_MAP, null);
    }

    public static void clearLastMapPath(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(KEY_LAST_MAP);
        editor.apply();
    }
}
