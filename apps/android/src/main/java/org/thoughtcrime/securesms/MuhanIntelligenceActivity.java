package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.components.ScaleStableImageView;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.muhan.MuhanAiChatAdapter;
import org.thoughtcrime.securesms.muhan.MuhanAiClient;
import org.thoughtcrime.securesms.muhan.MuhanAiConfig;
import org.thoughtcrime.securesms.muhan.MuhanAiMessage;
import org.thoughtcrime.securesms.muhan.MuhanAiStore;
import org.thoughtcrime.securesms.util.ChatBackground;

/**
 * Nova Chat: the "MuHan Intelligence" (慕寒智能) chat screen.
 *
 * <p>A self-contained assistant chat that talks to any OpenAI-compatible endpoint configured in the
 * settings. Messages are stored locally per account and never leave the device except to the
 * user-configured API.
 */
public class MuhanIntelligenceActivity extends BaseActionBarActivity {

  private RecyclerView listView;
  private EditText inputView;
  private ImageButton sendButton;
  private MuhanAiChatAdapter adapter;

  private final List<MuhanAiMessage> messages = new ArrayList<>();
  private int accountId;
  private MuhanAiClient.Request request;
  private boolean sending;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_muhan_intelligence);

    accountId = DcHelper.getContext(this).getAccountId();

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.muhan_ai_title);
    }

    listView = findViewById(R.id.muhan_ai_list);
    inputView = findViewById(R.id.muhan_ai_input);
    sendButton = findViewById(R.id.muhan_ai_send);

    // same chat background as a regular conversation (solid colour / Monet / user picked image)
    ((ScaleStableImageView) findViewById(R.id.muhan_ai_background))
        .setImageDrawable(ChatBackground.getDrawable(this));

    adapter = new MuhanAiChatAdapter(this, messages);
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    layoutManager.setStackFromEnd(true);
    listView.setLayoutManager(layoutManager);
    listView.setAdapter(adapter);

    messages.addAll(MuhanAiStore.load(this, accountId));
    ensureGreeting();
    adapter.notifyDataSetChanged();
    scrollToBottom();

    sendButton.setOnClickListener(v -> sendMessage());
  }

  private void ensureGreeting() {
    if (messages.isEmpty()) {
      messages.add(
          new MuhanAiMessage(
              MuhanAiMessage.ROLE_ASSISTANT, getString(R.string.muhan_ai_greeting)));
      MuhanAiStore.save(this, accountId, messages);
    }
  }

  private void sendMessage() {
    String text = inputView.getText().toString().trim();
    if (TextUtils.isEmpty(text) || sending) {
      return;
    }
    if (!MuhanAiConfig.isConfigured(this)) {
      showNotConfiguredDialog();
      return;
    }

    MuhanAiMessage userMessage = new MuhanAiMessage(MuhanAiMessage.ROLE_USER, text);
    final MuhanAiMessage assistantMessage = new MuhanAiMessage(MuhanAiMessage.ROLE_ASSISTANT, "");
    messages.add(userMessage);
    messages.add(assistantMessage);
    inputView.setText("");
    adapter.notifyDataSetChanged();
    scrollToBottom();
    MuhanAiStore.save(this, accountId, messages);

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
                      if (TextUtils.isEmpty(assistantMessage.content)) {
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
    MuhanAiStore.save(this, accountId, messages);
    scrollToBottom();
  }

  private void updateSendState(boolean busy) {
    sendButton.setEnabled(!busy);
    sendButton.setAlpha(busy ? 0.5f : 1f);
    sendButton.setContentDescription(
        getString(busy ? R.string.muhan_ai_sending : R.string.muhan_ai_send));
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
    if (request != null) {
      request.cancel();
      request = null;
    }
    sending = false;
    updateSendState(false);
    messages.clear();
    MuhanAiStore.clear(this, accountId);
    ensureGreeting();
    adapter.notifyDataSetChanged();
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
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      finish();
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
  protected void onDestroy() {
    super.onDestroy();
    if (request != null) {
      request.cancel();
      request = null;
    }
  }
}