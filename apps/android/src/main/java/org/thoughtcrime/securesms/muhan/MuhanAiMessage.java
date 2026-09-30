package org.thoughtcrime.securesms.muhan;

import androidx.annotation.NonNull;

/**
 * Nova Chat: a single message of a "MuHan Intelligence" conversation.
 *
 * <p>Public fields keep it directly (de)serializable by Jackson, see {@link MuhanAiStore}.
 */
public class MuhanAiMessage {

  public static final String ROLE_SYSTEM = "system";
  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";

  public String role = ROLE_USER;
  public String content = "";
  public long timestamp = 0;

  /** Jackson needs a no-arg constructor. */
  public MuhanAiMessage() {}

  public MuhanAiMessage(@NonNull String role, @NonNull String content) {
    this.role = role;
    this.content = content;
    this.timestamp = System.currentTimeMillis();
  }

  /** Kept package-private so that Jackson does not treat it as a serialised {@code user} property. */
  boolean isUser() {
    return ROLE_USER.equals(role);
  }
}