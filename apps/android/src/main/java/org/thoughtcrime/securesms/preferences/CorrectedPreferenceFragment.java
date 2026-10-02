package org.thoughtcrime.securesms.preferences;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import org.thoughtcrime.securesms.util.MonetColors;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    View lv = getView().findViewById(android.R.id.list);
    if (lv != null) lv.setPadding(0, 0, 0, 0);

    applyMonetIconTint();
  }

  @Override
  public void onResume() {
    super.onResume();
    applyMonetIconTint();
  }

  /** Nova Chat: tint every icon on this settings screen with the wallpaper palette (Monet). */
  private void applyMonetIconTint() {
    PreferenceGroup group = getPreferenceScreen();
    if (group != null) {
      applyMonetIconTint(group);
    }
  }

  private void applyMonetIconTint(PreferenceGroup group) {
    for (int i = 0; i < group.getPreferenceCount(); i++) {
      Preference preference = group.getPreference(i);
      if (preference instanceof PreferenceGroup) {
        applyMonetIconTint((PreferenceGroup) preference);
        continue;
      }
      Drawable icon = preference.getIcon();
      if (icon != null) {
        MonetColors.applyIconTint(requireContext(), icon);
      }
    }
  }
}
