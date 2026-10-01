package org.thoughtcrime.securesms.oobe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.LanguageUtil;

/** Step 1: pick the app language. Applied immediately. */
public class OobeLanguageFragment extends OobeFragment {

  private final List<View> rows = new ArrayList<>();
  private final List<String> values = new ArrayList<>();
  private String selected;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_oobe_language, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    LinearLayout list = view.findViewById(R.id.oobe_language_list);
    selected = LanguageUtil.getSelectedTag(requireContext());

    String[] valueArray = getResources().getStringArray(R.array.pref_language_values);
    String[] entryArray = getResources().getStringArray(R.array.pref_language_entries);
    for (int i = 0; i < valueArray.length; i++) {
      final String value = valueArray[i];
      View row = getLayoutInflater().inflate(R.layout.oobe_choice_row, list, false);
      ((TextView) row.findViewById(R.id.oobe_row_title)).setText(entryArray[i]);
      row.setOnClickListener(v -> select(value));
      values.add(value);
      rows.add(row);
      list.addView(row);
    }
    refreshSelection();
  }

  private void select(String value) {
    selected = value;
    refreshSelection();
    LanguageUtil.apply(requireContext(), value);
  }

  private void refreshSelection() {
    for (int i = 0; i < rows.size(); i++) {
      rows.get(i)
          .findViewById(R.id.oobe_row_check)
          .setVisibility(values.get(i).equals(selected) ? View.VISIBLE : View.GONE);
    }
  }
}