package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.Date;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ThemeUtil;

/**
 * Nova Chat: renders the "MuHan Intelligence" chat bubbles (user on the right, assistant on the
 * left), reusing the very same bubble drawables and theme colours as a regular conversation so the
 * page looks consistent with the rest of the app in light, dark and Monet (wallpaper) themes.
 *
 * <p>User messages may carry an image or a voice/audio attachment; those are shown inline with a
 * thumbnail resp. a small play button.
 */
public class MuhanAiChatAdapter extends RecyclerView.Adapter<MuhanAiChatAdapter.ViewHolder> {

  private static final int TYPE_RECEIVED = 0;
  private static final int TYPE_SENT = 1;

  private final Context context;
  private final List<MuhanAiMessage> messages;
  private final java.text.DateFormat timeFormat;

  private MediaPlayer player;
  private ImageButton playingButton;

  public MuhanAiChatAdapter(@NonNull Context context, @NonNull List<MuhanAiMessage> messages) {
    this.context = context;
    this.messages = messages;
    this.timeFormat = DateFormat.getTimeFormat(context);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final LinearLayout bubble;
    final ImageView image;
    final LinearLayout audioRow;
    final ImageButton audioPlay;
    final TextView audioDuration;
    final TextView text;
    final TextView time;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      bubble = itemView.findViewById(R.id.muhan_ai_bubble);
      image = itemView.findViewById(R.id.muhan_ai_message_image);
      audioRow = itemView.findViewById(R.id.muhan_ai_message_audio);
      audioPlay = itemView.findViewById(R.id.muhan_ai_message_audio_play);
      audioDuration = itemView.findViewById(R.id.muhan_ai_message_audio_duration);
      text = itemView.findViewById(R.id.muhan_ai_message_text);
      time = itemView.findViewById(R.id.muhan_ai_message_time);
    }
  }

  @Override
  public int getItemViewType(int position) {
    return messages.get(position).isUser() ? TYPE_SENT : TYPE_RECEIVED;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    int layout =
        viewType == TYPE_SENT
            ? R.layout.muhan_ai_message_sent
            : R.layout.muhan_ai_message_received;
    View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    MuhanAiMessage message = messages.get(position);
    boolean user = message.isUser();

    holder.text.setText(message.content);
    holder.text.setTextColor(
        ThemeUtil.getThemedColor(
            context,
            user
                ? R.attr.conversation_item_outgoing_text_primary_color
                : R.attr.conversation_item_incoming_text_primary_color));
    holder.text.setVisibility(TextUtils.isEmpty(message.content) ? View.GONE : View.VISIBLE);

    bindImage(holder, message);
    bindAudio(holder, message);

    // Same trick as ConversationItem: the bubble drawable is plain white and gets multiplied by
    // the themed bubble colour, which keeps the shape (asymmetric corner radius) of real bubbles.
    holder.bubble.setBackgroundResource(
        user
            ? R.drawable.message_bubble_background_sent_alone
            : R.drawable.message_bubble_background_received_alone);
    holder.bubble
        .getBackground()
        .setColorFilter(
            ThemeUtil.getThemedColor(
                context,
                user
                    ? R.attr.conversation_item_outgoing_bubble_color
                    : R.attr.conversation_item_incoming_bubble_color),
            PorterDuff.Mode.MULTIPLY);

    holder.time.setTextColor(
        ThemeUtil.getThemedColor(
            context,
            user
                ? R.attr.conversation_item_outgoing_text_secondary_color
                : R.attr.conversation_item_incoming_text_secondary_color));
    holder.time.setText(
        message.timestamp > 0 ? timeFormat.format(new Date(message.timestamp)) : "");
    holder.time.setVisibility(TextUtils.isEmpty(holder.time.getText()) ? View.GONE : View.VISIBLE);
  }

  private void bindImage(@NonNull ViewHolder holder, @NonNull MuhanAiMessage message) {
    if (holder.image == null) {
      return;
    }
    if (!message.hasImage()) {
      holder.image.setVisibility(View.GONE);
      GlideApp.with(context).clear(holder.image);
      return;
    }
    holder.image.setVisibility(View.VISIBLE);
    GlideApp.with(context).load(new File(message.attachmentPath)).into(holder.image);
  }

  private void bindAudio(@NonNull ViewHolder holder, @NonNull MuhanAiMessage message) {
    if (holder.audioRow == null) {
      return;
    }
    if (!message.hasAudio()) {
      holder.audioRow.setVisibility(View.GONE);
      return;
    }
    holder.audioRow.setVisibility(View.VISIBLE);
    holder.audioDuration.setText(
        message.attachmentDuration > 0
            ? MuhanMediaUtil.formatDuration(message.attachmentDuration)
            : MuhanMediaUtil.formatDuration(0));
    holder.audioDuration.setTextColor(
        ThemeUtil.getThemedColor(context, R.attr.conversation_item_outgoing_text_primary_color));
    boolean isPlaying = player != null && playingButton == holder.audioPlay;
    holder.audioPlay.setImageResource(isPlaying ? R.drawable.pause_icon : R.drawable.play_icon);
    holder.audioPlay.setContentDescription(
        context.getString(
            isPlaying ? R.string.muhan_ai_audio_pause : R.string.muhan_ai_audio_play));
    File file = new File(message.attachmentPath);
    holder.audioPlay.setOnClickListener(v -> togglePlayback(holder.audioPlay, file));
  }

  private void togglePlayback(@NonNull ImageButton button, @NonNull File file) {
    if (player != null && playingButton == button) {
      stopPlayback();
      return;
    }
    stopPlayback();
    try {
      MediaPlayer mediaPlayer = new MediaPlayer();
      mediaPlayer.setDataSource(file.getAbsolutePath());
      mediaPlayer.prepare();
      mediaPlayer.setOnCompletionListener(m -> stopPlayback());
      mediaPlayer.setOnErrorListener(
          (m, what, extra) -> {
            stopPlayback();
            return true;
          });
      mediaPlayer.start();
      player = mediaPlayer;
      playingButton = button;
      button.setImageResource(R.drawable.pause_icon);
      button.setContentDescription(context.getString(R.string.muhan_ai_audio_pause));
    } catch (Exception e) {
      stopPlayback();
    }
  }

  /** Stops and releases the currently playing voice message, if any. */
  public void stopPlayback() {
    if (player != null) {
      try {
        player.stop();
      } catch (Exception ignored) {
      }
      try {
        player.release();
      } catch (Exception ignored) {
      }
      player = null;
    }
    if (playingButton != null) {
      playingButton.setImageResource(R.drawable.play_icon);
      playingButton.setContentDescription(context.getString(R.string.muhan_ai_audio_play));
      playingButton = null;
    }
  }

  @Override
  public void onViewRecycled(@NonNull ViewHolder holder) {
    super.onViewRecycled(holder);
    if (holder.audioPlay != null && holder.audioPlay == playingButton) {
      stopPlayback();
    }
  }

  @Override
  public int getItemCount() {
    return messages.size();
  }
}