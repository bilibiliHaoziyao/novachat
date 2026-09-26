package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.preferences.AdvancedPreferenceFragment;
import org.thoughtcrime.securesms.util.IntentUtils;
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