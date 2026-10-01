package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Date;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ThemeUtil;

/**
 * Nova Chat: renders the "MuHan Intelligence" chat bubbles (user on the right, assistant on the
 * left), reusing the very same bubble drawables and theme colours as a regular conversation so the
 * page looks consistent with the rest of the app in light, dark and Monet (wallpaper) themes.
 */
public class MuhanAiChatAdapter extends RecyclerView.Adapter<MuhanAiChatAdapter.ViewHolder> {

  private static final int TYPE_RECEIVED = 0;
  private static final int TYPE_SENT = 1;

  private final Context context;
  private final List<MuhanAiMessage> messages;
  private final java.text.DateFormat timeFormat;

  public MuhanAiChatAdapter(@NonNull Context context, @NonNull List<MuhanAiMessage> messages) {
    this.context = context;
    this.messages = messages;
    this.timeFormat = DateFormat.getTimeFormat(context);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final LinearLayout bubble;
    final TextView text;
    final TextView time;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      bubble = itemView.findViewById(R.id.muhan_ai_bubble);
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

  @Override
  public int getItemCount() {
    return messages.size();
  }
}