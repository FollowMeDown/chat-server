package com.openchat.secureim.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.openchat.secureim.Release;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.DatabaseFactory;
import com.openchat.secureim.database.GroupDatabase;
import com.openchat.secureim.jobs.requirements.MasterSecretRequirement;
import com.openchat.secureim.push.OpenchatServicePushTrustStore;
import com.openchat.secureim.recipients.Recipient;
import com.openchat.secureim.recipients.RecipientFactory;
import com.openchat.secureim.recipients.RecipientFormattingException;
import com.openchat.secureim.util.BitmapDecodingException;
import com.openchat.secureim.util.BitmapUtil;
import com.openchat.secureim.util.GroupUtil;
import com.openchat.secureim.util.OpenchatServicePreferences;
import com.openchat.jobqueue.JobParameters;
import com.openchat.jobqueue.requirements.NetworkRequirement;
import com.openchat.protocal.InvalidMessageException;
import com.openchat.imservice.api.crypto.AttachmentCipherInputStream;
import com.openchat.imservice.push.PushServiceSocket;
import com.openchat.imservice.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AvatarDownloadJob extends MasterSecretJob {

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private final byte[] groupId;

  public AvatarDownloadJob(Context context, byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    GroupDatabase             database   = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long   avatarId = record.getAvatarId();
        byte[] key      = record.getAvatarKey();
        String relay    = record.getRelay();

        if (avatarId == -1 || key == null) {
          return;
        }

        attachment = downloadAttachment(relay, avatarId);

        InputStream scaleInputStream   = new AttachmentCipherInputStream(attachment, key);
        InputStream measureInputStream = new AttachmentCipherInputStream(attachment, key);
        Bitmap      avatar             = BitmapUtil.createScaledBitmap(measureInputStream, scaleInputStream, 500, 500);

        database.updateAvatar(groupId, avatar);

        try {
          Recipient groupRecipient = RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(groupId), true)
                                                     .getPrimaryRecipient();
          groupRecipient.setContactPhoto(avatar);
        } catch (RecipientFormattingException e) {
          Log.w("AvatarDownloader", e);
        }
      }
    } catch (InvalidMessageException | BitmapDecodingException | NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(Release.PUSH_URL,
                                                     new OpenchatServicePushTrustStore(context),
                                                     OpenchatServicePreferences.getLocalNumber(context),
                                                     OpenchatServicePreferences.getPushServerPassword(context));

    File destination = File.createTempFile("avatar", "tmp");

    destination.deleteOnExit();

    socket.retrieveAttachment(relay, contentLocation, destination);

    return destination;
  }

}