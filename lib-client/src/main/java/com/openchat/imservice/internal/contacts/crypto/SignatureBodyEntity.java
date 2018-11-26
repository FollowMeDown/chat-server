package com.openchat.imservice.internal.contacts.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignatureBodyEntity {

  @JsonProperty
  private byte[] isvEnclaveQuoteBody;

  @JsonProperty
  private String isvEnclaveQuoteStatus;

  @JsonProperty
  private String timestamp;

  public byte[] getIsvEnclaveQuoteBody() {
    return isvEnclaveQuoteBody;
  }

  public String getIsvEnclaveQuoteStatus() {
    return isvEnclaveQuoteStatus;
  }

  public String getTimestamp() {
    return timestamp;
  }
}
