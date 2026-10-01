package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.util.Prefs;

/**
 * Nova Chat: configuration of the "MuHan Intelligence" (慕寒智能) AI assistant.
 *
 * <p>The assistant talks to any OpenAI-compatible {@code /chat/completions} endpoint. All settings
 * live in the app {@link android.content.SharedPreferences} and are shared by every account.
 */
public final class MuhanAiConfig {

  /**
   * Built-in persona. It is sent as the {@code system} message only when the user did not write a
   * custom prompt; a custom prompt replaces (overrides) this default entirely.
   */
  public static final String DEFAULT_SYSTEM_PROMPT =
      "你现在是MuHan Intelligence，一个AI智能助手，中文名为慕寒智能";

  /** Images/audio are sent inline as {@code image_url} / {@code input_audio} content parts. */
  public static final String MEDIA_MODE_MULTIMODAL = "multimodal";

  /** Audio is first transcribed to text ({@code /audio/transcriptions}) and sent as plain text. */
  public static final String MEDIA_MODE_TRANSCRIBE = "transcribe";

  private MuhanAiConfig() {}

  public static boolean isEnabled(@NonNull Context context) {
    return Prefs.getBooleanPreference(
        context, Prefs.MUHAN_AI_ENABLED_PREF, Prefs.MUHAN_AI_ENABLED_DEFAULT);
  }

  public static String getBaseUrl(@NonNull Context context) {
    String url =
        Prefs.getStringPreference(
            context, Prefs.MUHAN_AI_BASE_URL_PREF, Prefs.MUHAN_AI_BASE_URL_DEFAULT);
    return TextUtils.isEmpty(url) ? Prefs.MUHAN_AI_BASE_URL_DEFAULT : url.trim();
  }

  public static String getApiKey(@NonNull Context context) {
    return Prefs.getStringPreference(context, Prefs.MUHAN_AI_API_KEY_PREF, "").trim();
  }

  public static String getModel(@NonNull Context context) {
    return Prefs.getStringPreference(context, Prefs.MUHAN_AI_MODEL_PREF, "").trim();
  }

  /** The user's custom prompt, may be empty. */
  public static String getCustomPrompt(@NonNull Context context) {
    return Prefs.getStringPreference(context, Prefs.MUHAN_AI_SYSTEM_PROMPT_PREF, "").trim();
  }

  /** The {@code system} prompt actually sent: custom prompt if present, otherwise the default. */
  public static String getEffectiveSystemPrompt(@NonNull Context context) {
    String custom = getCustomPrompt(context);
    return TextUtils.isEmpty(custom) ? DEFAULT_SYSTEM_PROMPT : custom;
  }

  /** How images/audio are handed to the model, see {@link #MEDIA_MODE_MULTIMODAL}. */
  public static String getMediaMode(@NonNull Context context) {
    String mode =
        Prefs.getStringPreference(
            context, Prefs.MUHAN_AI_MEDIA_MODE_PREF, Prefs.MUHAN_AI_MEDIA_MODE_DEFAULT);
    return MEDIA_MODE_TRANSCRIBE.equals(mode) ? MEDIA_MODE_TRANSCRIBE : MEDIA_MODE_MULTIMODAL;
  }

  /** Whether audio is transcribed to text before being sent. */
  public static boolean isTranscribeMode(@NonNull Context context) {
    return MEDIA_MODE_TRANSCRIBE.equals(getMediaMode(context));
  }

  /** Speech-to-text model, only used in {@link #MEDIA_MODE_TRANSCRIBE} mode. */
  public static String getTranscribeModel(@NonNull Context context) {
    String model =
        Prefs.getStringPreference(
            context,
            Prefs.MUHAN_AI_TRANSCRIBE_MODEL_PREF,
            Prefs.MUHAN_AI_TRANSCRIBE_MODEL_DEFAULT);
    return TextUtils.isEmpty(model) ? Prefs.MUHAN_AI_TRANSCRIBE_MODEL_DEFAULT : model.trim();
  }

  /**
   * Whether the assistant can actually be used, i.e. an API address (defaulted) and a model are
   * set. The API key is optional, some self-hosted endpoints (Ollama, vLLM, ...) do not need one.
   */
  public static boolean isConfigured(@NonNull Context context) {
    return !TextUtils.isEmpty(getBaseUrl(context)) && !TextUtils.isEmpty(getModel(context));
  }
}