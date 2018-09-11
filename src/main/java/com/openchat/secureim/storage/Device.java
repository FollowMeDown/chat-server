package com.openchat.secureim.storage;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.openchat.secureim.auth.AuthenticationCredentials;
import com.openchat.secureim.entities.SignedPreKey;
import com.openchat.secureim.util.Util;

import java.util.concurrent.TimeUnit;

public class Device {

  public static final long MASTER_ID = 1;

  @JsonProperty
  private long    id;

  @JsonProperty
  private String  authToken;

  @JsonProperty
  private String  salt;

  @JsonProperty
  private String  signalingKey;

  @JsonProperty
  private String  gcmId;

  @JsonProperty
  private String  apnId;

  @JsonProperty
  private long pushTimestamp;

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private int registrationId;

  @JsonProperty
  private SignedPreKey signedPreKey;

  @JsonProperty
  private long lastSeen;

  public Device() {}

  public Device(long id, String authToken, String salt,
                String signalingKey, String gcmId, String apnId,
                boolean fetchesMessages, int registrationId,
                SignedPreKey signedPreKey, long lastSeen)
  {
    this.id              = id;
    this.authToken       = authToken;
    this.salt            = salt;
    this.signalingKey    = signalingKey;
    this.gcmId           = gcmId;
    this.apnId           = apnId;
    this.fetchesMessages = fetchesMessages;
    this.registrationId  = registrationId;
    this.signedPreKey    = signedPreKey;
    this.lastSeen        = lastSeen;
  }

  public String getApnId() {
    return apnId;
  }

  public void setApnId(String apnId) {
    this.apnId = apnId;

    if (apnId != null) {
      this.pushTimestamp = System.currentTimeMillis();
    }
  }

  public void setLastSeen(long lastSeen) {
    this.lastSeen = lastSeen;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public String getGcmId() {
    return gcmId;
  }

  public void setGcmId(String gcmId) {
    this.gcmId = gcmId;

    if (gcmId != null) {
      this.pushTimestamp = System.currentTimeMillis();
    }
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setAuthenticationCredentials(AuthenticationCredentials credentials) {
    this.authToken = credentials.getHashedAuthenticationToken();
    this.salt      = credentials.getSalt();
  }

  public AuthenticationCredentials getAuthenticationCredentials() {
    return new AuthenticationCredentials(authToken, salt);
  }

  public String getSignalingKey() {
    return signalingKey;
  }

  public void setSignalingKey(String signalingKey) {
    this.signalingKey = signalingKey;
  }

  public boolean isActive() {
    boolean hasChannel = fetchesMessages || !Util.isEmpty(getApnId()) || !Util.isEmpty(getGcmId());

    return (id == MASTER_ID && hasChannel) ||
           (id != MASTER_ID && hasChannel && signedPreKey != null && lastSeen > (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)));
  }

  public boolean getFetchesMessages() {
    return fetchesMessages;
  }

  public void setFetchesMessages(boolean fetchesMessages) {
    this.fetchesMessages = fetchesMessages;
  }

  public boolean isMaster() {
    return getId() == MASTER_ID;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public void setRegistrationId(int registrationId) {
    this.registrationId = registrationId;
  }

  public SignedPreKey getSignedPreKey() {
    return signedPreKey;
  }

  public void setSignedPreKey(SignedPreKey signedPreKey) {
    this.signedPreKey = signedPreKey;
  }

  public long getPushTimestamp() {
    return pushTimestamp;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof Device)) return false;

    Device that = (Device)other;
    return this.id == that.id;
  }

  @Override
  public int hashCode() {
    return (int)this.id;
  }
}
