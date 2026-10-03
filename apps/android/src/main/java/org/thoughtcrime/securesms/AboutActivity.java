package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
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
 * <p>Hero header (logo, name, version chip, developer) plus two cards: description/license and the
 * actions (source code link, re-run the setup wizard).
 */
public class AboutActivity extends BaseActionBarActivity {

  private int baseContentTopPadding;

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_about);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    // keep the scrollable content clear of the navigation bar (edge-to-edge)
    ViewUtil.applyWindowInsets(findViewById(R.id.about_scroll), false, false, false, true);

    // the window action bar overlays the content in edge-to-edge mode, so the whole scrollable
    // content has to shift down by the measured overlap once the first layout pass completes
    View content = findViewById(R.id.about_content);
    baseContentTopPadding = content.getPaddingTop();
    content
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                shiftContentBelowActionBar(content);
              }
            });

    TextView versionView = findViewById(R.id.about_version);
    versionView.setText(AdvancedPreferenceFragment.getVersion(this));

    findViewById(R.id.about_source_button)
        .setOnClickListener(
            v -> IntentUtils.showInBrowser(this, "https://github.com/bilibiliHaoziyao/novachat"));

    findViewById(R.id.about_oobe_button).setOnClickListener(v -> confirmRerunOobe());
  }

  /**
   * Nova Chat: move the whole scrollable content down so the hero icon starts below the window
   * action bar instead of underneath it. The overlap is measured directly between the action bar
   * container and the content so the shift stays correct on every device and theme mode.
   */
  private void shiftContentBelowActionBar(View content) {
    View actionBarContainer = findViewById(R.id.action_bar_container);
    if (actionBarContainer == null) return;

    int[] containerLocation = new int[2];
    int[] contentLocation = new int[2];
    actionBarContainer.getLocationInWindow(containerLocation);
    content.getLocationInWindow(contentLocation);

    int overlap = containerLocation[1] + actionBarContainer.getHeight() - contentLocation[1];
    if (overlap <= 0) return;

    content.setPadding(
        content.getPaddingLeft(),
        baseContentTopPadding + overlap,
        content.getPaddingRight(),
        content.getPaddingBottom());
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
