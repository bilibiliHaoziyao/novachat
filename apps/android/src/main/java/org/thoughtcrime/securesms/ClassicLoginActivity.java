package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEvent;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.thoughtcrime.securesms.connect.AccountManager;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.oobe.OobeActivity;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.ProgressDialog;

/**
 * Nova Chat: classic first-run login wizard for using your own email account.
 *
 * <p>Single page with email / password / display name and collapsible advanced (IMAP/SMTP)
 * options. Server settings are auto-detected by the core when left empty. E2EE is optional
 * by default (force_encryption is disabled for new accounts).
 */
public class ClassicLoginActivity extends BaseActionBarActivity
    implements DcEventCenter.DcEventDelegate {

  private static final String TAG = "ClassicLoginActivity";

  private EditText emailInput;
  private EditText passwordInput;
  private EditText nameInput;
  private EditText imapServerInput;
  private EditText imapPortInput;
  private EditText smtpServerInput;
  private EditText smtpPortInput;
  private CheckBox sslCheckbox;
  private Button connectButton;
  private ProgressDialog progressDialog;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_classic_login);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    // keep the scrollable content clear of the navigation bar (edge-to-edge)
    ViewUtil.applyWindowInsets(
        findViewById(R.id.classic_login_scroll), false, false, false, true);

    // Nova Chat fix: make the software back control work (toolbar up-arrow and system back).
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                goBack();
              }
            });

    emailInput = findViewById(R.id.email_input);
    passwordInput = findViewById(R.id.password_input);
    nameInput = findViewById(R.id.name_input);
    imapServerInput = findViewById(R.id.imap_server_input);
    imapPortInput = findViewById(R.id.imap_port_input);
    smtpServerInput = findViewById(R.id.smtp_server_input);
    smtpPortInput = findViewById(R.id.smtp_port_input);
    sslCheckbox = findViewById(R.id.ssl_checkbox);
    connectButton = findViewById(R.id.connect_button);

    View advancedContainer = findViewById(R.id.advanced_container);
    findViewById(R.id.advanced_toggle)
        .setOnClickListener(
            v -> {
              boolean visible = advancedContainer.getVisibility() == View.VISIBLE;
              advancedContainer.setVisibility(visible ? View.GONE : View.VISIBLE);
            });

    connectButton.setOnClickListener(v -> startConfigure());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    DcHelper.getEventCenter(this).removeObservers(this);
    executor.shutdown();
  }

  private void startConfigure() {
    final String email = emailInput.getText().toString().trim();
    final String password = passwordInput.getText().toString();
    if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
      Toast.makeText(this, R.string.classic_login_error_required, Toast.LENGTH_LONG).show();
      return;
    }

    final String displayName = nameInput.getText().toString().trim();
    final String imapServer = imapServerInput.getText().toString().trim();
    final String imapPort = imapPortInput.getText().toString().trim();
    final String smtpServer = smtpServerInput.getText().toString().trim();
    final String smtpPort = smtpPortInput.getText().toString().trim();
    final boolean useSsl = sslCheckbox.isChecked();

    progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getString(R.string.classic_login_connecting));
    progressDialog.setCancelable(false);
    progressDialog.setCanceledOnTouchOutside(false);
    progressDialog.show();

    DcHelper.getEventCenter(this).captureNextError();

    executor.execute(
        () -> {
          try {
            // Create a new account and switch to it.
            AccountManager.getInstance().beginAccountCreation(ClassicLoginActivity.this);
            final DcContext dcContext = DcHelper.getContext(ClassicLoginActivity.this);

            dcContext.setConfig("addr", email);
            dcContext.setConfig("mail_pw", password);
            if (!TextUtils.isEmpty(displayName)) {
              dcContext.setConfig("displayname", displayName);
            }

            // Optional advanced (IMAP/SMTP); left empty -> auto-detected by the core.
            if (!TextUtils.isEmpty(imapServer)) {
              dcContext.setConfig("mail_server", imapServer);
              if (!TextUtils.isEmpty(imapPort)) {
                dcContext.setConfig("mail_port", imapPort);
              }
            }
            if (!TextUtils.isEmpty(smtpServer)) {
              dcContext.setConfig("send_server", smtpServer);
              if (!TextUtils.isEmpty(smtpPort)) {
                dcContext.setConfig("send_port", smtpPort);
              }
            }
            if (useSsl) {
              dcContext.setConfig("mail_security", "1");
              dcContext.setConfig("send_security", "1");
            }

            // Nova Chat: end-to-end encryption is optional by default.
            dcContext.setConfig("force_encryption", "0");

            final Rpc rpc = DcHelper.getRpc(ClassicLoginActivity.this);
            rpc.configure(dcContext.getAccountId());
            DcHelper.getEventCenter(ClassicLoginActivity.this).endCaptureNextError();
            Util.runOnMain(() -> progressSuccess());
          } catch (Exception e) {
            DcHelper.getEventCenter(ClassicLoginActivity.this).endCaptureNextError();
            Util.runOnMain(
                () -> progressError(e.getMessage() == null ? "Unknown error" : e.getMessage()));
          }
        });
  }

  private void progressSuccess() {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
    if (getIntent().getBooleanExtra(OobeActivity.EXTRA_FROM_OOBE, false)) {
      // hand control back to the setup wizard, which continues with the remaining steps
      setResult(RESULT_OK);
      finish();
      return;
    }
    Toast.makeText(this, R.string.classic_login_success, Toast.LENGTH_LONG).show();
    Intent intent = new Intent(getApplicationContext(), ConversationListActivity.class);
    intent.putExtra(ConversationListActivity.FROM_WELCOME, true);
    startActivity(intent);
    finishAffinity();
  }

  private void progressError(String message) {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
    String text = getString(R.string.classic_login_error, message);
    new androidx.appcompat.app.AlertDialog.Builder(this)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      goBack();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  /** Roll back a half-created account (if any) and leave the screen. */
  private void goBack() {
    AccountManager accountManager = AccountManager.getInstance();
    if (accountManager.canRollbackAccountCreation(this)) {
      accountManager.rollbackAccountCreation(this);
    }
    finish();
  }

  @Override
  public void handleEvent(@NonNull DcEvent event) {
    // configuration progress events are not needed for the minimal wizard
  }
}
