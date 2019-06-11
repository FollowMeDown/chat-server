package com.openchat.secureim.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.openchat.secureim.ConversationActivity;
import com.openchat.secureim.recipients.Recipient;
import com.openchat.secureim.recipients.Recipients;

public class NotificationItem {

  private final Recipients   recipients;
  private final Recipient    individualRecipient;
  private final Recipients   threadRecipients;
  private final long         threadId;
  private final CharSequence text;
  private final long         timestamp;

  public NotificationItem(Recipient individualRecipient, Recipients recipients,
                          Recipients threadRecipients, long threadId,
                          CharSequence text, long timestamp)
  {
    this.individualRecipient = individualRecipient;
    this.recipients          = recipients;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.threadId            = threadId;
    this.timestamp           = timestamp;
  }

  public Recipients getRecipients() {
    return threadRecipients == null ? recipients : threadRecipients;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public CharSequence getText() {
    return text;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getThreadId() {
    return threadId;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent     intent           = new Intent(context, ConversationActivity.class);
    Recipients notifyRecipients = threadRecipients != null ? threadRecipients : recipients;
    if (notifyRecipients != null) intent.putExtra("recipients", notifyRecipients.getIds());

    intent.putExtra("thread_id", threadId);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

}
