package org.thoughtcrime.securesms.muhan;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
    return perform(new Request(), baseUrl, apiKey, model, messages, false, null, "");
  }

  public static Request send(
      @NonNull String baseUrl,
      @NonNull String apiKey,
      @NonNull String model,
      @NonNull List<MuhanAiMessage> messages,
      boolean stream,
      @NonNull Callback callback) {
    return send(baseUrl, apiKey, model, messages, stream, "", callback);
  }

  /**
   * @param transcribeModel when not empty, audio attachments are transcribed through {@code
   *     /audio/transcriptions} and sent as text instead of being inlined; images are always sent
   *     inline.
   */
  public static Request send(
      @NonNull String baseUrl,
      @NonNull String apiKey,
      @NonNull String model,
      @NonNull List<MuhanAiMessage> messages,
      boolean stream,
      @NonNull String transcribeModel,
      @NonNull Callback callback) {
    Request handle = new Request();
    EXECUTOR.execute(
        () -> {
          try {
            String full =
                perform(
                    handle,
                    baseUrl,
                    apiKey,
                    model,
                    messages,
                    stream,
                    callback,
                    transcribeModel);
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
      Callback callback,
      String transcribeModel)
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
      node.set("content", buildContent(message, baseUrl, apiKey, transcribeModel));
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

  /**
   * Builds the {@code content} value of a chat message, inlining image / audio attachments or
   * replacing audio by its transcript, depending on the configured media mode.
   */
  private static JsonNode buildContent(
      MuhanAiMessage message, String baseUrl, String apiKey, String transcribeModel)
      throws Exception {
    String text = message.content == null ? "" : message.content;
    if (!message.hasAttachment()) {
      return MAPPER.getNodeFactory().textNode(text);
    }

    File file = new File(message.attachmentPath);
    if (message.hasImage()) {
      String base64 = MuhanMediaUtil.base64(file);
      if (base64 == null) {
        throw new IOException("cannot read the image attachment");
      }
      String mime =
          TextUtils.isEmpty(message.attachmentMime) ? "image/jpeg" : message.attachmentMime;
      ArrayNode parts = MAPPER.createArrayNode();
      if (!text.isEmpty()) {
        parts.addObject().put("type", "text").put("text", text);
      }
      ObjectNode image = parts.addObject();
      image.put("type", "image_url");
      image.putObject("image_url").put("url", "data:" + mime + ";base64," + base64);
      return parts;
    }

    // audio
    if (!TextUtils.isEmpty(transcribeModel) || message.audioTranscribed) {
      if (!message.audioTranscribed) {
        String transcript =
            transcribe(
                baseUrl,
                apiKey,
                transcribeModel,
                file,
                message.attachmentMime,
                message.attachmentName);
        message.content = text.isEmpty() ? transcript : text + "\n" + transcript;
        message.audioTranscribed = true;
        text = message.content;
      }
      return MAPPER.getNodeFactory().textNode(text);
    }

    String format = MuhanMediaUtil.inlineAudioFormat(message.attachmentMime, message.attachmentName);
    if (format == null) {
      throw new IllegalArgumentException("unsupported audio format for inline sending");
    }
    String base64 = MuhanMediaUtil.base64(file);
    if (base64 == null) {
      throw new IOException("cannot read the audio attachment");
    }
    ArrayNode parts = MAPPER.createArrayNode();
    if (!text.isEmpty()) {
      parts.addObject().put("type", "text").put("text", text);
    }
    ObjectNode audio = parts.addObject();
    audio.put("type", "input_audio");
    ObjectNode inputAudio = audio.putObject("input_audio");
    inputAudio.put("data", base64);
    inputAudio.put("format", format);
    return parts;
  }

  /**
   * Sends the audio file to {@code {baseUrl}/audio/transcriptions} and returns the recognised text.
   */
  public static String transcribe(
      @NonNull String baseUrl,
      @NonNull String apiKey,
      @NonNull String model,
      @NonNull File file,
      @Nullable String mime,
      @Nullable String name)
      throws Exception {
    if (TextUtils.isEmpty(model)) {
      throw new IllegalArgumentException("transcription model is not configured");
    }
    String boundary = "----MuhanAiBoundary" + System.nanoTime();
    String fileName = TextUtils.isEmpty(name) ? file.getName() : name;
    String contentType = TextUtils.isEmpty(mime) ? "application/octet-stream" : mime;

    ByteArrayOutputStream body = new ByteArrayOutputStream();
    writeFormField(body, boundary, "model", model);
    writeFormField(body, boundary, "response_format", "json");
    write(body, "--" + boundary + "\r\n");
    write(
        body,
        "Content-Disposition: form-data; name=\"file\"; filename=\""
            + fileName
            + "\"\r\n");
    write(body, "Content-Type: " + contentType + "\r\n\r\n");
    try (InputStream input = new java.io.FileInputStream(file)) {
      byte[] chunk = new byte[8192];
      int read;
      while ((read = input.read(chunk)) != -1) {
        body.write(chunk, 0, read);
      }
    }
    write(body, "\r\n--" + boundary + "--\r\n");

    HttpURLConnection connection =
        (HttpURLConnection) new URL(endpoint(baseUrl, "audio/transcriptions")).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(30_000);
    connection.setReadTimeout(120_000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    connection.setRequestProperty("Accept", "application/json");
    if (!TextUtils.isEmpty(apiKey)) {
      connection.setRequestProperty("Authorization", "Bearer " + apiKey);
    }
    try (OutputStream out = connection.getOutputStream()) {
      body.writeTo(out);
      out.flush();
    }

    int code = connection.getResponseCode();
    if (code < 200 || code >= 300) {
      String detail = readStream(connection.getErrorStream());
      throw new HttpException(code, TextUtils.isEmpty(detail) ? "HTTP " + code : detail);
    }
    JsonNode node = MAPPER.readTree(readStream(connection.getInputStream()));
    JsonNode text = node.path("text");
    return text.isMissingNode() || text.isNull() ? "" : text.asText().trim();
  }

  private static void writeFormField(ByteArrayOutputStream body, String boundary, String name, String value) {
    write(body, "--" + boundary + "\r\n");
    write(body, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
    write(body, value + "\r\n");
  }

  private static void write(ByteArrayOutputStream body, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    body.write(bytes, 0, bytes.length);
  }

  private static String endpoint(String baseUrl) {
    return endpoint(baseUrl, "chat/completions");
  }

  private static String endpoint(String baseUrl, String path) {
    String base = TextUtils.isEmpty(baseUrl) ? "" : baseUrl.trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.endsWith("/" + path)) {
      return base;
    }
    return base + "/" + path;
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