package org.thoughtcrime.securesms.connect;

import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Nova Chat: WebDAV account sync.
 *
 * <p>Uploads an end-to-end encrypted backup of the account (messages, contacts, keys) to an
 * arbitrary WebDAV server and restores it on other devices. The backup file produced by the core
 * is re-encrypted locally (AES-256-GCM, key derived from the user's backup passphrase via
 * PBKDF2-HMAC-SHA256) before it is uploaded, so the server never sees plaintext.
 */
public final class WebDavSyncManager {

  private static final String TAG = "WebDavSyncManager";

  /** Remote folder below the WebDAV root. */
  public static final String REMOTE_FOLDER = "nova-chat";

  /** Stable remote file name holding the latest backup. */
  public static final String REMOTE_FILE = "latest-backup.ac";

  private static final byte[] MAGIC = {'N', 'C', '1'};
  private static final int SALT_LEN = 16;
  private static final int IV_LEN = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final int PBKDF2_ITERATIONS = 120_000;
  private static final int KEY_BITS = 256;

  private WebDavSyncManager() {}

  /** Encrypts a file with a passphrase-derived key. Output: MAGIC | salt | iv | ciphertext. */
  public static void encryptFile(File plain, File encrypted, String passphrase) throws Exception {
    SecureRandom random = new SecureRandom();
    byte[] salt = new byte[SALT_LEN];
    random.nextBytes(salt);
    byte[] iv = new byte[IV_LEN];
    random.nextBytes(iv);
    SecretKey key = deriveKey(passphrase, salt);

    try (FileInputStream in = new FileInputStream(plain);
        OutputStream out = new FileOutputStream(encrypted)) {
      out.write(MAGIC);
      out.write(salt);
      out.write(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      try (CipherOutputStream cout = new CipherOutputStream(out, cipher)) {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
          cout.write(buf, 0, n);
        }
      }
    }
  }

  /** Decrypts a file produced by {@link #encryptFile}. */
  public static void decryptFile(File encrypted, File plain, String passphrase) throws Exception {
    try (FileInputStream in = new FileInputStream(encrypted)) {
      byte[] magic = new byte[MAGIC.length];
      if (in.read(magic) != MAGIC.length || !Arrays.equals(magic, MAGIC)) {
        throw new IOException("不是 Nova Chat 备份文件（magic 不匹配）");
      }
      byte[] salt = readFully(in, SALT_LEN);
      byte[] iv = readFully(in, IV_LEN);
      SecretKey key = deriveKey(passphrase, salt);

      try (FileOutputStream out = new FileOutputStream(plain)) {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        try (CipherInputStream cin = new CipherInputStream(in, cipher)) {
          byte[] buf = new byte[64 * 1024];
          int n;
          while ((n = cin.read(buf)) > 0) {
            out.write(buf, 0, n);
          }
        }
      }
    }
  }

  private static SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
    PBEKeySpec spec =
        new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
  }

  private static byte[] readFully(InputStream in, int len) throws IOException {
    byte[] buf = new byte[len];
    int off = 0;
    while (off < len) {
      int n = in.read(buf, off, len - off);
      if (n < 0) {
        throw new IOException("文件提前结束");
      }
      off += n;
    }
    return buf;
  }

  /** Ensures the remote "nova-chat" collection exists. */
  public static void ensureFolder(String baseUrl, String username, String password)
      throws IOException {
    HttpURLConnection conn = open("mkcol", baseUrl, username, password);
    try {
      int code = conn.getResponseCode();
      if (code != 201 && code != 405 && code != 301 && code != 302) {
        throw new IOException("无法创建远程目录，HTTP " + code);
      }
    } finally {
      conn.disconnect();
    }
  }

  /** Uploads a file via WebDAV PUT. */
  public static void upload(File file, String url, String username, String password)
      throws IOException {
    HttpURLConnection conn = open("put", url, username, password);
    conn.setDoOutput(true);
    conn.setFixedLengthStreamingMode(file.length());
    try {
      try (FileInputStream in = new FileInputStream(file);
          OutputStream out = conn.getOutputStream()) {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
          out.write(buf, 0, n);
        }
      }
      int code = conn.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("上传失败，HTTP " + code + " " + conn.getResponseMessage());
      }
    } finally {
      conn.disconnect();
    }
  }

  /** Downloads a file via WebDAV GET. */
  public static void download(String url, String username, String password, File dest)
      throws IOException {
    HttpURLConnection conn = open("get", url, username, password);
    try {
      int code = conn.getResponseCode();
      if (code != 200) {
        throw new IOException("下载失败，HTTP " + code + " " + conn.getResponseMessage());
      }
      try (InputStream in = conn.getInputStream();
          OutputStream out = new FileOutputStream(dest)) {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
          out.write(buf, 0, n);
        }
      }
    } finally {
      conn.disconnect();
    }
  }

  /** Returns the full remote URL for the backup file. */
  public static String remoteBackupUrl(String baseUrl) {
    String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    return base + REMOTE_FOLDER + "/" + REMOTE_FILE;
  }

  private static HttpURLConnection open(String method, String url, String username, String password)
      throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setConnectTimeout(20_000);
    conn.setReadTimeout(120_000);
    conn.setInstanceFollowRedirects(true);
    if ("put".equals(method)) {
      conn.setRequestMethod("PUT");
    } else if ("mkcol".equals(method)) {
      conn.setRequestMethod("MKCOL");
    } else {
      conn.setRequestMethod("GET");
    }
    String creds = username + ":" + (password == null ? "" : password);
    String auth =
        "Basic "
            + Base64.encodeToString(creds.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    conn.setRequestProperty("Authorization", auth);
    return conn;
  }

  /** SHA-256 helper used for debugging / file naming only. */
  public static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (FileInputStream in = new FileInputStream(file)) {
      byte[] buf = new byte[64 * 1024];
      int n;
      while ((n = in.read(buf)) > 0) {
        digest.update(buf, 0, n);
      }
    }
    byte[] hash = digest.digest();
    StringBuilder sb = new StringBuilder();
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
