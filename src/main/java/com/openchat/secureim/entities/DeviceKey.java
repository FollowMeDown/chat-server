package com.openchat.secureim.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import java.io.Serializable;

public class DeviceKey extends PreKeyV2 implements Serializable {

  @JsonProperty
  @NotEmpty
  private String signature;

  public DeviceKey() {}

  public DeviceKey(long keyId, String publicKey, String signature) {
    super(keyId, publicKey);
    this.signature = signature;
  }

  public String getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || !(object instanceof DeviceKey)) return false;
    DeviceKey that = (DeviceKey) object;

    if (signature == null) {
      return super.equals(object) && that.signature == null;
    } else {
      return super.equals(object) && this.signature.equals(that.signature);
    }
  }

  @Override
  public int hashCode() {
    if (signature == null) {
      return super.hashCode();
    } else {
      return super.hashCode() ^ signature.hashCode();
    }
  }

}
