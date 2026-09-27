package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.connect.DcHelper;

/**
 * Nova Chat: resolves the chat background.
 *
 * <p>By default this is a plain solid colour — white in light mode, black in dark mode — following
 * the system theme. When "Monet" colour extraction is enabled (the default) the solid colour is
 * taken from the wallpaper palette instead. A background image picked by the user always wins.
 */
public class ChatBackground {

  public static @ColorInt int getColor(@NonNull Context context) {
    if (Prefs.isMonetEnabled(context)) {
      Integer monetColor = MonetColors.getChatBackgroundColor(context);
      if (monetColor != null) {
        return monetColor;
      }
    }
    return DynamicTheme.isDarkTheme(context) ? Color.BLACK : Color.WHITE;
  }

  public static @NonNull Drawable getDrawable(@NonNull Context context) {
    int accountId = DcHelper.getContext(context).getAccountId();
    String imagePath = Prefs.getBackgroundImagePath(context, accountId);
    if (!imagePath.isEmpty()) {
      Drawable image = Drawable.createFromPath(imagePath);
      if (image != null) {
        return image;
      }
    }
    return new ColorDrawable(getColor(context));
  }
}