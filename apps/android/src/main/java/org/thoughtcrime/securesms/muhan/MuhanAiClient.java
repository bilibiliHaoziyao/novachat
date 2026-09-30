package org.thoughtcrime.securesms.muhan;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nova Chat: a minimal client for OpenAI-compatible chat completion APIs.
 *
 * <p>Works against any server exposing {@code POST {baseUrl}/chat/completions} (OpenAI, DeepSeek,
 * Moonshot, Ollama, vLLM, ...). Supports both the streaming (SSE) and the plain JSON flavour.
 * Requests run on a shared background executor; the {@link Callback} is invoked on that background
 * thread, so callers must post UI work to the main thread themselves.
 */
public final class MuhanAiClient {

  private static final String TAG = "MuhanAiClient";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

  public interface Callback {
    /** Called for every streamed chunk; only used when {@code stream} is enabled. */
    void onDelta(String delta);

    void onSuccess(String fullText);

    void onError(Exception error);
  }

  /** Handle returned by {@link #send}, used to abort an in-flight request. */
  public static final class Request {
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    void attach(HttpURLConnection connection) {
      this.connection = connection;
      if (cancelled && connection != null) {
        connection.disconnect();
      }
    }

    public void cancel() {
      cancelled = true;
      HttpURLConnection connection = this.connection;
      if (connection != null) {
        try {
          connection.disconnect();
        } catch (Exception ignored) {
        }
      }
    }

    public boolean isCancelled() {
      return cancelled;
    }
  }

  private MuhanAiClient() {}

  /**
   * Blocking variant, useful for the settings "test connection" action. Throws on any failure.
   *
   * @return the assistant reply (may be empty).
   */
  public static String sendBlocking(
      @NonNull String baseUrl,
      @NonNull String apiKey,
      @NonNull String model,
      @NonNull List<MuhanAiMessage> messages)
      throws Exception {
    return perform(new Request(), baseUrl, apiKey, model, messages, false, null);
  }

  public static Request send(
      @NonNull String baseUrl,
      @NonNull String apiKey,
      @NonNull String model,
      @NonNull List<MuhanAiMessage> messages,
      boolean stream,
      @NonNull Callback callback) {
    Request handle = new Request();
    EXECUTOR.execute(
        () -> {
          try {
            String full = perform(handle, baseUrl, apiKey, model, messages, stream, callback);
            if (!handle.isCancelled()) {
              callback.onSuccess(full);
            }
          } catch (Exception e) {
            if (!handle.isCancelled()) {
              callback.onError(e);
            }
          }
        });
    return handle;
  }

  private static String perform(
      Request handle,
      String baseUrl,
      String apiKey,
      String model,
      List<MuhanAiMessage> messages,
      boolean stream,
      Callback callback)
      throws Exception {
    if (TextUtils.isEmpty(model)) {
      throw new IllegalArgumentException("model is not configured");
    }
    String endpoint = endpoint(baseUrl);

    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    handle.attach(connection);
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(30_000);
    connection.setReadTimeout(120_000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connection.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
    if (!TextUtils.isEmpty(apiKey)) {
      connection.setRequestProperty("Authorization", "Bearer " + apiKey);
    }

    ObjectNode root = MAPPER.createObjectNode();
    root.put("model", model);
    root.put("stream", stream);
    ArrayNode array = root.putArray("messages");
    for (MuhanAiMessage message : messages) {
      ObjectNode node = array.addObject();
      node.put("role", message.role);
      node.put("content", message.content);
    }

    try (OutputStream out = connection.getOutputStream()) {
      out.write(MAPPER.writeValueAsBytes(root));
      out.flush();
    }

    int code = connection.getResponseCode();
    if (code < 200 || code >= 300) {
      String detail = readStream(connection.getErrorStream());
      throw new HttpException(code, TextUtils.isEmpty(detail) ? "HTTP " + code : detail);
    }

    return stream
        ? readStreamed(handle, connection.getInputStream(), callback)
        : parseFullResponse(readStream(connection.getInputStream()));
  }

  private static String readStreamed(Request handle, InputStream input, Callback callback)
      throws Exception {
    StringBuilder full = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (handle.isCancelled()) {
          break;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
          continue;
        }
        String data = trimmed.substring("data:".length()).trim();
        if ("[DONE]".equals(data)) {
          break;
        }
        JsonNode node = MAPPER.readTree(data);
        JsonNode choices = node.path("choices");
        if (choices.isArray() && choices.size() > 0) {
          JsonNode content = choices.get(0).path("delta").path("content");
          if (!content.isMissingNode() && !content.isNull()) {
            String chunk = content.asText();
            if (!chunk.isEmpty()) {
              full.append(chunk);
              callback.onDelta(chunk);
            }
          }
        }
      }
    }
    return full.toString();
  }

  private static String parseFullResponse(String body) throws Exception {
    JsonNode node = MAPPER.readTree(body);
    JsonNode content = node.path("choices").path(0).path("message").path("content");
    if (content.isMissingNode() || content.isNull()) {
      // some servers answer with `text` instead of `choices`
      JsonNode text = node.path("text");
      return text.isMissingNode() ? "" : text.asText();
    }
    return content.asText();
  }

  private static String endpoint(String baseUrl) {
    String base = TextUtils.isEmpty(baseUrl) ? "" : baseUrl.trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.endsWith("/chat/completions")) {
      return base;
    }
    return base + "/chat/completions";
  }

  private static String readStream(InputStream input) {
    if (input == null) {
      return "";
    }
    try (InputStream stream = input;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      byte[] chunk = new byte[4096];
      int read;
      while ((read = stream.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return buffer.toString(StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      Log.w(TAG, "Cannot read response: " + e.getMessage());
      return "";
    }
  }

  /** Raised when the server answers with a non-2xx status code. */
  public static class HttpException extends Exception {
    public final int code;

    public HttpException(int code, String message) {
      super(message);
      this.code = code;
    }
  }
}