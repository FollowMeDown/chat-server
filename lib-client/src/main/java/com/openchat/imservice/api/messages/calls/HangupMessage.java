package com.openchat.imservice.api.messages.calls;

public class HangupMessage {

  private final long id;

  public HangupMessage(long id) {
    this.id = id;
  }

  public long getId() {
    return id;
  }
}
