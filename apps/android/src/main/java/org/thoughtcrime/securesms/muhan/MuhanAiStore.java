package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Nova Chat: local persistence of "MuHan Intelligence" conversations.
 *
 * <p>History is a plain JSON file in the app-private storage, kept separate per account so that
 * switching accounts does not leak one conversation into another. It never touches the mail core.
 */
public final class MuhanAiStore {

  private static final String TAG = "MuhanAiStore";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MuhanAiStore() {}

  private static File historyFile(@NonNull Context context, int accountId) {
    File dir = new File(context.getFilesDir(), "muhan-ai");
    return new File(dir, "history-" + accountId + ".json");
  }

  public static List<MuhanAiMessage> load(@NonNull Context context, int accountId) {
    File file = historyFile(context, accountId);
    if (!file.exists()) {
      return new ArrayList<>();
    }
    try {
      List<MuhanAiMessage> messages =
          MAPPER.readValue(file, new TypeReference<List<MuhanAiMessage>>() {});
      return messages != null ? messages : new ArrayList<>();
    } catch (Exception e) {
      Log.w(TAG, "Cannot read AI history: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  public static void save(
      @NonNull Context context, int accountId, @NonNull List<MuhanAiMessage> messages) {
    File file = historyFile(context, accountId);
    try {
      File dir = file.getParentFile();
      if (dir != null && !dir.exists()) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
      }
      MAPPER.writeValue(file, messages);
    } catch (Exception e) {
      Log.w(TAG, "Cannot write AI history: " + e.getMessage());
    }
  }

  public static void clear(@NonNull Context context, int accountId) {
    //noinspection ResultOfMethodCallIgnored
    historyFile(context, accountId).delete();
  }
}