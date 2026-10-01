package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.muhan.MuhanAiClient;
import org.thoughtcrime.securesms.muhan.MuhanAiConfig;
import org.thoughtcrime.securesms.muhan.MuhanAiMessage;
import org.thoughtcrime.securesms.muhan.MuhanAiStore;
import org.thoughtcrime.securesms.util.Prefs;

/**
 * Nova Chat: settings for the "MuHan Intelligence" AI assistant.
 *
 * <p>Lets the user enable/disable the pinned chat entry, point the assistant at any OpenAI-compatible
 * endpoint (address, key, model), override the built-in persona prompt and test the connection.
 */
public class MuhanAiPreferenceFragment extends CorrectedPreferenceFragment {

  private static final String KEY_TEST = "pref_muhan_ai_test";
  private static final String KEY_CLEAR = "pref_muhan_ai_clear";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_muhan_ai);

    EditTextPreference baseUrl = findPreference(Prefs.MUHAN_AI_BASE_URL_PREF);
    baseUrl.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          refreshSummaries();
          return true;
        });

    EditTextPreference apiKey = findPreference(Prefs.MUHAN_AI_API_KEY_PREF);
    apiKey.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          refreshSummaries();
          return true;
        });

    EditTextPreference model = findPreference(Prefs.MUHAN_AI_MODEL_PREF);
    model.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          refreshSummaries();
          return true;
        });

    EditTextPreference transcribeModel = findPreference(Prefs.MUHAN_AI_TRANSCRIBE_MODEL_PREF);
    if (transcribeModel != null) {
      transcribeModel.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            refreshSummaries();
            return true;
          });
    }

    ListPreference mediaMode = findPreference(Prefs.MUHAN_AI_MEDIA_MODE_PREF);
    if (mediaMode != null) {
      mediaMode.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            // the new value is persisted after this callback, refresh once that has happened
            mainHandler.post(this::refreshSummaries);
            return true;
          });
    }

    ListPreference apiSource = findPreference(Prefs.MUHAN_AI_API_SOURCE_PREF);
    if (apiSource != null) {
      apiSource.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            mainHandler.post(this::refreshSummaries);
            return true;
          });
    }

    findPreference(KEY_TEST).setOnPreferenceClickListener(p -> { testConnection(); return true; });
    findPreference(KEY_CLEAR).setOnPreferenceClickListener(p -> { confirmClear(); return true; });

    refreshSummaries();
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshSummaries();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdownNow();
  }

  private void refreshSummaries() {
    Context context = requireContext();
    setValueSummary(
        Prefs.MUHAN_AI_BASE_URL_PREF,
        R.string.muhan_ai_base_url_summary,
        MuhanAiConfig.getBaseUrl(context));
    setValueSummary(
        Prefs.MUHAN_AI_API_KEY_PREF,
        R.string.muhan_ai_api_key_summary,
        maskKey(MuhanAiConfig.getApiKey(context)));
    setValueSummary(
        Prefs.MUHAN_AI_MODEL_PREF,
        R.string.muhan_ai_model_summary,
        MuhanAiConfig.getModel(context));
    setValueSummary(
        Prefs.MUHAN_AI_SYSTEM_PROMPT_PREF,
        R.string.muhan_ai_system_prompt_summary,
        MuhanAiConfig.getCustomPrompt(context));
    setValueSummary(
        Prefs.MUHAN_AI_TRANSCRIBE_MODEL_PREF,
        R.string.muhan_ai_transcribe_model_summary,
        MuhanAiConfig.getTranscribeModel(context));

    ListPreference mediaMode = findPreference(Prefs.MUHAN_AI_MEDIA_MODE_PREF);
    if (mediaMode != null) {
      CharSequence entry = mediaMode.getEntry();
      mediaMode.setSummary(
          getString(R.string.muhan_ai_media_mode_summary) + "\n" + (entry == null ? "" : entry));
    }

    ListPreference apiSource = findPreference(Prefs.MUHAN_AI_API_SOURCE_PREF);
    if (apiSource != null) {
      CharSequence entry = apiSource.getEntry();
      apiSource.setSummary(
          getString(R.string.muhan_ai_api_source_summary) + "\n" + (entry == null ? "" : entry));
    }

    // the address, key and model only matter for a custom endpoint
    boolean custom = !MuhanAiConfig.isBuiltinApi(context);
    setEnabled(Prefs.MUHAN_AI_BASE_URL_PREF, custom);
    setEnabled(Prefs.MUHAN_AI_API_KEY_PREF, custom);
    setEnabled(Prefs.MUHAN_AI_MODEL_PREF, custom);
  }

  private void setEnabled(String key, boolean enabled) {
    Preference preference = findPreference(key);
    if (preference != null) {
      preference.setEnabled(enabled);
    }
  }

  private void setValueSummary(String key, int summaryRes, String value) {
    EditTextPreference preference = findPreference(key);
    if (preference == null) {
      return;
    }
    String summary = getString(summaryRes);
    if (!TextUtils.isEmpty(value)) {
      summary = summary + "\n" + value;
    }
    preference.setSummary(summary);
  }

  private static String maskKey(String key) {
    if (TextUtils.isEmpty(key)) {
      return "";
    }
    return key.length() <= 4 ? "****" : "****" + key.substring(key.length() - 4);
  }

  private void testConnection() {
    Context context = requireContext();
    if (!MuhanAiConfig.isConfigured(context)) {
      Toast.makeText(context, R.string.muhan_ai_not_configured, Toast.LENGTH_LONG).show();
      return;
    }
    final String baseUrl = MuhanAiConfig.getBaseUrl(context);
    final String apiKey = MuhanAiConfig.getApiKey(context);
    final String model = MuhanAiConfig.getModel(context);
    final List<MuhanAiMessage> probe =
        Collections.singletonList(new MuhanAiMessage(MuhanAiMessage.ROLE_USER, "ping"));

    Toast.makeText(context, R.string.muhan_ai_test_running, Toast.LENGTH_SHORT).show();
    executor.execute(
        () -> {
          String message;
          int duration;
          try {
            MuhanAiClient.sendBlocking(baseUrl, apiKey, model, probe);
            message = getString(R.string.muhan_ai_test_ok);
            duration = Toast.LENGTH_SHORT;
          } catch (Exception e) {
            message = getString(R.string.muhan_ai_test_failed, String.valueOf(e.getMessage()));
            duration = Toast.LENGTH_LONG;
          }
          final String result = message;
          final int resultDuration = duration;
          mainHandler.post(
              () -> {
                if (isAdded()) {
                  Toast.makeText(requireContext(), result, resultDuration).show();
                }
              });
        });
  }

  private void confirmClear() {
    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.muhan_ai_clear)
        .setMessage(R.string.muhan_ai_clear_confirm)
        .setPositiveButton(
            R.string.muhan_ai_clear,
            (d, w) -> {
              MuhanAiStore.clear(
                  requireContext(), DcHelper.getContext(requireContext()).getAccountId());
              Toast.makeText(requireContext(), R.string.muhan_ai_cleared, Toast.LENGTH_SHORT).show();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  public static CharSequence getSummary(Context context) {
    return context.getString(MuhanAiConfig.isEnabled(context) ? R.string.on : R.string.off);
  }
}