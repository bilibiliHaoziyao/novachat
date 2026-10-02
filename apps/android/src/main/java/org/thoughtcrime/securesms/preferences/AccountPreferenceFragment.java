package org.thoughtcrime.securesms.preferences;

import static android.app.Activity.RESULT_OK;
import static org.thoughtcrime.securesms.connect.DcHelper.CONFIG_BCC_SELF;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import java.util.Objects;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebDavSettingsActivity;
import org.thoughtcrime.securesms.util.ScreenLockUtil;

/**
 * Settings &rarr; Account: identity, multi-device and the WebDAV backup. Everything that belongs to
 * the account rather than to a single chat.
 */
public class AccountPreferenceFragment extends ListSummaryPreferenceFragment {

  private CheckBoxPreference multiDeviceCheckbox;
  private ActivityResultLauncher<Intent> screenLockLauncher;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    screenLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                ((ApplicationPreferencesActivity) requireActivity()).showBackupProvider();
              }
            });

    Preference addSecondDevice = findPreference("pref_account_multidevice");
    if (addSecondDevice != null) {
      addSecondDevice.setOnPreferenceClickListener(
          preference -> {
            if (!ScreenLockUtil.applyScreenLock(
                requireActivity(),
                getString(R.string.multidevice_title),
                getString(R.string.multidevice_this_creates_a_qr_code)
                    + "\n\n"
                    + getString(R.string.enter_system_secret_to_continue),
                screenLockLauncher)) {
              new AlertDialog.Builder(requireActivity())
                  .setTitle(R.string.multidevice_title)
                  .setMessage(R.string.multidevice_this_creates_a_qr_code)
                  .setPositiveButton(
                      R.string.perm_continue,
                      (dialog, which) ->
                          ((ApplicationPreferencesActivity) requireActivity())
                              .showBackupProvider())
                  .setNegativeButton(R.string.cancel, null)
                  .show();
            }
            return true;
          });
    }

    multiDeviceCheckbox = findPreference("pref_bcc_self");
    if (multiDeviceCheckbox != null) {
      multiDeviceCheckbox.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            boolean enabled = (Boolean) newValue;
            if (enabled) {
              dcContext.setConfigInt(CONFIG_BCC_SELF, 1);
              return true;
            } else {
              new AlertDialog.Builder(requireContext())
                  .setMessage(R.string.pref_multidevice_change_warn)
                  .setPositiveButton(
                      R.string.ok,
                      (dialogInterface, i) -> {
                        dcContext.setConfigInt(CONFIG_BCC_SELF, 0);
                        ((CheckBoxPreference) preference).setChecked(false);
                      })
                  .setNegativeButton(R.string.cancel, null)
                  .show();
              return false;
            }
          });
    }

    Preference webdav = findPreference("pref_webdav");
    if (webdav != null) {
      webdav.setOnPreferenceClickListener(
          preference -> {
            startActivity(new Intent(requireActivity(), WebDavSettingsActivity.class));
            return true;
          });
    }
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_account);
  }

  @Override
  public void onResume() {
    super.onResume();
    Objects.requireNonNull(
            ((ApplicationPreferencesActivity) requireActivity()).getSupportActionBar())
        .setTitle(R.string.pref_account);
    if (multiDeviceCheckbox != null) {
      multiDeviceCheckbox.setChecked(0 != dcContext.getConfigInt(CONFIG_BCC_SELF));
    }
  }
}