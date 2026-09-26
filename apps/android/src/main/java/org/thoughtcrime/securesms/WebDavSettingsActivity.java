package org.thoughtcrime.securesms;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEvent;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.thoughtcrime.securesms.connect.AccountManager;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.connect.WebDavSyncManager;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.ProgressDialog;

/**
 * Nova Chat: WebDAV account sync settings.
 *
 * <p>Backs up the current account (messages, contacts, keys) as an encrypted file to an arbitrary
 * WebDAV server, and restores it on other devices. Encryption uses the user-provided backup
 * passphrase, the server only sees ciphertext.
 */
public class WebDavSettingsActivity extends BaseActionBarActivity
    implements DcEventCenter.DcEventDelegate {

  private static final int IMEX_NONE = 0;
  private static final int IMEX_EXPORT = 1;
  private static final int IMEX_IMPORT = 2;

  private EditText urlInput;
  private EditText usernameInput;
  private EditText passwordInput;
  private EditText passphraseInput;
  private TextView statusView;
  private ProgressDialog progressDialog;

  private int pendingImex = IMEX_NONE;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_webdav_settings);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    // keep the scrollable content clear of the navigation bar (edge-to-edge)
    ViewUtil.applyWindowInsets(findViewById(R.id.webdav_scroll), false, false, false, true);

    urlInput = findViewById(R.id.webdav_url_input);
    usernameInput = findViewById(R.id.webdav_username_input);
    passwordInput = findViewById(R.id.webdav_password_input);
    passphraseInput = findViewById(R.id.webdav_passphrase_input);
    statusView = findViewById(R.id.webdav_status);

    Button backupButton = findViewById(R.id.webdav_backup_button);
    backupButton.setOnClickListener(v -> startBackup());

    Button restoreButton = findViewById(R.id.webdav_restore_button);
    restoreButton.setOnClickListener(v -> confirmRestore());

    DcHelper.getEventCenter(this).addObserver(DcContext.DC_EVENT_IMEX_PROGRESS, this);
    statusView.setText(R.string.webdav_sync_explain);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    DcHelper.getEventCenter(this).removeObservers(this);
    executor.shutdown();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private String getUrl() {
    return urlInput.getText().toString().trim();
  }

  private String getUsername() {
    return usernameInput.getText().toString().trim();
  }

  private String getPassword() {
    return passwordInput.getText().toString();
  }

  private String getPassphrase() {
    return passphraseInput.getText().toString();
  }

  private boolean validateInput() {
    if (TextUtils.isEmpty(getUrl())
        || TextUtils.isEmpty(getUsername())
        || TextUtils.isEmpty(getPassword())
        || TextUtils.isEmpty(getPassphrase())) {
      Toast.makeText(this, R.string.webdav_required_fields, Toast.LENGTH_LONG).show();
      return false;
    }
    return true;
  }

  private void showProgress(int msgRes) {
    if (progressDialog != null) {
      progressDialog.dismiss();
    }
    progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getString(msgRes));
    progressDialog.setCancelable(false);
    progressDialog.setCanceledOnTouchOutside(false);
    progressDialog.show();
  }

  private void dismissProgress() {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
  }

  private void showError(String message) {
    dismissProgress();
    String text = getString(R.string.webdav_failed, message);
    statusView.setText(text);
    new AlertDialog.Builder(this)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  private void startBackup() {
    if (!validateInput()) {
      return;
    }
    showProgress(R.string.webdav_backup_started);
    final File exportDir = new File(getCacheDir(), "webdav-export");
    if (exportDir.exists()) {
      deleteRecursively(exportDir);
    }
    //noinspection ResultOfMethodCallIgnored
    exportDir.mkdirs();

    executor.execute(
        () -> {
          try {
            WebDavSyncManager.ensureFolder(getUrl(), getUsername(), getPassword());
            pendingImex = IMEX_EXPORT;
            DcHelper.getContext(WebDavSettingsActivity.this)
                .imex(DcContext.DC_IMEX_EXPORT_BACKUP, exportDir.getAbsolutePath());
          } catch (IOException e) {
            Util.runOnMain(() -> showError(e.getMessage()));
          }
        });
  }

  private void confirmRestore() {
    if (!validateInput()) {
      return;
    }
    new AlertDialog.Builder(this)
        .setMessage(R.string.webdav_restore_warn)
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              showProgress(R.string.webdav_restore_started);
              runRestore();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void runRestore() {
    final File encFile = new File(getCacheDir(), "webdav-restore-" + System.currentTimeMillis() + ".ac");
    final File decFile =
        new File(getCacheDir(), "webdav-restore-" + System.currentTimeMillis() + ".tar");
    executor.execute(
        () -> {
          try {
            WebDavSyncManager.download(
                WebDavSyncManager.remoteBackupUrl(getUrl()),
                getUsername(),
                getPassword(),
                encFile);
            WebDavSyncManager.decryptFile(encFile, decFile, getPassphrase());
            Util.runOnMain(() -> importBackup(decFile));
          } catch (Exception e) {
            Util.runOnMain(() -> showError(e.getMessage()));
          }
        });
  }

  private void importBackup(File backupFile) {
    final DcContext dcContext = DcHelper.getContext(this);
    if (dcContext.isConfigured() != 0) {
      // Import into a fresh, unconfigured account.
      AccountManager.getInstance().beginAccountCreation(this);
    }
    pendingImex = IMEX_IMPORT;
    DcHelper.getContext(this).imex(DcContext.DC_IMEX_IMPORT_BACKUP, backupFile.getAbsolutePath());
  }

  @Override
  public void handleEvent(@NonNull DcEvent event) {
    if (event.getId() != DcContext.DC_EVENT_IMEX_PROGRESS || pendingImex == IMEX_NONE) {
      return;
    }
    long progress = event.getData1Int();
    if (progress == 0) {
      final int mode = pendingImex;
      pendingImex = IMEX_NONE;
      final DcContext dcContext = DcHelper.getAccounts(this).getAccount(event.getAccountId());
      final String error = dcContext.getLastError();
      Util.runOnMain(
          () ->
              showError(
                  error == null || error.isEmpty()
                      ? getString(R.string.error)
                      : error));
      if (mode == IMEX_IMPORT && dcContext.isOk()) {
        // roll back the just-created account on failed import
        try {
          AccountManager.getInstance().rollbackAccountCreation(this);
        } catch (Exception ignored) {
        }
      }
    } else if (progress >= 1000) {
      final int mode = pendingImex;
      pendingImex = IMEX_NONE;
      if (mode == IMEX_EXPORT) {
        finishExport();
      } else if (mode == IMEX_IMPORT) {
        finishImport();
      }
    }
  }

  private void finishExport() {
    final File exportDir = new File(getCacheDir(), "webdav-export");
    final File newest = newestBackupFile(exportDir);
    if (newest == null) {
      showError("未找到导出的备份文件");
      return;
    }
    final File enc = new File(getCacheDir(), "webdav-upload-" + System.currentTimeMillis() + ".ac");
    executor.execute(
        () -> {
          try {
            WebDavSyncManager.encryptFile(newest, enc, getPassphrase());
            WebDavSyncManager.upload(
                enc, WebDavSyncManager.remoteBackupUrl(getUrl()), getUsername(), getPassword());
            Util.runOnMain(
                () -> {
                  dismissProgress();
                  statusView.setText(R.string.webdav_backup_ok);
                  Toast.makeText(this, R.string.webdav_backup_ok, Toast.LENGTH_LONG).show();
                });
          } catch (Exception e) {
            Util.runOnMain(() -> showError(e.getMessage()));
          } finally {
            //noinspection ResultOfMethodCallIgnored
            enc.delete();
            deleteRecursively(exportDir);
          }
        });
  }

  private void finishImport() {
    dismissProgress();
    statusView.setText(R.string.webdav_restore_ok);
    Toast.makeText(this, R.string.webdav_restore_needs_reboot, Toast.LENGTH_LONG).show();
    finishAffinity();
  }

  @Nullable
  private static File newestBackupFile(File dir) {
    File[] files = dir.listFiles((d, name) -> name.startsWith("delta-chat-backup") && name.endsWith(".tar"));
    if (files == null || files.length == 0) {
      return null;
    }
    File newest = files[0];
    for (File f : files) {
      if (f.lastModified() > newest.lastModified()) {
        newest = f;
      }
    }
    return newest;
  }

  private static void deleteRecursively(File file) {
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }
}
