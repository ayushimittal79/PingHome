package com.pinghome.app.ui.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/** Remembers the light/dark choice made from the Home screen's toggle (follows the system until then). */
public final class ThemePrefs {

    private static final String PREFS = "pinghome_ui";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemePrefs() {}

    public static void applySaved(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static boolean isDark(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void toggle(Context context) {
        int next = isDark(context) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_NIGHT_MODE, next).apply();
        AppCompatDelegate.setDefaultNightMode(next);
    }
}
