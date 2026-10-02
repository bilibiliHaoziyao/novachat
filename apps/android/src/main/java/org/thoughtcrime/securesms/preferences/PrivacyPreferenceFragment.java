package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import java.util.Objects;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.Prefs;

/** Settings &rarr; Privacy &amp; Security: end-to-end encryption, screen security and the keyboard. */
public class PrivacyPreferenceFragment extends ListSummaryPreferenceFragment {

  private CheckBoxPreference e2eeCheckbox;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    Preference screenSecurity = findPreference(Prefs.SCREEN_SECURITY_PREF);
    if (screenSecurity != null) {
      screenSecurity.setOnPreferenceChangeListener(new ScreenShotSecurityListener());
    }

    // Nova Chat: end-to-end encryption is on by default.
    e2eeCheckbox = findPreference("pref_e2ee");
    if (e2eeCheckbox != null) {
      e2eeCheckbox.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            boolean enabled = (Boolean) newValue;
            int warnRes = enabled ? R.string.pref_e2ee_on_warn : R.string.pref_e2ee_off_warn;
            new AlertDialog.Builder(requireContext())
                .setMessage(warnRes)
                .setPositiveButton(
                    R.string.ok,
                    (dialog, which) -> {
                      dcContext.setConfig(
                          DcHelper.CONFIG_FORCE_ENCRYPTION, enabled ? "1" : "0");
                      ((CheckBoxPreference) preference).setChecked(enabled);
                    })
                .setNegativeButton(R.string.cancel, null)
                .show();
            return false;
          });
    }
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_privacy);
  }

  /** Used by the settings overview to show whether end-to-end encryption is active. */
  public static CharSequence getSummary(Context context) {
    boolean enabled =
        1 == DcHelper.getContext(context).getConfigInt(DcHelper.CONFIG_FORCE_ENCRYPTION);
    return context.getString(R.string.pref_e2ee)
        + ": "
        + context.getString(enabled ? R.string.on : R.string.off);
  }

  @Override
  public void onResume() {
    super.onResume();
    Objects.requireNonNull(
            ((ApplicationPreferencesActivity) requireActivity()).getSupportActionBar())
        .setTitle(R.string.pref_privacy_security);
    if (e2eeCheckbox != null) {
      e2eeCheckbox.setChecked(1 == dcContext.getConfigInt(DcHelper.CONFIG_FORCE_ENCRYPTION));
    }
  }

  private class ScreenShotSecurityListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
      boolean enabled = (Boolean) newValue;
      Prefs.setScreenSecurityEnabled(getContext(), enabled);
      Toast.makeText(
              getContext(), R.string.pref_screen_security_please_restart_hint, Toast.LENGTH_LONG)
          .show();
      return true;
    }
  }
}