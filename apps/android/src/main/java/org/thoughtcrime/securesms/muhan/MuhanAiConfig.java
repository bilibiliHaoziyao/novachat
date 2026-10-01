package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
      "你现在是MuHan Intelligence，中文名为慕寒智能，由MuHan Studio开发";

  /** Images/audio are sent inline as {@code image_url} / {@code input_audio} content parts. */
  public static final String MEDIA_MODE_MULTIMODAL = "multimodal";

  /** Audio is first transcribed to text ({@code /audio/transcriptions}) and sent as plain text. */
  public static final String MEDIA_MODE_TRANSCRIBE = "transcribe";

  /** The endpoint baked into the app is used, see {@link #getBaseUrl}. */
  public static final String API_SOURCE_BUILTIN = "builtin";

  /** The endpoint configured by the user in the settings is used. */
  public static final String API_SOURCE_CUSTOM = "custom";

  /** Messages a single day may send through the built-in endpoint. */
  public static final int BUILTIN_DAILY_LIMIT = 5;

  private MuhanAiConfig() {}

  public static boolean isEnabled(@NonNull Context context) {
    return Prefs.getBooleanPreference(
        context, Prefs.MUHAN_AI_ENABLED_PREF, Prefs.MUHAN_AI_ENABLED_DEFAULT);
  }

  /** Whether the built-in endpoint or the user's own one is used. */
  public static String getApiSource(@NonNull Context context) {
    String source =
        Prefs.getStringPreference(
            context, Prefs.MUHAN_AI_API_SOURCE_PREF, Prefs.MUHAN_AI_API_SOURCE_DEFAULT);
    return API_SOURCE_CUSTOM.equals(source) ? API_SOURCE_CUSTOM : API_SOURCE_BUILTIN;
  }

  public static boolean isBuiltinApi(@NonNull Context context) {
    return API_SOURCE_BUILTIN.equals(getApiSource(context));
  }

  public static String getBaseUrl(@NonNull Context context) {
    if (isBuiltinApi(context)) {
      return Prefs.MUHAN_AI_BASE_URL_DEFAULT;
    }
    String url = Prefs.getStringPreference(context, Prefs.MUHAN_AI_BASE_URL_PREF, "").trim();
    return TextUtils.isEmpty(url) ? Prefs.MUHAN_AI_BASE_URL_DEFAULT : url;
  }

  public static String getApiKey(@NonNull Context context) {
    if (isBuiltinApi(context)) {
      return Prefs.MUHAN_AI_API_KEY_DEFAULT;
    }
    return Prefs.getStringPreference(context, Prefs.MUHAN_AI_API_KEY_PREF, "").trim();
  }

  public static String getModel(@NonNull Context context) {
    if (isBuiltinApi(context)) {
      return Prefs.MUHAN_AI_MODEL_DEFAULT;
    }
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

  /**
   * Messages still available today. Only the built-in endpoint is rationed; a custom endpoint is
   * always unlimited.
   */
  public static int getRemainingQuota(@NonNull Context context) {
    if (!isBuiltinApi(context)) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, BUILTIN_DAILY_LIMIT - getUsedQuota(context));
  }

  /** Whether another message may be sent right now. */
  public static boolean canSend(@NonNull Context context) {
    return getRemainingQuota(context) > 0;
  }

  /** Books one message against today's budget; only meaningful for the built-in endpoint. */
  public static void recordMessageSent(@NonNull Context context) {
    if (!isBuiltinApi(context)) {
      return;
    }
    String today = today();
    Prefs.setStringPreference(context, Prefs.MUHAN_AI_QUOTA_DATE_PREF, today);
    Prefs.setMuhanAiQuotaCount(context, getUsedQuota(context) + 1);
  }

  /** Messages already sent through the built-in endpoint today; resets at midnight. */
  private static int getUsedQuota(@NonNull Context context) {
    String stored = Prefs.getStringPreference(context, Prefs.MUHAN_AI_QUOTA_DATE_PREF, "");
    return today().equals(stored) ? Prefs.getMuhanAiQuotaCount(context) : 0;
  }

  private static String today() {
    return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
  }
}