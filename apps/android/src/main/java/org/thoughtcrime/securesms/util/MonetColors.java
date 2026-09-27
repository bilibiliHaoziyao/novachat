package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Nova Chat: reads the system ("Monet") wallpaper palette and derives the chat background colour
 * from it. Android 12+ exposes the palette as the {@code @android:color/system_*} resources; on
 * devices without dynamic colour support those resources are missing and we fall back to plain
 * white/black.
 */
public class MonetColors {

  /** The wallpaper palette is only exposed from Android 12 on. */
  private static final int MIN_SDK = Build.VERSION_CODES.S;

  /**
   * @return the chat background colour tinted by the wallpaper palette, or {@code null} when the
   *     device has no dynamic colour support.
   */
  public static @Nullable @ColorInt Integer getChatBackgroundColor(Context context) {
    if (Build.VERSION.SDK_INT < MIN_SDK) {
      return null;
    }
    // A pale accent tint in light mode, a deep accent tint in dark mode.
    return getSystemColor(context, DynamicTheme.isDarkTheme(context) ? "system_accent1_900" : "system_accent1_100");
  }

  private static @Nullable @ColorInt Integer getSystemColor(Context context, String name) {
    Resources resources = context.getResources();
    int id = resources.getIdentifier(name, "color", "android");
    if (id == 0) {
      return null;
    }
    try {
      return ContextCompat.getColor(context, id);
    } catch (Resources.NotFoundException e) {
      return null;
    }
  }
}