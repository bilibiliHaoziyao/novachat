package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import org.thoughtcrime.securesms.oobe.OobeActivity;
import org.thoughtcrime.securesms.preferences.AdvancedPreferenceFragment;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Nova Chat: the "About" screen.
 *
 * <p>Replaces the removed "Donate" and "Help" entries on the settings page. Shows the app name,
 * version and a short description together with a link to the source code.
 */
public class AboutActivity extends BaseActionBarActivity {

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_about);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    // keep the scrollable content clear of the navigation bar (edge-to-edge)
    ViewUtil.applyWindowInsets(findViewById(R.id.about_scroll), false, false, false, true);

    TextView versionView = findViewById(R.id.about_version);
    versionView.setText(AdvancedPreferenceFragment.getVersion(this));

    findViewById(R.id.about_source_button)
        .setOnClickListener(
            v -> IntentUtils.showInBrowser(this, "https://github.com/bilibiliHaoziyao/novachat"));

    findViewById(R.id.about_oobe_button).setOnClickListener(v -> confirmRerunOobe());
  }

  /** Nova Chat: forget that the setup wizard ran, so it is shown again on the next start. */
  private void confirmRerunOobe() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.oobe_rerun_title)
        .setMessage(R.string.oobe_rerun_message)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(
            R.string.oobe_rerun_positive,
            (dialog, which) -> {
              Prefs.setOobeCompleted(this, false);
              Prefs.setOobeStarted(this, false);
              Prefs.setOobeStep(this, 0);

              Intent intent = new Intent(this, OobeActivity.class);
              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
              startActivity(intent);
              finish();
            })
        .show();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}