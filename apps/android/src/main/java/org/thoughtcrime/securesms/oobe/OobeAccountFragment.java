package org.thoughtcrime.securesms.oobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.ClassicLoginActivity;
import org.thoughtcrime.securesms.InstantOnboardingActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WelcomeActivity;
import org.thoughtcrime.securesms.connect.DcHelper;

/**
 * Step 3: create or restore an account. Every entry point opens the original Nova Chat flow — the
 * very same pages that are used outside the wizard — and advances the wizard once that flow reports
 * a configured account, so the user is taken back into the remaining steps.
 */
public class OobeAccountFragment extends OobeFragment {

  private static final int REQUEST_ACCOUNT = 2601;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_oobe_account, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    view.findViewById(R.id.oobe_account_instant_button)
        .setOnClickListener(
            v -> launch(new Intent(requireContext(), InstantOnboardingActivity.class)));

    view.findViewById(R.id.oobe_account_email_button)
        .setOnClickListener(
            v -> launch(new Intent(requireContext(), ClassicLoginActivity.class)));

    view.findViewById(R.id.oobe_account_migrate_button)
        .setOnClickListener(
            v -> {
              Intent intent = new Intent(requireContext(), WelcomeActivity.class);
              intent.putExtra(WelcomeActivity.EXTRA_OPEN_SIGN_IN_OPTIONS, true);
              launch(intent);
            });
  }

  @Override
  protected void onChromeReady() {
    if (DcHelper.isConfigured(requireContext())) {
      // wizard re-run with an account already in place: just move on
      host().setPrimaryVisible(true);
      host().setPrimaryText(R.string.oobe_account_continue);
    } else {
      // the in-page buttons are the call to action on this step
      host().setPrimaryVisible(false);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_ACCOUNT && resultCode == Activity.RESULT_OK) {
      // the original flow reported a configured account — resume the wizard
      host().goNext();
    }
  }

  private void launch(Intent intent) {
    intent.putExtra(OobeActivity.EXTRA_FROM_OOBE, true);
    startActivityForResult(intent, REQUEST_ACCOUNT);
  }
}