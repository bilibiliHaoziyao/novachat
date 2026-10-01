package org.thoughtcrime.securesms;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.thoughtcrime.securesms.components.ScaleStableImageView;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.muhan.MuhanAiArchive;
import org.thoughtcrime.securesms.muhan.MuhanAiChatAdapter;
import org.thoughtcrime.securesms.muhan.MuhanAiClient;
import org.thoughtcrime.securesms.muhan.MuhanAiConfig;
import org.thoughtcrime.securesms.muhan.MuhanAiConversation;
import org.thoughtcrime.securesms.muhan.MuhanAiMessage;
import org.thoughtcrime.securesms.muhan.MuhanAiStore;
import org.thoughtcrime.securesms.muhan.MuhanMediaUtil;
import org.thoughtcrime.securesms.muhan.MuhanVoiceRecorder;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.ChatBackground;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Nova Chat: the "MuHan Intelligence" (慕寒智能) chat screen.
 *
 * <p>A self-contained assistant chat that talks to any OpenAI-compatible endpoint configured in the
 * settings. Messages are stored locally per account and never leave the device except to the
 * user-configured API. Besides plain text, images and voice messages / audio files can be sent,
 * either inline (multimodal) or - for audio - as a transcription, depending on the settings.
 */
public class MuhanIntelligenceActivity extends BaseActionBarActivity {

  private static final int MENU_ATTACH_IMAGE = 1;
  private static final int MENU_ATTACH_AUDIO = 2;
  private static final int MENU_ATTACH_RECORD = 3;

  private RecyclerView listView;
  private EditText inputView;
  private ImageButton sendButton;
  private ImageButton attachButton;

  private View attachmentPreview;
  private ImageView attachmentThumb;
  private TextView attachmentName;

  private View inputBar;
  private View recordingBar;
  private TextView recordingTime;

  private MuhanAiChatAdapter adapter;

  // Live list of the messages of the conversation currently shown; it backs the adapter. Every
  // conversation owns its own list, {@link #persist} snapshots this one into the current
  // conversation so that switching away keeps the previous conversation intact.
  private final List<MuhanAiMessage> messages = new ArrayList<>();
  private MuhanAiArchive archive;
  private MuhanAiConversation conversation;
  private int accountId;
  private MuhanAiClient.Request request;
  private boolean sending;

  // Attachment that will be sent with the next message.
  private String pendingType = MuhanAiMessage.ATTACHMENT_NONE;
  private String pendingPath = "";
  private String pendingMime = "";
  private String pendingName = "";
  private long pendingDuration = 0;

  private MuhanVoiceRecorder recorder;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Runnable recordingTick =
      new Runnable() {
        @Override
        public void run() {
          if (recorder != null && recorder.isRecording()) {
            recordingTime.setText(
                getString(R.string.muhan_ai_record_title)
                    + " "
                    + MuhanMediaUtil.formatDuration(System.currentTimeMillis() - recordingStartedAt));
            mainHandler.postDelayed(this, 500);
          }
        }
      };
  private long recordingStartedAt;

  private final ExecutorService mediaExecutor = Executors.newSingleThreadExecutor();

  private ActivityResultLauncher<Intent> imagePicker;
  private ActivityResultLauncher<Intent> audioPicker;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    imagePicker =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                onPickedAttachment(result.getData().getData(), MuhanAiMessage.ATTACHMENT_IMAGE);
              }
            });
    audioPicker =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                onPickedAttachment(result.getData().getData(), MuhanAiMessage.ATTACHMENT_AUDIO);
              }
            });

    setContentView(R.layout.activity_muhan_intelligence);

    // Nova Chat: the activity is edge-to-edge, so keep the input bar clear of the gesture
    // navigation bar ("pill") and of any display cutout.
    ViewUtil.applyWindowInsets(findViewById(R.id.root_layout), true, false, true, true);

    accountId = DcHelper.getContext(this).getAccountId();

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.muhan_ai_title);
    }

    listView = findViewById(R.id.muhan_ai_list);
    inputView = findViewById(R.id.muhan_ai_input);
    sendButton = findViewById(R.id.muhan_ai_send);
    attachButton = findViewById(R.id.muhan_ai_attach);
    attachmentPreview = findViewById(R.id.muhan_ai_attachment_preview);
    attachmentThumb = findViewById(R.id.muhan_ai_attachment_thumb);
    attachmentName = findViewById(R.id.muhan_ai_attachment_name);
    inputBar = findViewById(R.id.muhan_ai_input_bar);
    recordingBar = findViewById(R.id.muhan_ai_recording_bar);
    recordingTime = findViewById(R.id.muhan_ai_recording_time);

    // same chat background as a regular conversation (solid colour / Monet / user picked image)
    ((ScaleStableImageView) findViewById(R.id.muhan_ai_background))
        .setImageDrawable(ChatBackground.getDrawable(this));

    adapter = new MuhanAiChatAdapter(this, messages);
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    layoutManager.setStackFromEnd(true);
    listView.setLayoutManager(layoutManager);
    listView.setAdapter(adapter);

    archive = MuhanAiStore.load(this, accountId);
    conversation = archive.current();
    messages.addAll(conversation.messages());
    ensureGreeting();
    adapter.notifyDataSetChanged();
    scrollToBottom();

    sendButton.setOnClickListener(v -> sendMessage());
    attachButton.setOnClickListener(this::showAttachMenu);
    findViewById(R.id.muhan_ai_attachment_remove).setOnClickListener(v -> clearPendingAttachment());
    findViewById(R.id.muhan_ai_recording_cancel).setOnClickListener(v -> cancelRecording());
    findViewById(R.id.muhan_ai_recording_stop).setOnClickListener(v -> stopRecordingAndSend());
  }

  private void ensureGreeting() {
    if (messages.isEmpty()) {
      messages.add(
          new MuhanAiMessage(
              MuhanAiMessage.ROLE_ASSISTANT, getString(R.string.muhan_ai_greeting)));
      persist();
    }
  }

  /** Writes the whole archive (every conversation plus the current one) to disk. */
  private void persist() {
    // snapshot the live list: the next conversation gets its own one
    conversation.messages = new ArrayList<>(messages);
    MuhanAiStore.save(this, accountId, archive);
  }

  // region attachments

  private void showAttachMenu(View anchor) {
    if (sending) {
      return;
    }
    PopupMenu popup = new PopupMenu(this, anchor);
    popup.getMenu().add(Menu.NONE, MENU_ATTACH_IMAGE, 1, R.string.muhan_ai_attach_image);
    popup.getMenu().add(Menu.NONE, MENU_ATTACH_AUDIO, 2, R.string.muhan_ai_attach_audio);
    popup.getMenu().add(Menu.NONE, MENU_ATTACH_RECORD, 3, R.string.muhan_ai_attach_record);
    popup.setOnMenuItemClickListener(
        item -> {
          switch (item.getItemId()) {
            case MENU_ATTACH_IMAGE:
              pickMedia("image/*", imagePicker);
              return true;
            case MENU_ATTACH_AUDIO:
              pickMedia("audio/*", audioPicker);
              return true;
            case MENU_ATTACH_RECORD:
              startRecordingWithPermission();
              return true;
            default:
              return false;
          }
        });
    popup.show();
  }

  private void pickMedia(@NonNull String mimeType, @NonNull ActivityResultLauncher<Intent> launcher) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType(mimeType);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    try {
      launcher.launch(intent);
    } catch (Exception e) {
      Toast.makeText(this, R.string.muhan_ai_attach_failed, Toast.LENGTH_SHORT).show();
    }
  }

  private void onPickedAttachment(@Nullable Uri uri, @NonNull String type) {
    if (uri == null) {
      return;
    }
    mediaExecutor.execute(
        () -> {
          File file = MuhanMediaUtil.importToMedia(this, uri);
          String mime = MuhanMediaUtil.resolveMimeType(this, uri);
          String name = MuhanMediaUtil.displayName(getContentResolver(), uri);
          if (TextUtils.isEmpty(mime) && file != null) {
            mime = MuhanMediaUtil.mimeForFile(file.getName());
          }
          final String resolvedMime = mime;
          final String resolvedName = name;
          runOnUiThread(
              () -> {
                if (file == null) {
                  Toast.makeText(this, R.string.muhan_ai_attach_failed, Toast.LENGTH_SHORT).show();
                  return;
                }
                setPendingAttachment(
                    type, file.getAbsolutePath(), resolvedMime, resolvedName, 0);
              });
        });
  }

  private void setPendingAttachment(
      @NonNull String type,
      @NonNull String path,
      @Nullable String mime,
      @Nullable String name,
      long duration) {
    deleteFileQuietly(pendingPath);
    pendingType = type;
    pendingPath = path;
    pendingMime = mime == null ? "" : mime;
    pendingName = TextUtils.isEmpty(name) ? new File(path).getName() : name;
    pendingDuration = duration;
    updateAttachmentPreview();
  }

  private void clearPendingAttachment() {
    deleteFileQuietly(pendingPath);
    resetPendingAttachment();
  }

  /** Forgets the pending attachment without deleting its file (used once it was sent). */
  private void resetPendingAttachment() {
    pendingType = MuhanAiMessage.ATTACHMENT_NONE;
    pendingPath = "";
    pendingMime = "";
    pendingName = "";
    pendingDuration = 0;
    updateAttachmentPreview();
  }

  private void updateAttachmentPreview() {
    if (MuhanAiMessage.ATTACHMENT_NONE.equals(pendingType)) {
      attachmentPreview.setVisibility(View.GONE);
      attachmentThumb.setImageDrawable(null);
      return;
    }
    attachmentPreview.setVisibility(View.VISIBLE);
    if (MuhanAiMessage.ATTACHMENT_IMAGE.equals(pendingType)) {
      attachmentThumb.setVisibility(View.VISIBLE);
      GlideApp.with(this).load(new File(pendingPath)).into(attachmentThumb);
      attachmentName.setText(pendingName);
    } else {
      attachmentThumb.setVisibility(View.GONE);
      attachmentThumb.setImageDrawable(null);
      String label =
          pendingDuration > 0
              ? MuhanMediaUtil.formatDuration(pendingDuration) + " · " + pendingName
              : pendingName;
      attachmentName.setText(label);
    }
  }

  private static void deleteFileQuietly(@Nullable String path) {
    if (TextUtils.isEmpty(path)) {
      return;
    }
    File file = new File(path);
    if (file.exists()) {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
    }
  }

  // endregion

  // region voice recording

  private void startRecordingWithPermission() {
    Permissions.with(this)
        .request(Manifest.permission.RECORD_AUDIO)
        .ifNecessary()
        .withPermanentDenialDialog(getString(R.string.muhan_ai_recording_failed))
        .onAllGranted(this::startRecording)
        .onAnyDenied(
            () ->
                Toast.makeText(this, R.string.muhan_ai_recording_failed, Toast.LENGTH_SHORT).show())
        .execute();
  }

  private void startRecording() {
    if (recorder != null) {
      return;
    }
    // the input bar is hidden while recording, so get the keyboard out of the way
    inputView.clearFocus();
    android.view.inputmethod.InputMethodManager imm =
        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
    }
    recorder =
        new MuhanVoiceRecorder(
            new MuhanVoiceRecorder.Listener() {
              @Override
              public void onFinished(@NonNull File file, long durationMs) {
                runOnUiThread(
                    () -> {
                      recorder = null;
                      showRecordingBar(false);
                      setPendingAttachment(
                          MuhanAiMessage.ATTACHMENT_AUDIO,
                          file.getAbsolutePath(),
                          "audio/wav",
                          file.getName(),
                          durationMs);
                      sendMessage();
                    });
              }

              @Override
              public void onError(@NonNull Exception error) {
                runOnUiThread(
                    () -> {
                      recorder = null;
                      showRecordingBar(false);
                      Toast.makeText(
                              MuhanIntelligenceActivity.this,
                              R.string.muhan_ai_recording_failed,
                              Toast.LENGTH_SHORT)
                          .show();
                    });
              }
            });
    try {
      recorder.start(this);
    } catch (Exception e) {
      recorder = null;
      Toast.makeText(this, R.string.muhan_ai_recording_failed, Toast.LENGTH_SHORT).show();
      return;
    }
    recordingStartedAt = System.currentTimeMillis();
    showRecordingBar(true);
    mainHandler.post(recordingTick);
  }

  private void stopRecordingAndSend() {
    if (recorder != null) {
      recorder.stop();
    }
  }

  private void cancelRecording() {
    if (recorder != null) {
      recorder.cancel();
      recorder = null;
    }
    showRecordingBar(false);
    Toast.makeText(this, R.string.muhan_ai_canceled_recording, Toast.LENGTH_SHORT).show();
  }

  private void showRecordingBar(boolean show) {
    mainHandler.removeCallbacks(recordingTick);
    recordingBar.setVisibility(show ? View.VISIBLE : View.GONE);
    inputBar.setVisibility(show ? View.GONE : View.VISIBLE);
    attachmentPreview.setVisibility(
        !show && !MuhanAiMessage.ATTACHMENT_NONE.equals(pendingType) ? View.VISIBLE : View.GONE);
    if (show) {
      recordingTime.setText(R.string.muhan_ai_record_title);
    }
  }

  // endregion

  private void sendMessage() {
    String text = inputView.getText().toString().trim();
    boolean hasAttachment = !MuhanAiMessage.ATTACHMENT_NONE.equals(pendingType);
    if ((TextUtils.isEmpty(text) && !hasAttachment) || sending) {
      return;
    }
    if (!MuhanAiConfig.isConfigured(this)) {
      showNotConfiguredDialog();
      return;
    }
    if (!MuhanAiConfig.canSend(this)) {
      showQuotaDialog();
      return;
    }

    boolean transcribeAudio = MuhanAiConfig.isTranscribeMode(this);
    boolean audioAttachment = hasAttachment && MuhanAiMessage.ATTACHMENT_AUDIO.equals(pendingType);
    if (audioAttachment
        && !transcribeAudio
        && MuhanMediaUtil.inlineAudioFormat(pendingMime, pendingName) == null) {
      showAudioFormatHint();
      return;
    }
    // the built-in endpoint is rationed, book the message against today's budget
    MuhanAiConfig.recordMessageSent(this);

    MuhanAiMessage userMessage = new MuhanAiMessage(MuhanAiMessage.ROLE_USER, text);
    if (hasAttachment) {
      userMessage.attachmentType = pendingType;
      userMessage.attachmentPath = pendingPath;
      userMessage.attachmentMime = pendingMime;
      userMessage.attachmentName = pendingName;
      userMessage.attachmentDuration = pendingDuration;
    }
    final MuhanAiMessage assistantMessage = new MuhanAiMessage(MuhanAiMessage.ROLE_ASSISTANT, "");
    messages.add(userMessage);
    messages.add(assistantMessage);
    inputView.setText("");
    resetPendingAttachment();
    if (audioAttachment && transcribeAudio) {
      assistantMessage.content = getString(R.string.muhan_ai_transcribing);
    }
    adapter.notifyDataSetChanged();
    scrollToBottom();
    persist();

    List<MuhanAiMessage> requestMessages = new ArrayList<>();
    requestMessages.add(
        new MuhanAiMessage(
            MuhanAiMessage.ROLE_SYSTEM, MuhanAiConfig.getEffectiveSystemPrompt(this)));
    for (int i = 0; i < messages.size() - 1; i++) {
      requestMessages.add(messages.get(i));
    }

    sending = true;
    updateSendState(true);
    final StringBuilder streamed = new StringBuilder();
    request =
        MuhanAiClient.send(
            MuhanAiConfig.getBaseUrl(this),
            MuhanAiConfig.getApiKey(this),
            MuhanAiConfig.getModel(this),
            requestMessages,
            true,
            transcribeAudio ? MuhanAiConfig.getTranscribeModel(this) : "",
            new MuhanAiClient.Callback() {
              @Override
              public void onDelta(String delta) {
                runOnUiThread(
                    () -> {
                      streamed.append(delta);
                      assistantMessage.content = streamed.toString();
                      adapter.notifyItemChanged(messages.size() - 1);
                      scrollToBottom();
                    });
              }

              @Override
              public void onSuccess(String fullText) {
                runOnUiThread(
                    () -> {
                      if (TextUtils.isEmpty(assistantMessage.content)
                          || assistantMessage.content.equals(
                              getString(R.string.muhan_ai_transcribing))) {
                        assistantMessage.content =
                            TextUtils.isEmpty(fullText)
                                ? getString(R.string.muhan_ai_empty_reply)
                                : fullText;
                      }
                      finishRequest();
                    });
              }

              @Override
              public void onError(Exception error) {
                runOnUiThread(
                    () -> {
                      assistantMessage.content =
                          getString(R.string.muhan_ai_error, String.valueOf(error.getMessage()));
                      finishRequest();
                    });
              }
            });
  }

  private void finishRequest() {
    sending = false;
    request = null;
    updateSendState(false);
    adapter.notifyItemChanged(messages.size() - 1);
    persist();
    scrollToBottom();
  }

  private void updateSendState(boolean busy) {
    sendButton.setEnabled(!busy);
    sendButton.setAlpha(busy ? 0.5f : 1f);
    sendButton.setContentDescription(
        getString(busy ? R.string.muhan_ai_sending : R.string.muhan_ai_send));
  }

  private void showAudioFormatHint() {
    new AlertDialog.Builder(this)
        .setMessage(R.string.muhan_ai_audio_format_hint)
        .setPositiveButton(R.string.muhan_ai_open_settings, (d, w) -> openSettings())
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showNotConfiguredDialog() {
    new AlertDialog.Builder(this)
        .setMessage(R.string.muhan_ai_not_configured)
        .setPositiveButton(R.string.muhan_ai_open_settings, (d, w) -> openSettings())
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void confirmClearHistory() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.muhan_ai_clear)
        .setMessage(R.string.muhan_ai_clear_confirm)
        .setPositiveButton(R.string.muhan_ai_clear, (d, w) -> clearHistory())
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void clearHistory() {
    abortRequest();
    messages.clear();
    MuhanAiStore.clear(this, accountId);
    archive = new MuhanAiArchive();
    conversation = archive.current();
    ensureGreeting();
    adapter.notifyDataSetChanged();
  }

  /** Drops an in-flight request and returns the composer to its idle state. */
  private void abortRequest() {
    if (request != null) {
      request.cancel();
      request = null;
    }
    sending = false;
    updateSendState(false);
  }

  private void startNewConversation() {
    abortRequest();
    persist();
    conversation = archive.createConversation();
    messages.clear();
    ensureGreeting();
    adapter.notifyDataSetChanged();
    scrollToBottom();
    persist();
  }

  private void switchConversation(@NonNull MuhanAiConversation target) {
    if (target == conversation) {
      return;
    }
    abortRequest();
    persist();
    conversation = target;
    archive.currentId = target.id;
    messages.clear();
    messages.addAll(target.messages());
    ensureGreeting();
    adapter.notifyDataSetChanged();
    scrollToBottom();
    persist();
  }

  /** Long-press target of the "new conversation" action: pick one of the existing conversations. */
  private void showConversationSwitcher() {
    final List<MuhanAiConversation> conversations = new ArrayList<>(archive.conversations());
    Collections.reverse(conversations);
    if (conversations.size() <= 1) {
      Toast.makeText(this, R.string.muhan_ai_switch_conversation_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    final String[] titles = new String[conversations.size()];
    for (int i = 0; i < conversations.size(); i++) {
      MuhanAiConversation item = conversations.get(i);
      titles[i] =
          conversationTitle(item)
              + (item.id == archive.currentId
                  ? " (" + getString(R.string.muhan_ai_conversation_current) + ")"
                  : "");
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.muhan_ai_switch_conversation)
        .setItems(titles, (dialog, which) -> switchConversation(conversations.get(which)))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  /** Label of a conversation in the switcher: its first user message, or a generic name. */
  private String conversationTitle(@NonNull MuhanAiConversation target) {
    for (MuhanAiMessage message : target.messages()) {
      if (MuhanAiMessage.ROLE_USER.equals(message.role) && !TextUtils.isEmpty(message.content)) {
        String text = message.content.replace('\n', ' ').trim();
        return text.length() > 24 ? text.substring(0, 24) + "…" : text;
      }
    }
    return getString(R.string.muhan_ai_conversation_new);
  }

  private void showQuotaDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.muhan_ai_quota_title)
        .setMessage(
            getString(R.string.muhan_ai_quota_limited, MuhanAiConfig.BUILTIN_DAILY_LIMIT))
        .setPositiveButton(R.string.muhan_ai_open_settings, (d, w) -> openSettings())
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void openSettings() {
    startActivity(new Intent(this, ApplicationPreferencesActivity.class));
  }

  private void scrollToBottom() {
    if (!messages.isEmpty()) {
      listView.scrollToPosition(messages.size() - 1);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(@NonNull Menu menu) {
    getMenuInflater().inflate(R.menu.menu_muhan_intelligence, menu);
    MenuItem newConversation = menu.findItem(R.id.menu_muhan_ai_new);
    if (newConversation != null) {
      // the action view is used instead of a plain item so that a long press can be observed
      View actionView = getLayoutInflater().inflate(R.layout.muhan_ai_action_new_conversation, null);
      actionView.setOnClickListener(v -> startNewConversation());
      actionView.setOnLongClickListener(
          v -> {
            showConversationSwitcher();
            return true;
          });
      newConversation.setActionView(actionView);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      finish();
      return true;
    } else if (id == R.id.menu_muhan_ai_new) {
      startNewConversation();
      return true;
    } else if (id == R.id.menu_muhan_ai_clear) {
      confirmClearHistory();
      return true;
    } else if (id == R.id.menu_muhan_ai_settings) {
      openSettings();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  protected void onDestroy() {
    // persist first, so a reply that was still streaming when the user left is not lost
    if (archive != null && conversation != null) {
      persist();
    }
    super.onDestroy();
    if (request != null) {
      request.cancel();
      request = null;
    }
    if (recorder != null) {
      recorder.cancel();
      recorder = null;
    }
    mainHandler.removeCallbacks(recordingTick);
    mediaExecutor.shutdownNow();
    if (adapter != null) {
      adapter.stopPlayback();
    }
  }
}