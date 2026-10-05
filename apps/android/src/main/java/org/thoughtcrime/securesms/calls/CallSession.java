package org.thoughtcrime.securesms.calls;

import android.content.Intent;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.telecom.CallControlScope;

@RequiresApi(api = Build.VERSION_CODES.O)
final class CallSession {
  final int accId;
  Integer callId;
  Integer chatId;
  final boolean isIncoming;
  boolean startsWithVideo;
  String offerSdp;
  boolean answerInProgress;
  boolean hasNotifiedBackend;
  volatile String cachedIceServersJson;
  boolean withScreenShare;
  int projectionResultCode;
  @Nullable Intent projectionData;

  CallControlScope callControlScope;
  Runnable endpointTask;

  CallSession(int accId, Integer callId, boolean isIncoming) {
    this.accId = accId;
    this.callId = callId;
    this.isIncoming = isIncoming;
  }

  boolean matches(int accId, int callId) {
    return this.accId == accId && this.callId != null && this.callId == callId;
  }
}
