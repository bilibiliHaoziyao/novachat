package org.thoughtcrime.securesms.oobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebDavSettingsActivity;

/** Step 4: optional WebDAV backup. */
public class OobeWebDavFragment extends OobeFragment {

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_oobe_webdav, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    view.findViewById(R.id.oobe_webdav_configure_button)
        .setOnClickListener(
            v -> startActivity(new Intent(requireContext(), WebDavSettingsActivity.class)));
  }
}