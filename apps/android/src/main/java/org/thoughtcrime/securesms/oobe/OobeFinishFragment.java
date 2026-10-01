package org.thoughtcrime.securesms.oobe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.R;

/** Step 6: everything is set up. */
public class OobeFinishFragment extends OobeFragment {

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_oobe_finish, container, false);
  }

  @Override
  protected int primaryTextRes() {
    return R.string.oobe_finish_action;
  }

  @Override
  public void onPrimary() {
    host().finishOobe();
  }
}