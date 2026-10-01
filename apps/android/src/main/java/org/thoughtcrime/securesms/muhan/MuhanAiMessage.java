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

  /** No attachment. */
  public static final String ATTACHMENT_NONE = "";

  /** A still image, handed to the model as an {@code image_url} content part. */
  public static final String ATTACHMENT_IMAGE = "image";

  /** A voice message or an audio file, handed to the model inline or transcribed. */
  public static final String ATTACHMENT_AUDIO = "audio";

  public String role = ROLE_USER;
  public String content = "";
  public long timestamp = 0;

  /**
   * Attachment kind, one of {@link #ATTACHMENT_NONE}, {@link #ATTACHMENT_IMAGE} or {@link
   * #ATTACHMENT_AUDIO}. Empty for plain text messages.
   */
  public String attachmentType = ATTACHMENT_NONE;

  /** Absolute path of the attachment inside the app-private storage; empty when there is none. */
  public String attachmentPath = "";

  /** MIME type of the attachment, e.g. {@code image/jpeg} or {@code audio/wav}. */
  public String attachmentMime = "";

  /** Original file name of the attachment, used as a label in the bubble. */
  public String attachmentName = "";

  /** Duration of an audio attachment in milliseconds, {@code 0} when unknown. */
  public long attachmentDuration = 0;

  /** Set once an audio attachment has been transcribed, so the transcript is not requested twice. */
  public boolean audioTranscribed = false;

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

  public boolean hasAttachment() {
    return !ATTACHMENT_NONE.equals(attachmentType) && !attachmentPath.isEmpty();
  }

  public boolean hasImage() {
    return ATTACHMENT_IMAGE.equals(attachmentType) && !attachmentPath.isEmpty();
  }

  public boolean hasAudio() {
    return ATTACHMENT_AUDIO.equals(attachmentType) && !attachmentPath.isEmpty();
  }
}