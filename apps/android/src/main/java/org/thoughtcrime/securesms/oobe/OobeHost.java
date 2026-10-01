package org.thoughtcrime.securesms.oobe;

import androidx.annotation.StringRes;

/**
 * The window chrome of the first-run wizard. Implemented by {@link OobeActivity} and used by the
 * individual steps to control the top progress indicator and the bottom buttons.
 */
public interface OobeHost {

  /** Moves to the next step, or finishes the wizard when the last step is reached. */
  void goNext();

  /** Moves back to the previous step. */
  void goBack();

  /** Marks the wizard as completed and opens the main UI. */
  void finishOobe();

  /** The step currently displayed, {@code 0}-based. */
  int getStepIndex();

  void setPrimaryText(@StringRes int resId);

  void setPrimaryEnabled(boolean enabled);

  void setPrimaryVisible(boolean visible);

  void setSkipVisible(boolean visible);

  void setSkipText(@StringRes int resId);
}