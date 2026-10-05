package org.thoughtcrime.securesms.calls;

import android.content.Context;
import android.os.CountDownTimer;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import org.thoughtcrime.securesms.R;

/**
 * Nova Chat: the anti-scam warning shown before a screen sharing call is started.
 *
 * <p>The confirm button counts down from {@link #COUNTDOWN_SECONDS} and only acts on a normal tap
 * once the countdown finished. Tapping the button {@link #RAPID_TAP_COUNT} times while the
 * countdown is still running skips the wait, so impatient users are not blocked.
 */
public final class ScreenShareWarningDialog {

  private static final int COUNTDOWN_SECONDS = 10;
  private static final int RAPID_TAP_COUNT = 3;

  private ScreenShareWarningDialog() {}

  public static void show(Context context, Runnable onConfirm) {
    int[] remaining = {COUNTDOWN_SECONDS};
    int[] tapsDuringCountdown = {0};
    CountDownTimer[] timer = {null};

    AlertDialog dialog =
        new Builder(context)
            .setTitle(R.string.screen_share_warning_title)
            .setIcon(R.drawable.ic_screen_share_warning)
            .setMessage(R.string.screen_share_warning_message)
            .setNegativeButton(R.string.screen_share_cancel, null)
            .setPositiveButton(confirmText(context, COUNTDOWN_SECONDS), (d, which) -> {})
            .create();

    dialog.setOnDismissListener(
        d -> {
          if (timer[0] != null) {
            timer[0].cancel();
          }
        });
    dialog.show();

    Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    positive.setOnClickListener(
        v -> {
          if (remaining[0] > 0) {
            tapsDuringCountdown[0]++;
            if (tapsDuringCountdown[0] >= RAPID_TAP_COUNT) {
              dialog.dismiss();
              onConfirm.run();
            }
            return;
          }
          dialog.dismiss();
          onConfirm.run();
        });

    timer[0] =
        new CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000L) {
          @Override
          public void onTick(long millisUntilFinished) {
            remaining[0] = (int) Math.ceil(millisUntilFinished / 1000.0);
            positive.setText(confirmText(context, remaining[0]));
          }

          @Override
          public void onFinish() {
            remaining[0] = 0;
            positive.setText(context.getString(R.string.screen_share_confirm));
          }
        };
    timer[0].start();
  }

  private static String confirmText(Context context, int seconds) {
    return context.getString(R.string.screen_share_confirm) + " (" + seconds + ")";
  }
}
