package org.thoughtcrime.securesms;

import static android.app.Activity.RESULT_OK;
import static org.thoughtcrime.securesms.connect.DcHelper.CONFIG_BCC_SELF;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.loader.app.LoaderManager;
import com.b44t.messenger.DcContext;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import org.thoughtcrime.securesms.components.AvatarSelector;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.scribbles.ScribbleActivity;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ScreenLockUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

@SuppressLint("StaticFieldLeak")
public class CreateProfileActivity extends BaseActionBarActivity {

  private static final String TAG = "CreateProfileActivity";

  private static final int REQUEST_CODE_AVATAR = 1;

  private InputAwareLayout container;
  private ImageView avatar;
  private EditText name;
  private EditText statusView;

  private SwitchCompat multideviceSwitch;
  private ActivityResultLauncher<Intent> screenLockLauncher;

  private boolean avatarChanged;
  private boolean imageLoaded;

  private Bitmap avatarBmp;
  private AttachmentManager attachmentManager;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.profile_create_activity);

    screenLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                ApplicationPreferencesActivity.showBackupProvider(this);
              }
            });

    DcContext dcContext = DcHelper.getContext(this);
    getSupportActionBar()
        .setTitle(
            dcContext.isTeamProfile()
                ? R.string.team_profile
                : R.string.pref_profile_info_headline);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);

    attachmentManager = new AttachmentManager(this, () -> {});
    avatarChanged = false;
    initializeResources();
    initializeProfileName();
    initializeProfileAvatar();
    initializeStatusText();
    initializeAccountSection();

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (container.isInputOpen()) {
                  container.hideCurrentInput(name);
                } else {
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (multideviceSwitch != null) {
      multideviceSwitch.setChecked(0 != DcHelper.getContext(this).getConfigInt(CONFIG_BCC_SELF));
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.preferences_create_profile_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      getOnBackPressedDispatcher().onBackPressed();
      return true;
    } else if (itemId == R.id.menu_create_profile) {
      updateProfile();
    }

    return false;
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (resultCode != Activity.RESULT_OK) {
      return;
    }

    switch (requestCode) {
      case REQUEST_CODE_AVATAR:
        Uri inputFile = (data != null ? data.getData() : null);
        onFileSelected(inputFile);
        break;

      case ScribbleActivity.SCRIBBLE_REQUEST_CODE:
        setAvatarView(data.getData());
        break;
    }
  }

  private void setAvatarView(Uri output) {
    GlideApp.with(this)
        .asBitmap()
        .load(output)
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .centerCrop()
        .override(AvatarHelper.AVATAR_SIZE, AvatarHelper.AVATAR_SIZE)
        .into(
            new SimpleTarget<Bitmap>() {
              @Override
              public void onResourceReady(
                  @NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                avatarChanged = true;
                imageLoaded = true;
                avatarBmp = resource;
              }
            });
    GlideApp.with(this)
        .load(output)
        .circleCrop()
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .into(avatar);
  }

  private void onFileSelected(Uri inputFile) {
    if (inputFile == null) {
      inputFile = attachmentManager.getImageCaptureUri();
    }

    AvatarHelper.cropAvatar(this, inputFile);
  }

  private void initializeResources() {
    TextView loginSuccessText = ViewUtil.findById(this, R.id.login_success_text);
    this.avatar = ViewUtil.findById(this, R.id.avatar);
    this.name = ViewUtil.findById(this, R.id.name_text);
    this.container = ViewUtil.findById(this, R.id.container);
    this.statusView = ViewUtil.findById(this, R.id.status_text);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(container);

    boolean isTeam = DcHelper.getContext(this).isTeamProfile();
    ((TextInputLayout) ViewUtil.findById(this, R.id.name))
        .setHint(isTeam ? R.string.team_name : R.string.pref_your_name);

    loginSuccessText.setVisibility(View.GONE);
  }

  private void initializeProfileName() {
    String profileName = DcHelper.get(this, DcHelper.CONFIG_DISPLAY_NAME);
    if (!TextUtils.isEmpty(profileName)) {
      name.setText(profileName);
      name.setSelection(profileName.length(), profileName.length());
    }
  }

  private void initializeProfileAvatar() {
    File avatarFile = AvatarHelper.getSelfAvatarFile(this);
    if (avatarFile.exists() && avatarFile.length() > 0) {
      imageLoaded = true;
      GlideApp.with(this).load(avatarFile).circleCrop().into(avatar);
    } else {
      imageLoaded = false;
      avatar.setImageDrawable(
          new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
              .asDrawable(this, getResources().getColor(R.color.grey_400)));
    }
    avatar.setOnClickListener(
        view ->
            new AvatarSelector(
                    this,
                    LoaderManager.getInstance(this),
                    new AvatarSelectedListener(),
                    imageLoaded)
                .show(this, avatar));
  }

  private void initializeStatusText() {
    String status = DcHelper.get(this, DcHelper.CONFIG_SELF_STATUS);
    statusView.setText(status);
  }

  /**
   * Nova Chat: account settings (second device, multi-device mode, WebDAV) moved here from the
   * separate "Account" settings screen.
   */
  private void initializeAccountSection() {
    View addSecondDevice = findViewById(R.id.account_multidevice_row);
    addSecondDevice.setOnClickListener(
        v -> {
          if (!ScreenLockUtil.applyScreenLock(
              this,
              getString(R.string.multidevice_title),
              getString(R.string.multidevice_this_creates_a_qr_code)
                  + "\n\n"
                  + getString(R.string.enter_system_secret_to_continue),
              screenLockLauncher)) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.multidevice_title)
                .setMessage(R.string.multidevice_this_creates_a_qr_code)
                .setPositiveButton(
                    R.string.perm_continue,
                    (dialog, which) -> ApplicationPreferencesActivity.showBackupProvider(this))
                .setNegativeButton(R.string.cancel, null)
                .show();
          }
        });

    multideviceSwitch = findViewById(R.id.account_bcc_self_switch);
    multideviceSwitch.setOnCheckedChangeListener(
        (button, isChecked) -> {
          DcContext dcContext = DcHelper.getContext(this);
          if (isChecked) {
            dcContext.setConfigInt(CONFIG_BCC_SELF, 1);
          } else {
            new AlertDialog.Builder(this)
                .setMessage(R.string.pref_multidevice_change_warn)
                .setPositiveButton(
                    R.string.ok,
                    (dialogInterface, i) -> dcContext.setConfigInt(CONFIG_BCC_SELF, 0))
                .setNegativeButton(
                    R.string.cancel, (dialogInterface, i) -> button.setChecked(true))
                .setOnCancelListener(dialog -> button.setChecked(true))
                .show();
          }
        });

    View webdav = findViewById(R.id.account_webdav_row);
    webdav.setOnClickListener(
        v -> startActivity(new Intent(this, WebDavSettingsActivity.class)));
  }

  private void updateProfile() {
    if (TextUtils.isEmpty(this.name.getText())) {
      Toast.makeText(this, R.string.please_enter_name, Toast.LENGTH_LONG).show();
      return;
    }
    final String name = this.name.getText().toString();

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        Context context = CreateProfileActivity.this;
        DcHelper.set(context, DcHelper.CONFIG_DISPLAY_NAME, name);
        setStatusText();

        if (avatarChanged) {
          try {
            AvatarHelper.setSelfAvatar(CreateProfileActivity.this, avatarBmp);
            Prefs.setProfileAvatarId(CreateProfileActivity.this, new SecureRandom().nextInt());
          } catch (IOException e) {
            Log.w(TAG, e);
            return false;
          }
        }

        return true;
      }

      @Override
      public void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (result) {
          attachmentManager.cleanup();
          finish();
        } else {
          Toast.makeText(CreateProfileActivity.this, R.string.error, Toast.LENGTH_LONG).show();
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void setStatusText() {
    String newStatus = statusView.getText().toString().trim();
    DcHelper.set(this, DcHelper.CONFIG_SELF_STATUS, newStatus);
  }

  private class AvatarSelectedListener implements AvatarSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      switch (type) {
        case AvatarSelector.ADD_GALLERY:
          AttachmentManager.selectImage(CreateProfileActivity.this, REQUEST_CODE_AVATAR);
          break;
        case AvatarSelector.REMOVE_PHOTO:
          avatarBmp = null;
          imageLoaded = false;
          avatarChanged = true;
          avatar.setImageDrawable(
              new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
                  .asDrawable(
                      CreateProfileActivity.this, getResources().getColor(R.color.grey_400)));
          break;
        case AvatarSelector.TAKE_PHOTO:
          attachmentManager.capturePhoto(CreateProfileActivity.this, REQUEST_CODE_AVATAR);
          break;
      }
    }

    @Override
    public void onQuickAttachment(Uri inputFile) {
      onFileSelected(inputFile);
    }
  }
}
