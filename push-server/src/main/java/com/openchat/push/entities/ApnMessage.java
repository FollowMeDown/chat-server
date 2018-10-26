package com.openchat.push.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ApnMessage {

  @JsonProperty
  @NotEmpty
  private String apnId;

  @JsonProperty
  @NotEmpty
  private String number;

  @JsonProperty
  @Min(1)
  private int deviceId;

  @JsonProperty
  @NotEmpty
  private String message;

  @JsonProperty
  @NotNull
  private boolean voip;

  public ApnMessage() {}

  @VisibleForTesting
  public ApnMessage(String apnId, String number, int deviceId, String message, boolean voip) {
    this.apnId    = apnId;
    this.number   = number;
    this.deviceId = deviceId;
    this.message  = message;
    this.voip     = voip;
  }

  public String getApnId() {
    return apnId;
  }

  public String getNumber() {
    return number;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public String getMessage() {
    return message;
  }

  public boolean isVoip() {
    return voip;
  }
}
