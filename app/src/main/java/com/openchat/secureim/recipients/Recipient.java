package com.openchat.secureim.recipients;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.openchat.secureim.contacts.avatars.ContactColors;
import com.openchat.secureim.contacts.avatars.ContactPhoto;
import com.openchat.secureim.contacts.avatars.ContactPhotoFactory;
import com.openchat.secureim.recipients.RecipientProvider.RecipientDetails;
import com.openchat.secureim.util.FutureTaskListener;
import com.openchat.secureim.util.GroupUtil;
import com.openchat.secureim.util.ListenableFutureTask;
import com.openchat.protocal.util.guava.Optional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public class Recipient {

  private final static String TAG = Recipient.class.getSimpleName();

  private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

  private final long recipientId;

  private String number;
  private String name;

  private ContactPhoto      contactPhoto;
  private Uri               contactUri;
  private Optional<Integer> color;

  Recipient(long recipientId, String number, ListenableFutureTask<RecipientDetails> future)
  {
    this.recipientId  = recipientId;
    this.number       = number;
    this.contactPhoto = ContactPhotoFactory.getLoadingPhoto();
    this.color        = Optional.absent();

    future.addListener(new FutureTaskListener<RecipientDetails>() {
      @Override
      public void onSuccess(RecipientDetails result) {
        if (result != null) {
          synchronized (Recipient.this) {
            Recipient.this.name         = result.name;
            Recipient.this.number       = result.number;
            Recipient.this.contactUri   = result.contactUri;
            Recipient.this.contactPhoto = result.avatar;
            Recipient.this.color        = result.color;
          }

          notifyListeners();
        }
      }

      @Override
      public void onFailure(Throwable error) {
        Log.w("Recipient", error);
      }
    });
  }

  Recipient(long recipientId, RecipientDetails details) {
    this.recipientId  = recipientId;
    this.number       = details.number;
    this.contactUri   = details.contactUri;
    this.name         = details.name;
    this.contactPhoto = details.avatar;
    this.color        = details.color;
  }

  public synchronized Uri getContactUri() {
    return this.contactUri;
  }

  public synchronized @Nullable String getName() {
    return this.name;
  }

  public synchronized @NonNull Optional<Integer> getColor() {
    if      (color.isPresent()) return color;
    else if (name != null)      return Optional.of(ContactColors.generateFor(name));
    else                        return Optional.of(ContactColors.UNKNOWN_COLOR);
  }

  public void setColor(Optional<Integer> color) {
    synchronized (this) {
      this.color = color;
    }

    notifyListeners();
  }

  public String getNumber() {
    return number;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public boolean isGroupRecipient() {
    return GroupUtil.isEncodedGroup(number);
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);
  }

  public synchronized String toShortString() {
    return (name == null ? number : name);
  }

  public synchronized @NonNull ContactPhoto getContactPhoto() {
    return contactPhoto;
  }

  public static Recipient getUnknownRecipient() {
    return new Recipient(-1, new RecipientDetails("Unknown", "Unknown", null,
                                                  ContactPhotoFactory.getDefaultContactPhoto("Unknown"),
                                                  Optional.<Integer>absent()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof Recipient)) return false;

    Recipient that = (Recipient) o;

    return this.recipientId == that.recipientId;
  }

  @Override
  public int hashCode() {
    return 31 + (int)this.recipientId;
  }

  private void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners)
      listener.onModified(Recipient.this);
  }

  public interface RecipientModifiedListener {
    public void onModified(Recipient recipient);
  }
}
