package org.thoughtcrime.securesms.oobe;

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
 * Step 3: create or restore an account. Offers the three ways in — instant sign-up, classic email
 * login and migrating from another device — and advances automatically once an account exists.
 */
public class OobeAccountFragment extends OobeFragment {

  private static final String STATE_LAUNCHED = "oobe_account_launched";

  private boolean launched;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      launched = savedInstanceState.getBoolean(STATE_LAUNCHED, false);
    }
  }

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
        .setOnClickListener(v -> launch(InstantOnboardingActivity.class));

    view.findViewById(R.id.oobe_account_email_button)
        .setOnClickListener(v -> launch(ClassicLoginActivity.class));

    view.findViewById(R.id.oobe_account_migrate_button)
        .setOnClickListener(
            v -> {
              launched = true;
              Intent intent = new Intent(requireContext(), WelcomeActivity.class);
              intent.putExtra(WelcomeActivity.EXTRA_OPEN_SIGN_IN_OPTIONS, true);
              intent.putExtra(OobeActivity.EXTRA_FROM_OOBE, true);
              startActivity(intent);
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
  public void onResume() {
    super.onResume();
    if (launched && DcHelper.isConfigured(requireContext())) {
      host().goNext();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(STATE_LAUNCHED, launched);
  }

  private void launch(Class<?> activity) {
    launched = true;
    Intent intent = new Intent(requireContext(), activity);
    intent.putExtra(OobeActivity.EXTRA_FROM_OOBE, true);
    startActivity(intent);
  }
}