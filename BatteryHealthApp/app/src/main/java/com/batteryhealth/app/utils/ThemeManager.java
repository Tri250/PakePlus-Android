package com.batteryhealth.app.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 主题管理器：支持跟随系统/浅色/深色三种模式
 */
public class ThemeManager {

    private static final String PREFS_THEME = "theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    // 模式常量
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    public static void applyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
        applyTheme(mode);
    }

    public static void applyTheme(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static void setThemeMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, mode)
                .apply();
        applyTheme(mode);
    }

    public static int getThemeMode(Context context) {
        return context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static boolean isDarkMode(Context context) {
        int mode = getThemeMode(context);
        if (mode == MODE_DARK) return true;
        if (mode == MODE_LIGHT) return false;
        // MODE_SYSTEM: check system setting
        return (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}