package org.thoughtcrime.securesms.oobe;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import org.thoughtcrime.securesms.R;

/**
 * Base class of the wizard steps. Applies the default chrome (primary button, optional "skip"
 * link) whenever the step becomes visible and delegates the button taps to overridable hooks.
 */
public abstract class OobeFragment extends Fragment {

  protected final OobeHost host() {
    return (OobeHost) requireActivity();
  }

  @Override
  public void onResume() {
    super.onResume();
    OobeHost host = host();
    host.setPrimaryVisible(true);
    host.setPrimaryEnabled(true);
    host.setPrimaryText(primaryTextRes());
    host.setSkipText(skipTextRes());
    host.setSkipVisible(isSkippable());
    onChromeReady();
  }

  @StringRes
  protected int primaryTextRes() {
    return R.string.oobe_continue;
  }

  @StringRes
  protected int skipTextRes() {
    return R.string.oobe_skip;
  }

  /** Whether this step may be left without any configuration. */
  protected boolean isSkippable() {
    return false;
  }

  /** Hook for steps that need to adjust the chrome after the defaults have been applied. */
  protected void onChromeReady() {}

  /** Called when the primary button is tapped; advances by default. */
  public void onPrimary() {
    host().goNext();
  }

  /** Called when the "skip" link is tapped; advances by default. */
  public void onSkip() {
    host().goNext();
  }
}