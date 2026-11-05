package com.hatkid.mkxpz;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

/** The interface preference is independent of the game's translation language. */
public final class AppLanguage {
    private static final String KEY = "interface_language";

    private AppLanguage() { }

    public static String selection(Context context) {
        return context.getSharedPreferences(EssentiaSettings.PREFS, 0).getString(KEY, "");
    }

    public static void select(Context context, String language) {
        context.getSharedPreferences(EssentiaSettings.PREFS, 0).edit().putString(KEY, language).commit();
    }

    public static Context wrap(Context context) {
        String language = selection(context);
        if (!"es".equals(language) && !"en".equals(language)) return context;
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language));
        return context.createConfigurationContext(config);
    }
}
