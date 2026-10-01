package org.thoughtcrime.securesms.muhan;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Nova Chat: helpers around the image/audio attachments of a "MuHan Intelligence" conversation.
 *
 * <p>Picked files are copied into the app-private storage so that the conversation history stays
 * valid after the picker's temporary grant has expired, and so that the bytes can be read back
 * later when the request is built.
 */
public final class MuhanMediaUtil {

  private static final String TAG = "MuhanMediaUtil";

  private MuhanMediaUtil() {}

  /** Directory holding the attachments of every account. */
  public static File mediaDir(@NonNull Context context) {
    File dir = new File(new File(context.getFilesDir(), "muhan-ai"), "media");
    if (!dir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
    }
    return dir;
  }

  /**
   * Copies the content behind {@code uri} into the app-private media directory.
   *
   * @return the newly created file, or {@code null} when it could not be copied.
   */
  @Nullable
  public static File importToMedia(@NonNull Context context, @NonNull Uri uri) {
    ContentResolver resolver = context.getContentResolver();
    String name = displayName(resolver, uri);
    String extension = extensionOf(name);
    if (TextUtils.isEmpty(extension)) {
      extension = extensionForMime(resolveMimeType(context, uri));
    }
    if (TextUtils.isEmpty(extension)) {
      extension = "bin";
    }

    File target = new File(mediaDir(context), UUID.randomUUID() + "." + extension);
    try (InputStream input = resolver.openInputStream(uri);
        FileOutputStream output = new FileOutputStream(target)) {
      if (input == null) {
        //noinspection ResultOfMethodCallIgnored
        target.delete();
        return null;
      }
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      output.flush();
      return target;
    } catch (Exception e) {
      Log.w(TAG, "Cannot import attachment: " + e.getMessage());
      //noinspection ResultOfMethodCallIgnored
      target.delete();
      return null;
    }
  }

  /** MIME type of the given content URI, falling back to the file extension. */
  @NonNull
  public static String resolveMimeType(@NonNull Context context, @NonNull Uri uri) {
    String type = null;
    try {
      type = context.getContentResolver().getType(uri);
    } catch (Exception ignored) {
    }
    if (TextUtils.isEmpty(type)) {
      type = mimeForFile(uri.getLastPathSegment());
    }
    if (TextUtils.isEmpty(type)) {
      type = mimeForFile(displayName(context.getContentResolver(), uri));
    }
    return type == null ? "" : type;
  }

  /** MIME type guessed from a file name / path. */
  @NonNull
  public static String mimeForFile(@Nullable String name) {
    String extension = extensionOf(name);
    if (TextUtils.isEmpty(extension)) {
      return "";
    }
    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    return mime == null ? "" : mime;
  }

  /** Best-effort display name of a content URI. */
  @NonNull
  public static String displayName(@NonNull ContentResolver resolver, @NonNull Uri uri) {
    String name = null;
    try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) {
          name = cursor.getString(index);
        }
      }
    } catch (Exception ignored) {
    }
    if (TextUtils.isEmpty(name)) {
      name = uri.getLastPathSegment();
    }
    return name == null ? "" : name;
  }

  /** Reads a file and returns its Base64 representation, or {@code null} on failure. */
  @Nullable
  public static String base64(@NonNull File file) {
    try (InputStream input = new java.io.FileInputStream(file)) {
      java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = input.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
    } catch (Exception e) {
      Log.w(TAG, "Cannot read attachment: " + e.getMessage());
      return null;
    }
  }

  /**
   * The OpenAI {@code input_audio} format token for a file, or {@code null} when the format cannot
   * be sent inline (only {@code wav} and {@code mp3} are accepted by the API).
   */
  @Nullable
  public static String inlineAudioFormat(@Nullable String mime, @Nullable String name) {
    String extension = extensionOf(name);
    if (TextUtils.isEmpty(extension) && !TextUtils.isEmpty(mime)) {
      extension = extensionForMime(mime);
    }
    if (extension == null) {
      return null;
    }
    extension = extension.toLowerCase(Locale.US);
    if ("wav".equals(extension) || "wave".equals(extension)) {
      return "wav";
    }
    if ("mp3".equals(extension) || "mpga".equals(extension) || "mpeg".equals(extension)) {
      return "mp3";
    }
    return null;
  }

  /** Extension for a MIME type, without the leading dot. */
  @NonNull
  public static String extensionForMime(@Nullable String mime) {
    if (TextUtils.isEmpty(mime)) {
      return "";
    }
    String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
    if (!TextUtils.isEmpty(extension)) {
      return extension;
    }
    if (mime.startsWith("audio/")) {
      return mime.substring("audio/".length());
    }
    return "";
  }

  /** Extension for a file name / path, without the leading dot. */
  @NonNull
  public static String extensionOf(@Nullable String name) {
    if (TextUtils.isEmpty(name)) {
      return "";
    }
    int dot = name.lastIndexOf('.');
    int slash = name.lastIndexOf('/');
    if (dot <= slash || dot == name.length() - 1) {
      return "";
    }
    return name.substring(dot + 1);
  }

  /** Formats a duration as {@code m:ss}. */
  @NonNull
  public static String formatDuration(long millis) {
    long totalSeconds = Math.max(0, millis / 1000);
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
  }
}