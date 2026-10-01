package org.thoughtcrime.securesms.muhan;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Nova Chat: a minimal voice recorder that writes 16 kHz mono PCM into a WAV file.
 *
 * <p>WAV (instead of the platform default M4A) is used on purpose: it can be handed to OpenAI
 * compatible chat endpoints as an inline {@code input_audio} part without any transcoding, and it
 * is accepted by the transcription endpoint as well.
 */
public final class MuhanVoiceRecorder {

  private static final String TAG = "MuhanVoiceRecorder";

  private static final int SAMPLE_RATE = 16_000;
  private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
  private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  public interface Listener {
    void onFinished(@NonNull File file, long durationMs);

    void onError(@NonNull Exception error);
  }

  private final Listener listener;

  private AudioRecord audioRecord;
  private Thread worker;
  private File outputFile;
  private volatile boolean recording;
  private volatile long recordedBytes;

  public MuhanVoiceRecorder(@NonNull Listener listener) {
    this.listener = listener;
  }

  public boolean isRecording() {
    return recording;
  }

  /** Starts capturing; throws when the microphone is unavailable. */
  public void start(@NonNull Context context) throws IOException {
    if (recording) {
      throw new IllegalStateException("already recording");
    }
    outputFile = new File(MuhanMediaUtil.mediaDir(context), UUID.randomUUID() + ".wav");
    int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING);
    if (minBuffer <= 0) {
      throw new IOException("unsupported audio configuration");
    }
    int bufferSize = Math.max(minBuffer, SAMPLE_RATE);

    audioRecord =
        new AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, ENCODING, bufferSize * 2);
    if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
      release();
      throw new IOException("cannot open the microphone");
    }

    recordedBytes = 0;
    recording = true;
    audioRecord.startRecording();

    worker = new Thread(this::recordLoop, "muhan-voice-recorder");
    worker.start();
  }

  private void recordLoop() {
    short[] buffer = new short[SAMPLE_RATE / 2];
    byte[] pcm = new byte[buffer.length * 2];
    try (RandomAccessFile file = new RandomAccessFile(outputFile, "rw")) {
      file.write(new byte[44]);
      while (recording) {
        int read = audioRecord.read(buffer, 0, buffer.length);
        if (read <= 0) {
          continue;
        }
        for (int i = 0; i < read; i++) {
          pcm[2 * i] = (byte) (buffer[i] & 0xff);
          pcm[2 * i + 1] = (byte) ((buffer[i] >> 8) & 0xff);
        }
        file.write(pcm, 0, read * 2);
        recordedBytes += read * 2;
      }
      file.seek(0);
      file.write(wavHeader(recordedBytes));
    } catch (Exception e) {
      Log.w(TAG, "Recording failed: " + e.getMessage());
      recording = false;
      listener.onError(e);
    }
  }

  /** Stops the capture and reports the recording through the listener. */
  public void stop() {
    if (!recording) {
      return;
    }
    recording = false;
    stopAudioRecord();
    joinWorker();
    release();

    long duration = recordedBytes * 1000L / (SAMPLE_RATE * 2L);
    if (outputFile == null || recordedBytes <= 0) {
      discard();
      listener.onError(new IOException("empty recording"));
      return;
    }
    File finished = outputFile;
    outputFile = null;
    listener.onFinished(finished, duration);
  }

  /** Stops the capture and deletes the file. */
  public void cancel() {
    if (!recording && outputFile == null) {
      return;
    }
    recording = false;
    stopAudioRecord();
    joinWorker();
    release();
    discard();
  }

  private void stopAudioRecord() {
    AudioRecord record = audioRecord;
    if (record != null) {
      try {
        record.stop();
      } catch (Exception ignored) {
      }
    }
  }

  private void joinWorker() {
    Thread thread = worker;
    if (thread != null) {
      try {
        thread.join(2_000);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      worker = null;
    }
  }

  private void release() {
    AudioRecord record = audioRecord;
    if (record != null) {
      try {
        record.release();
      } catch (Exception ignored) {
      }
      audioRecord = null;
    }
  }

  private void discard() {
    File file = outputFile;
    outputFile = null;
    if (file != null && file.exists()) {
      //noinspection ResultOfMethodCallIgnored
      file.delete();
    }
  }

  /** Builds a canonical 44 byte little-endian WAV header for 16 bit mono PCM. */
  @NonNull
  private static byte[] wavHeader(long pcmBytes) {
    int byteRate = SAMPLE_RATE * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes());
    header.putInt((int) (36 + pcmBytes));
    header.put("WAVE".getBytes());
    header.put("fmt ".getBytes());
    header.putInt(16);
    header.putShort((short) 1); // PCM
    header.putShort((short) 1); // mono
    header.putInt(SAMPLE_RATE);
    header.putInt(byteRate);
    header.putShort((short) 2); // block align
    header.putShort((short) 16); // bits per sample
    header.put("data".getBytes());
    header.putInt((int) pcmBytes);
    return header.array();
  }

  @Nullable
  public File getOutputFile() {
    return outputFile;
  }
}