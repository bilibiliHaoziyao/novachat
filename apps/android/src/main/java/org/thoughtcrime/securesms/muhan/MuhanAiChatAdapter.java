package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import org.thoughtcrime.securesms.R;

/**
 * Nova Chat: renders the "MuHan Intelligence" chat bubbles (user on the right, assistant on the
 * left). Bubble colours and text colours are resolved from the current app theme, so light/dark and
 * Monet (wallpaper) theming are honoured.
 */
public class MuhanAiChatAdapter extends RecyclerView.Adapter<MuhanAiChatAdapter.ViewHolder> {

  private final Context context;
  private final List<MuhanAiMessage> messages;

  public MuhanAiChatAdapter(@NonNull Context context, @NonNull List<MuhanAiMessage> messages) {
    this.context = context;
    this.messages = messages;
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final TextView text;

    ViewHolder(@NonNull View itemView) {
      super(itemView);
      text = itemView.findViewById(R.id.muhan_ai_message_text);
    }
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.muhan_ai_message_item, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    MuhanAiMessage message = messages.get(position);
    boolean user = message.isUser();

    LinearLayout container = (LinearLayout) holder.itemView;
    container.setGravity(user ? Gravity.END : Gravity.START);

    holder.text.setText(message.content);
    holder.text.setTextColor(
        resolveColor(
            context,
            user
                ? R.attr.conversation_item_outgoing_text_primary_color
                : R.attr.conversation_item_incoming_text_primary_color,
            user ? R.color.black : R.color.black));
    holder.text.setBackground(
        bubble(
            resolveColor(
                context,
                user
                    ? R.attr.conversation_item_outgoing_bubble_color
                    : R.attr.conversation_item_incoming_bubble_color,
                R.color.white)));
  }

  @Override
  public int getItemCount() {
    return messages.size();
  }

  private GradientDrawable bubble(@ColorInt int color) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.RECTANGLE);
    float radius = context.getResources().getDimension(R.dimen.message_corner_radius);
    drawable.setCornerRadius(radius);
    drawable.setColor(color);
    return drawable;
  }

  @ColorInt
  private static int resolveColor(@NonNull Context context, int attr, int fallbackRes) {
    TypedValue value = new TypedValue();
    if (context.getTheme().resolveAttribute(attr, value, true)) {
      if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
          && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        return value.data;
      }
      if (value.resourceId != 0) {
        return ContextCompat.getColor(context, value.resourceId);
      }
    }
    return ContextCompat.getColor(context, fallbackRes);
  }
}