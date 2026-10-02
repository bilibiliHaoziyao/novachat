package org.thoughtcrime.securesms.preferences;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import java.util.Objects;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ConnectivityActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.proxy.ProxySettingsActivity;
import org.thoughtcrime.securesms.relay.RelayListActivity;
import org.thoughtcrime.securesms.util.ScreenLockUtil;

/** Settings &rarr; Network: connectivity status, relays and the proxy. */
public class NetworkPreferenceFragment extends ListSummaryPreferenceFragment {

  private ActivityResultLauncher<Intent> screenLockLauncher;
  private Preference connectivity;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    screenLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                openRelayListActivity();
              }
            });

    connectivity = findPreference("pref_connectivity");
    if (connectivity != null) {
      connectivity.setOnPreferenceClickListener(
          preference -> {
            startActivity(new Intent(requireActivity(), ConnectivityActivity.class));
            return true;
          });
    }

    Preference relayListBtn = findPreference("pref_relay_list_button");
    if (relayListBtn != null) {
      relayListBtn.setOnPreferenceClickListener(
          preference -> {
            boolean result =
                ScreenLockUtil.applyScreenLock(
                    requireActivity(),
                    getString(R.string.transports),
                    getString(R.string.enter_system_secret_to_continue),
                    screenLockLauncher);
            if (!result) {
              openRelayListActivity();
            }
            return true;
          });
    }

    Preference proxySettings = findPreference("proxy_settings_button");
    if (proxySettings != null) {
      proxySettings.setOnPreferenceClickListener(
          preference -> {
            startActivity(new Intent(requireActivity(), ProxySettingsActivity.class));
            return true;
          });
    }
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_network);
  }

  @Override
  public void onResume() {
    super.onResume();
    Objects.requireNonNull(
            ((ApplicationPreferencesActivity) requireActivity()).getSupportActionBar())
        .setTitle(R.string.pref_network);
    updateConnectivitySummary();
  }

  private void updateConnectivitySummary() {
    if (connectivity != null) {
      connectivity.setSummary(
          DcHelper.getConnectivitySummary(
              requireActivity(), getString(R.string.connectivity_connected)));
    }
  }

  private void openRelayListActivity() {
    startActivity(new Intent(requireActivity(), RelayListActivity.class));
  }
}