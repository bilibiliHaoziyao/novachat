package org.thoughtcrime.securesms.oobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Nova Chat: the first-run "out of the box experience".
 *
 * <p>A single activity hosts six full-screen steps — language, region, account, WebDAV, MuHan
 * Intelligence and a final welcome page — and provides the shared chrome: a back arrow, a progress
 * indicator and the bottom "skip"/"continue" buttons. The current step is remembered, so the tour
 * can be resumed after an account was created in one of the external flows.
 */
public class OobeActivity extends BaseActionBarActivity implements OobeHost {

  public static final String EXTRA_FROM_OOBE = "oobe_from";

  private static final int STEP_COUNT = 6;
  private static final int STEP_LANGUAGE = 0;
  private static final int STEP_REGION = 1;
  private static final int STEP_ACCOUNT = 2;
  private static final int STEP_WEBDAV = 3;
  private static final int STEP_MUHAN = 4;
  private static final int STEP_FINISH = 5;

  private static final String STATE_STEP = "oobe_activity_step";

  private ImageButton backButton;
  private Button primaryButton;
  private Button skipButton;
  private LinearLayout dotsContainer;
  private View[] dots;

  private int step;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_oobe);
    ViewUtil.applyWindowInsets(findViewById(R.id.oobe_root));
    applyImmersiveSystemBars();

    backButton = findViewById(R.id.oobe_back_button);
    primaryButton = findViewById(R.id.oobe_primary_button);
    skipButton = findViewById(R.id.oobe_skip_button);
    dotsContainer = findViewById(R.id.oobe_dots);

    backButton.setOnClickListener(v -> goBack());
    primaryButton.setOnClickListener(v -> dispatchPrimary());
    skipButton.setOnClickListener(v -> dispatchSkip());

    buildDots();

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (step > 0) {
                  goBack();
                } else {
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });

    if (!Prefs.isOobeStarted(this)) {
      Prefs.setOobeStarted(this, true);
    }

    if (savedInstanceState == null) {
      step = clamp(Prefs.getOobeStep(this));
      showStep(step, 0);
    } else {
      // the framework restored the current step fragment already
      step = clamp(savedInstanceState.getInt(STATE_STEP, 0));
      updateChrome();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(STATE_STEP, step);
  }

  /**
   * Nova Chat: immersive status bar. The wizard draws its own background behind the system bars, so
   * the bars stay transparent and only the icon colour has to follow the active theme — dark icons
   * on the light background, light icons on the dark one.
   */
  private void applyImmersiveSystemBars() {
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    boolean lightTheme = !DynamicTheme.isDarkTheme(this);
    controller.setAppearanceLightStatusBars(lightTheme);
    controller.setAppearanceLightNavigationBars(lightTheme);
  }

  @Override
  public int getStepIndex() {
    return step;
  }

  @Override
  public void goNext() {
    if (step < STEP_COUNT - 1) {
      step++;
      showStep(step, 1);
    } else {
      finishOobe();
    }
  }

  @Override
  public void goBack() {
    if (step > 0) {
      step--;
      showStep(step, -1);
    }
  }

  @Override
  public void finishOobe() {
    Prefs.setOobeCompleted(this, true);
    Intent intent = new Intent(getApplicationContext(), ConversationListActivity.class);
    intent.putExtra(ConversationListActivity.FROM_WELCOME, true);
    startActivity(intent);
    finishAffinity();
  }

  @Override
  public void setPrimaryText(@StringRes int resId) {
    primaryButton.setText(resId);
  }

  @Override
  public void setPrimaryEnabled(boolean enabled) {
    primaryButton.setEnabled(enabled);
  }

  @Override
  public void setPrimaryVisible(boolean visible) {
    primaryButton.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  @Override
  public void setSkipVisible(boolean visible) {
    skipButton.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  @Override
  public void setSkipText(@StringRes int resId) {
    skipButton.setText(resId);
  }

  private void dispatchPrimary() {
    Fragment current = currentFragment();
    if (current instanceof OobeFragment) {
      ((OobeFragment) current).onPrimary();
    }
  }

  private void dispatchSkip() {
    Fragment current = currentFragment();
    if (current instanceof OobeFragment) {
      ((OobeFragment) current).onSkip();
    }
  }

  @Nullable
  private Fragment currentFragment() {
    return getSupportFragmentManager().findFragmentById(R.id.oobe_fragment_container);
  }

  private void showStep(int step, int direction) {
    Fragment fragment = createFragment(step);
    FragmentTransaction transaction =
        getSupportFragmentManager().beginTransaction();
    if (direction > 0) {
      transaction.setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left);
    } else if (direction < 0) {
      transaction.setCustomAnimations(R.anim.slide_from_left, R.anim.slide_to_right);
    }
    transaction.replace(R.id.oobe_fragment_container, fragment, String.valueOf(step));
    transaction.commitAllowingStateLoss();

    Prefs.setOobeStep(this, step);
    updateChrome();
  }

  private void updateChrome() {
    for (int i = 0; i < dots.length; i++) {
      dots[i].setBackgroundResource(
          i == step ? R.drawable.oobe_dot_active : R.drawable.oobe_dot_inactive);
    }
    // going back is only meaningful before the account exists or between the optional steps
    backButton.setVisibility(step == STEP_REGION || step == STEP_MUHAN ? View.VISIBLE : View.GONE);
  }

  private void buildDots() {
    dotsContainer.removeAllViews();
    dots = new View[STEP_COUNT];
    int size = getResources().getDimensionPixelSize(R.dimen.oobe_dot_size);
    int margin = getResources().getDimensionPixelSize(R.dimen.oobe_dot_margin);
    for (int i = 0; i < STEP_COUNT; i++) {
      View dot = new View(this);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
      params.setMargins(margin, 0, margin, 0);
      dot.setLayoutParams(params);
      dot.setBackgroundResource(R.drawable.oobe_dot_inactive);
      dotsContainer.addView(dot);
      dots[i] = dot;
    }
  }

  private Fragment createFragment(int step) {
    switch (step) {
      case STEP_LANGUAGE:
        return new OobeLanguageFragment();
      case STEP_REGION:
        return new OobeRegionFragment();
      case STEP_ACCOUNT:
        return new OobeAccountFragment();
      case STEP_WEBDAV:
        return new OobeWebDavFragment();
      case STEP_MUHAN:
        return new OobeMuhanFragment();
      case STEP_FINISH:
      default:
        return new OobeFinishFragment();
    }
  }

  private static int clamp(int step) {
    return Math.max(0, Math.min(STEP_COUNT - 1, step));
  }
}