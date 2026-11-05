package com.hatkid.mkxpz;

import java.util.Locale;

/** Approved target codes supported by the translation providers and safe for cache filenames. */
final class TranslationLanguage {
    private static final String[] CODES = {"es", "en", "pt", "fr", "de", "it", "ja", "ko", "zh-CN"};

    private TranslationLanguage() { }

    static String[] codes() { return CODES.clone(); }

    static String normalize(String code) {
        for (String supported : CODES) if (supported.equals(code)) return supported;
        return "es";
    }

    static String label(String code, Locale displayLocale) {
        Locale locale = Locale.forLanguageTag(normalize(code));
        return locale.getDisplayName(displayLocale);
    }
}
