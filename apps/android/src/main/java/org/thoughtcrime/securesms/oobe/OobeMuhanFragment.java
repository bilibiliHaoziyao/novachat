package org.thoughtcrime.securesms.oobe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.muhan.MuhanAiConfig;
import org.thoughtcrime.securesms.util.Prefs;

/**
 * Step 5: introduce "MuHan Intelligence" and let the user choose where it talks to — the built-in
 * endpoint or their own OpenAI-compatible one.
 */
public class OobeMuhanFragment extends OobeFragment {

  private final List<View> rows = new ArrayList<>();
  private final List<String> sources = new ArrayList<>();

  private View customForm;
  private EditText baseUrlInput;
  private EditText apiKeyInput;
  private EditText modelInput;
  private String selected;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_oobe_muhan, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    LinearLayout list = view.findViewById(R.id.oobe_muhan_source_list);
    customForm = view.findViewById(R.id.oobe_muhan_custom_form);
    baseUrlInput = view.findViewById(R.id.oobe_muhan_base_url_input);
    apiKeyInput = view.findViewById(R.id.oobe_muhan_api_key_input);
    modelInput = view.findViewById(R.id.oobe_muhan_model_input);

    selected = MuhanAiConfig.getApiSource(requireContext());

    String[] values = {MuhanAiConfig.API_SOURCE_BUILTIN, MuhanAiConfig.API_SOURCE_CUSTOM};
    String[] labels = {
      getString(R.string.oobe_muhan_source_builtin), getString(R.string.oobe_muhan_source_custom)
    };
    for (int i = 0; i < values.length; i++) {
      final String value = values[i];
      View row = getLayoutInflater().inflate(R.layout.oobe_choice_row, list, false);
      ((TextView) row.findViewById(R.id.oobe_row_title)).setText(labels[i]);
      row.setOnClickListener(v -> select(value));
      sources.add(value);
      rows.add(row);
      list.addView(row);
    }

    // pre-filled with the values already stored, may be left empty
    baseUrlInput.setText(
        Prefs.getStringPreference(requireContext(), Prefs.MUHAN_AI_BASE_URL_PREF, ""));
    apiKeyInput.setText(
        Prefs.getStringPreference(requireContext(), Prefs.MUHAN_AI_API_KEY_PREF, ""));
    modelInput.setText(Prefs.getStringPreference(requireContext(), Prefs.MUHAN_AI_MODEL_PREF, ""));

    refreshSelection();
  }

  @Override
  protected int primaryTextRes() {
    return R.string.oobe_muhan_done;
  }

  @Override
  public void onPrimary() {
    MuhanAiConfig.setApiSource(requireContext(), selected);
    if (MuhanAiConfig.API_SOURCE_CUSTOM.equals(selected)) {
      MuhanAiConfig.setBaseUrl(requireContext(), baseUrlInput.getText().toString());
      MuhanAiConfig.setApiKey(requireContext(), apiKeyInput.getText().toString());
      MuhanAiConfig.setModel(requireContext(), modelInput.getText().toString());
    }
    super.onPrimary();
  }

  private void select(String source) {
    selected = source;
    refreshSelection();
  }

  private void refreshSelection() {
    for (int i = 0; i < rows.size(); i++) {
      rows.get(i)
          .findViewById(R.id.oobe_row_check)
          .setVisibility(sources.get(i).equals(selected) ? View.VISIBLE : View.GONE);
    }
    customForm.setVisibility(
        MuhanAiConfig.API_SOURCE_CUSTOM.equals(selected) ? View.VISIBLE : View.GONE);
  }
}