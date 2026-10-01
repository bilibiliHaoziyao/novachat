package org.thoughtcrime.securesms.muhan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Nova Chat: all "MuHan Intelligence" conversations of one account plus the one currently shown.
 *
 * <p>Public fields keep it directly (de)serializable by Jackson, see {@link MuhanAiStore}; the
 * ProGuard rules keep the class intact in minified builds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuhanAiArchive {

  public long currentId = 0;
  public List<MuhanAiConversation> conversations = new ArrayList<>();

  /** Jackson needs a no-arg constructor. */
  public MuhanAiArchive() {}

  /** Never {@code null}, so callers can rely on the list even after a partial deserialization. */
  public List<MuhanAiConversation> conversations() {
    if (conversations == null) {
      conversations = new ArrayList<>();
    }
    return conversations;
  }

  /** The conversation shown last, creating the very first one on demand. */
  public MuhanAiConversation current() {
    for (MuhanAiConversation conversation : conversations()) {
      if (conversation.id == currentId) {
        return conversation;
      }
    }
    if (conversations().isEmpty()) {
      conversations().add(new MuhanAiConversation(System.currentTimeMillis()));
    }
    MuhanAiConversation conversation = conversations().get(conversations().size() - 1);
    currentId = conversation.id;
    return conversation;
  }

  /** Appends a brand new conversation and makes it the current one. */
  public MuhanAiConversation createConversation() {
    long id = System.currentTimeMillis();
    for (MuhanAiConversation existing : conversations()) {
      if (existing.id >= id) {
        id = existing.id + 1;
      }
    }
    MuhanAiConversation conversation = new MuhanAiConversation(id);
    conversations().add(conversation);
    currentId = id;
    return conversation;
  }

  /** Whether the archive holds more than the currently shown conversation. */
  public boolean hasSeveralConversations() {
    return conversations().size() > 1;
  }
}