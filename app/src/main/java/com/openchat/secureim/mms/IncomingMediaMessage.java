package com.openchat.secureim.mms;

import android.text.TextUtils;
import android.util.Log;

import com.openchat.secureim.crypto.MasterSecretUnion;
import com.openchat.secureim.crypto.MediaKey;
import com.openchat.secureim.database.PartDatabase;
import com.openchat.secureim.util.GroupUtil;
import com.openchat.secureim.util.Util;
import com.openchat.protocal.util.guava.Optional;
import com.openchat.imservice.api.messages.OpenchatServiceAttachment;
import com.openchat.imservice.api.messages.OpenchatServiceGroup;

import java.util.List;

import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.RetrieveConf;

public class IncomingMediaMessage {

  private final PduHeaders headers;
  private final PduBody    body;
  private final String     groupId;
  private final boolean    push;

  public IncomingMediaMessage(RetrieveConf retrieved) {
    this.headers = retrieved.getPduHeaders();
    this.body    = retrieved.getBody();
    this.groupId = null;
    this.push    = false;
  }

  public IncomingMediaMessage(MasterSecretUnion masterSecret,
                              String from,
                              String to,
                              long sentTimeMillis,
                              Optional<String> relay,
                              Optional<String> body,
                              Optional<OpenchatServiceGroup> group,
                              Optional<List<OpenchatServiceAttachment>> attachments)
  {
    this.headers = new PduHeaders();
    this.body    = new PduBody();
    this.push    = true;

    if (group.isPresent()) {
      this.groupId = GroupUtil.getEncodedId(group.get().getGroupId());
    } else {
      this.groupId = null;
    }

    this.headers.setEncodedStringValue(new EncodedStringValue(from), PduHeaders.FROM);
    this.headers.appendEncodedStringValue(new EncodedStringValue(to), PduHeaders.TO);
    this.headers.setLongInteger(sentTimeMillis / 1000, PduHeaders.DATE);

    if (body.isPresent() && !TextUtils.isEmpty(body.get())) {
      PduPart text = new PduPart();
      text.setData(Util.toUtf8Bytes(body.get()));
      text.setContentType(Util.toIsoBytes("text/plain"));
      text.setCharset(CharacterSets.UTF_8);
      this.body.addPart(text);
    }

    if (attachments.isPresent()) {
      for (OpenchatServiceAttachment attachment : attachments.get()) {
        if (attachment.isPointer()) {
          PduPart media        = new PduPart();
          String  encryptedKey = MediaKey.getEncrypted(masterSecret, attachment.asPointer().getKey());

          media.setContentType(Util.toIsoBytes(attachment.getContentType()));
          media.setContentLocation(Util.toIsoBytes(String.valueOf(attachment.asPointer().getId())));
          media.setContentDisposition(Util.toIsoBytes(encryptedKey));

          if (relay.isPresent()) {
            media.setName(Util.toIsoBytes(relay.get()));
          }

          media.setTransferProgress(PartDatabase.TRANSFER_PROGRESS_AUTO_PENDING);

          this.body.addPart(media);
        }
      }
    }
  }

  public PduHeaders getPduHeaders() {
    return headers;
  }

  public PduBody getBody() {
    return body;
  }

  public String getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public boolean isGroupMessage() {
    return groupId != null                                           ||
        !Util.isEmpty(headers.getEncodedStringValues(PduHeaders.CC)) ||
        (headers.getEncodedStringValues(PduHeaders.TO) != null &&
         headers.getEncodedStringValues(PduHeaders.TO).length > 1);
  }
}
