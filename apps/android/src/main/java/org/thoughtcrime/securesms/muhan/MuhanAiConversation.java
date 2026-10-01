package org.thoughtcrime.securesms.muhan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Nova Chat: one "MuHan Intelligence" conversation.
 *
 * <p>Public fields keep it directly (de)serializable by Jackson, see {@link MuhanAiStore}; the
 * ProGuard rules keep the class intact in minified builds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuhanAiConversation {

  public long id = 0;
  public long createdAt = 0;
  public List<MuhanAiMessage> messages = new ArrayList<>();

  /** Jackson needs a no-arg constructor. */
  public MuhanAiConversation() {}

  public MuhanAiConversation(long id) {
    this.id = id;
    this.createdAt = System.currentTimeMillis();
  }

  /** Never {@code null}, so callers can rely on the list even after a partial deserialization. */
  public List<MuhanAiMessage> messages() {
    if (messages == null) {
      messages = new ArrayList<>();
    }
    return messages;
  }
}