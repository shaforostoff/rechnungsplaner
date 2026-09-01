package com.shaforostoff.rechnungsplaner.ui;

import android.content.Context;
import android.content.res.Configuration;

import com.shaforostoff.rechnungsplaner.data.SettingsStore;

import java.util.Locale;

/** Applies the app-language setting, which is separate from the invoice-document language. */
public final class Locales {

    private Locales() {
    }

    public static Locale forTag(String tag) {
        if (tag == null) return Locale.getDefault();
        String t = tag.trim().toLowerCase(Locale.US);
        if (t.startsWith("de")) return Locale.GERMAN;
        if (t.startsWith("es")) return new Locale("es");
        if (t.startsWith("en")) return Locale.ENGLISH;
        return Locale.getDefault();
    }

    /**
     * Wraps a context in the chosen app language.
     *
     * <p>Called from {@code attachBaseContext} so every screen picks the setting up, including the
     * ones already on the back stack after it changes.
     */
    public static Context wrap(Context base) {
        String tag = new SettingsStore(base).getUiLanguage();
        if (SettingsStore.LANGUAGE_SYSTEM.equals(tag)) return base;
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(forTag(tag));
        return base.createConfigurationContext(cfg);
    }
}
