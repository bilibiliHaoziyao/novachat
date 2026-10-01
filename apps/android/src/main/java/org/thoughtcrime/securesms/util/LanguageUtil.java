package org.thoughtcrime.securesms.util;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Nova Chat: in-app language selection.
 *
 * <p>Wraps {@link AppCompatDelegate#setApplicationLocales} and additionally remembers the choice in
 * the app preferences, so it can be restored on the next start on devices where AppCompat does not
 * persist it by itself.
 */
public final class LanguageUtil {

  /** Preference key backing the in-app language selection. */
  public static final String LANGUAGE_PREF = "pref_language";

  /** Value that restores the device language. */
  public static final String LANGUAGE_SYSTEM = "system";

  private LanguageUtil() {}

  /** Switch the app language; {@link #LANGUAGE_SYSTEM} restores the device language. */
  public static void apply(Context context, String languageTag) {
    Prefs.setStringPreference(context, LANGUAGE_PREF, languageTag);
    AppCompatDelegate.setApplicationLocales(toLocales(languageTag));
  }

  /** Re-applies the stored language, called once when the app process starts. */
  public static void applyStored(Context context) {
    String tag = Prefs.getStringPreference(context, LANGUAGE_PREF, LANGUAGE_SYSTEM);
    if (!LANGUAGE_SYSTEM.equals(tag)) {
      AppCompatDelegate.setApplicationLocales(toLocales(tag));
    }
  }

  /** The language currently selected, or {@link #LANGUAGE_SYSTEM} if the device default is used. */
  public static String getSelectedTag(Context context) {
    LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
    if (!locales.isEmpty()) {
      return locales.toLanguageTags();
    }
    return Prefs.getStringPreference(context, LANGUAGE_PREF, LANGUAGE_SYSTEM);
  }

  private static LocaleListCompat toLocales(String languageTag) {
    return languageTag == null || LANGUAGE_SYSTEM.equals(languageTag)
        ? LocaleListCompat.getEmptyLocaleList()
        : LocaleListCompat.forLanguageTags(languageTag);
  }
}