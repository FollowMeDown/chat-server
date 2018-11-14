package com.openchat.imservice.api.messages;

import com.openchat.protocal.util.guava.Optional;
import com.openchat.imservice.api.OpenchatServiceMessageReceiver;

public class OpenchatServiceAttachmentPointer extends OpenchatServiceAttachment {

  private final long              id;
  private final byte[]            key;
  private final Optional<String>  relay;
  private final Optional<Integer> size;
  private final Optional<byte[]>  preview;

  public OpenchatServiceAttachmentPointer(long id, String contentType, byte[] key, String relay) {
    this(id, contentType, key, relay, Optional.<Integer>absent(), Optional.<byte[]>absent());
  }

  public OpenchatServiceAttachmentPointer(long id, String contentType, byte[] key, String relay,
                                        Optional<Integer> size, Optional<byte[]> preview)
  {
    super(contentType);
    this.id      = id;
    this.key     = key;
    this.relay   = Optional.fromNullable(relay);
    this.size    = size;
    this.preview = preview;
  }

  public long getId() {
    return id;
  }

  public byte[] getKey() {
    return key;
  }

  @Override
  public boolean isStream() {
    return false;
  }

  @Override
  public boolean isPointer() {
    return true;
  }

  public Optional<String> getRelay() {
    return relay;
  }

  public Optional<Integer> getSize() {
    return size;
  }

  public Optional<byte[]> getPreview() {
    return preview;
  }
}