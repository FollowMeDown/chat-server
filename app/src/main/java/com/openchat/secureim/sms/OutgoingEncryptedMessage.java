package com.openchat.secureim.sms;

import com.openchat.secureim.recipients.Recipient;
import com.openchat.secureim.recipients.Recipients;

public class OutgoingEncryptedMessage extends OutgoingTextMessage {

  public OutgoingEncryptedMessage(Recipients recipients, String body) {
    super(recipients, body);
  }

  private OutgoingEncryptedMessage(OutgoingEncryptedMessage base, String body) {
    super(base, body);
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingEncryptedMessage(this, body);
  }
}
