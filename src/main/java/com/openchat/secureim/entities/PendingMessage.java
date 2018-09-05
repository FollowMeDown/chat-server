package com.openchat.secureim.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PendingMessage {

  @JsonProperty
  private String sender;

  @JsonProperty
  private long   messageId;

  @JsonProperty
  private String encryptedOutgoingMessage;

  public PendingMessage() {}

  public PendingMessage(String sender, long messageId, String encryptedOutgoingMessage) {
    this.sender                    = sender;
    this.messageId                 = messageId;
    this.encryptedOutgoingMessage  = encryptedOutgoingMessage;
  }

  public String getEncryptedOutgoingMessage() {
    return encryptedOutgoingMessage;
  }

  public long getMessageId() {
    return messageId;
  }

  public String getSender() {
    return sender;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof PendingMessage)) return false;
    PendingMessage that = (PendingMessage)other;

    return
        this.sender.equals(that.sender) &&
        this.messageId == that.messageId &&
        this.encryptedOutgoingMessage.equals(that.encryptedOutgoingMessage);
  }

  @Override
  public int hashCode() {
    return this.sender.hashCode() ^ (int)this.messageId ^ this.encryptedOutgoingMessage.hashCode();
  }
}
