package org.thoughtcrime.securesms.preferences;

import static android.text.InputType.TYPE_TEXT_VARIATION_URI;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import java.util.Objects;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.LogViewActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.util.Prefs;

/**
 * Settings &rarr; Advanced: the log and the experimental features. Everything that used to live here
 * but belongs to a topic (encryption, network, backup) moved to the matching settings page.
 */
public class AdvancedPreferenceFragment extends ListSummaryPreferenceFragment
    implements DcEventCenter.DcEventDelegate {
  private static final String TAG = "AdvancedPreferenceFrag";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    Preference submitDebugLog = this.findPreference("pref_view_log");
    if (submitDebugLog != null) {
      submitDebugLog.setOnPreferenceClickListener(new ViewLogListener());
    }

    Preference webxdcStore = this.findPreference(Prefs.WEBXDC_STORE_URL_PREF);
    if (webxdcStore != null) {
      webxdcStore.setOnPreferenceClickListener(new WebxdcStoreUrlListener());
    }
    updateWebxdcStoreSummary();

    Preference locationStreamingEnabled = this.findPreference("pref_location_streaming_enabled");
    if (locationStreamingEnabled != null) {
      locationStreamingEnabled.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            if ((Boolean) newValue) {
              new AlertDialog.Builder(requireActivity())
                  .setTitle("Thanks for trying out \"Location Streaming\"!")
                  .setMessage(
                      "• You will find a corresponding option in the attach menu (the paper clip) of each chat now\n\n"
                          + "• If you want to quit the experimental feature, you can disable it at \"Settings / Advanced\"")
                  .setCancelable(false)
                  .setPositiveButton(R.string.ok, null)
                  .show();
            }
            return true;
          });
    }
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_advanced);
  }

  @Override
  public void onResume() {
    super.onResume();
    Objects.requireNonNull(
            ((ApplicationPreferencesActivity) requireActivity()).getSupportActionBar())
        .setTitle(R.string.menu_advanced);
  }

  public static @NonNull String getVersion(@Nullable Context context) {
    try {
      if (context == null) return "";

      String app = context.getString(R.string.app_name);
      String version =
          context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;

      return String.format("%s %s", app, version);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
      return context.getString(R.string.app_name);
    }
  }

  private class ViewLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
      final Intent intent = new Intent(requireActivity(), LogViewActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class WebxdcStoreUrlListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
      View gl = View.inflate(requireActivity(), R.layout.single_line_input, null);
      EditText inputField = gl.findViewById(R.id.input_field);
      inputField.setHint(Prefs.DEFAULT_WEBXDC_STORE_URL);
      inputField.setText(Prefs.getWebxdcStoreUrl(requireActivity()));
      inputField.setSelection(inputField.getText().length());
      inputField.setInputType(TYPE_TEXT_VARIATION_URI);
      new AlertDialog.Builder(requireActivity())
          .setTitle(R.string.webxdc_store_url)
          .setMessage(R.string.webxdc_store_url_explain)
          .setView(gl)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(
              android.R.string.ok,
              (dlg, btn) -> {
                Prefs.setWebxdcStoreUrl(requireActivity(), inputField.getText().toString());
                updateWebxdcStoreSummary();
              })
          .show();
      return true;
    }
  }

  private void updateWebxdcStoreSummary() {
    Preference preference = this.findPreference(Prefs.WEBXDC_STORE_URL_PREF);
    if (preference != null) {
      preference.setSummary(Prefs.getWebxdcStoreUrl(requireActivity()));
    }
  }
}