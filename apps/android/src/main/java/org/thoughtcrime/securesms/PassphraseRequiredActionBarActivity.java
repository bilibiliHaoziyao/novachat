package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.oobe.OobeActivity;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.util.Prefs;

public abstract class PassphraseRequiredActionBarActivity extends BaseActionBarActivity {
  private static final String TAG = "PassReqBarActivity";

  @Override
  protected final void onCreate(Bundle savedInstanceState) {
    Log.w(TAG, "onCreate(" + savedInstanceState + ")");

    if (allowInLockedMode()) {
      super.onCreate(savedInstanceState);
      onCreate(savedInstanceState, true);
      return;
    }

    if (GenericForegroundService.isForegroundTaskStarted()) {
      // this does not prevent intent set by onNewIntent(),
      // however, at least during onboarding,
      // this catches a lot of situations with otherwise weird app states.
      super.onCreate(savedInstanceState);
      finish();
      return;
    }

    // Nova Chat: first-run "out of the box experience". It is shown to brand new installs and,
    // until it was finished once, to anyone who started it before (e.g. after creating an account
    // in one of the external flows). Existing accounts are never pulled into it.
    Context context = getApplicationContext();
    boolean configured = DcHelper.isConfigured(context);
    boolean oobeCompleted = Prefs.isOobeCompleted(this);
    boolean oobePending = Prefs.isOobeStarted(this) && !oobeCompleted;
    boolean needsOobe = oobePending || (!configured && !oobeCompleted);

    if (needsOobe || !configured) {
      Intent intent =
          new Intent(this, needsOobe ? OobeActivity.class : WelcomeActivity.class);
      startActivity(intent);
      super.onCreate(savedInstanceState);
      finish();
    } else {
      super.onCreate(savedInstanceState);
    }

    if (!isFinishing()) {
      onCreate(savedInstanceState, true);
    }
  }

  protected void onCreate(Bundle savedInstanceState, boolean ready) {}

  // "Locked Mode" is when the account is not configured (Welcome screen)
  // or when sharing a backup (Add second device).
  // The app is "locked" to an activity if you will.
  // In "Locked Mode" the user should not leave that activity otherwise the state would be lost -
  // so eg. tapping app icon or notifications MUST NOT replace activity stack.
  // However, sometimes it is fine to allow to pushing activities in these situations,
  // like to see the logs or offline help.
  protected boolean allowInLockedMode() {
    return false;
  }
}
