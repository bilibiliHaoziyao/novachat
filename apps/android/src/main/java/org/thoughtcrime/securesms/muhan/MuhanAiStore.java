package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Nova Chat: local persistence of the "MuHan Intelligence" conversations.
 *
 * <p>Everything is a plain JSON file in the app-private storage, kept separate per account so that
 * switching accounts does not leak one conversation into another. It never touches the mail core.
 *
 * <p>The archive holds every conversation of the account plus the id of the one shown last. Older
 * builds stored a bare list of messages in {@code history-<accountId>.json}; such a file is migrated
 * into a single conversation when it still contains readable messages.
 */
public final class MuhanAiStore {

  private static final String TAG = "MuhanAiStore";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MuhanAiStore() {}

  private static File storeDir(@NonNull Context context) {
    return new File(context.getFilesDir(), "muhan-ai");
  }

  private static File archiveFile(@NonNull Context context, int accountId) {
    return new File(storeDir(context), "archive-" + accountId + ".json");
  }

  private static File legacyFile(@NonNull Context context, int accountId) {
    return new File(storeDir(context), "history-" + accountId + ".json");
  }

  /** Loads the archive of the account, migrating the legacy single-conversation format if needed. */
  @NonNull
  public static MuhanAiArchive load(@NonNull Context context, int accountId) {
    File file = archiveFile(context, accountId);
    if (file.exists()) {
      try {
        MuhanAiArchive archive = MAPPER.readValue(file, MuhanAiArchive.class);
        if (archive != null && !archive.conversations().isEmpty()) {
          return archive;
        }
      } catch (Exception e) {
        Log.w(TAG, "Cannot read AI archive: " + e.getMessage());
      }
    }
    return migrateLegacy(context, accountId);
  }

  public static void save(
      @NonNull Context context, int accountId, @NonNull MuhanAiArchive archive) {
    File file = archiveFile(context, accountId);
    try {
      File dir = file.getParentFile();
      if (dir != null && !dir.exists()) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
      }
      MAPPER.writeValue(file, archive);
      // the legacy file has been superseded by the archive
      //noinspection ResultOfMethodCallIgnored
      legacyFile(context, accountId).delete();
    } catch (Exception e) {
      Log.w(TAG, "Cannot write AI archive: " + e.getMessage());
    }
  }

  public static void clear(@NonNull Context context, int accountId) {
    //noinspection ResultOfMethodCallIgnored
    archiveFile(context, accountId).delete();
    //noinspection ResultOfMethodCallIgnored
    legacyFile(context, accountId).delete();
  }

  /**
   * Converts a {@code history-<accountId>.json} written by an older build into an archive.
   *
   * <p>Messages without any content are dropped: files written by minified builds used obfuscated
   * field names and would otherwise deserialize into a series of empty bubbles.
   */
  @NonNull
  private static MuhanAiArchive migrateLegacy(@NonNull Context context, int accountId) {
    MuhanAiArchive archive = new MuhanAiArchive();
    File file = legacyFile(context, accountId);
    if (!file.exists()) {
      return archive;
    }
    try {
      List<MuhanAiMessage> messages =
          MAPPER.readValue(file, new TypeReference<List<MuhanAiMessage>>() {});
      List<MuhanAiMessage> readable = new ArrayList<>();
      if (messages != null) {
        for (MuhanAiMessage message : messages) {
          if (message != null && (!TextUtils.isEmpty(message.content) || message.hasAttachment())) {
            readable.add(message);
          }
        }
      }
      if (!readable.isEmpty()) {
        MuhanAiConversation conversation = new MuhanAiConversation(System.currentTimeMillis());
        conversation.messages = readable;
        archive.conversations().add(conversation);
        archive.currentId = conversation.id;
        Log.i(TAG, "Migrated " + readable.size() + " legacy AI messages");
      }
    } catch (Exception e) {
      Log.w(TAG, "Cannot read legacy AI history: " + e.getMessage());
    }
    return archive;
  }
}