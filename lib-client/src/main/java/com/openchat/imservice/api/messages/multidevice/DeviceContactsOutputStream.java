package com.openchat.imservice.api.messages.multidevice;

import com.openchat.imservice.internal.push.OpenchatServiceProtos;
import com.openchat.imservice.internal.util.Util;

import java.io.IOException;
import java.io.OutputStream;

public class DeviceContactsOutputStream {

  private final OutputStream out;

  public DeviceContactsOutputStream(OutputStream out) {
    this.out = out;
  }

  public void write(DeviceContact contact) throws IOException {
    writeContactDetails(contact);
    writeAvatarImage(contact);
  }

  public void close() throws IOException {
    out.close();
  }

  private void writeAvatarImage(DeviceContact contact) throws IOException {
    if (contact.getAvatar().isPresent()) {
      Util.copy(contact.getAvatar().get().getInputStream(), out);
    }
  }

  private void writeContactDetails(DeviceContact contact) throws IOException {
    OpenchatServiceProtos.ContactDetails.Builder contactDetails = OpenchatServiceProtos.ContactDetails.newBuilder();
    contactDetails.setNumber(contact.getNumber());

    if (contact.getName().isPresent()) {
      contactDetails.setName(contact.getName().get());
    }

    if (contact.getAvatar().isPresent()) {
      OpenchatServiceProtos.ContactDetails.Avatar.Builder avatarBuilder = OpenchatServiceProtos.ContactDetails.Avatar.newBuilder();
      avatarBuilder.setContentType(contact.getAvatar().get().getContentType());
      avatarBuilder.setLength(contact.getAvatar().get().getLength());
    }

    byte[] serializedContactDetails = contactDetails.build().toByteArray();

    writeVarint64(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }

  public void writeVarint64(long value) throws IOException {
    while (true) {
      if ((value & ~0x7FL) == 0) {
        out.write((int) value);
        return;
      } else {
        out.write(((int) value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

}
