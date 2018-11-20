package com.openchat.imservice.internal.push.http;

import com.openchat.imservice.api.crypto.DigestingOutputStream;
import com.openchat.imservice.api.crypto.ProfileCipherOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ProfileCipherOutputStreamFactory implements OutputStreamFactory {

  private final byte[] key;

  public ProfileCipherOutputStreamFactory(byte[] key) {
    this.key = key;
  }

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) throws IOException {
    return new ProfileCipherOutputStream(wrap, key);
  }

  @Override
  public long getCiphertextLength(long plaintextLength) {
    return ProfileCipherOutputStream.getCiphertextLength(plaintextLength);
  }
}