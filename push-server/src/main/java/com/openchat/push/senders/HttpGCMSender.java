package com.openchat.push.senders;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.messaging.server.Message;
import com.openchat.messaging.server.Result;
import com.openchat.messaging.server.Sender;
import com.openchat.push.entities.GcmMessage;
import com.openchat.push.entities.UnregisteredEvent;
import com.openchat.push.util.Constants;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.codahale.metrics.MetricRegistry.name;

public class HttpGCMSender implements GCMSender {

  private final Logger logger = LoggerFactory.getLogger(HttpGCMSender.class);

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          success        = metricRegistry.meter(name(getClass(), "sent", "success"));
  private final Meter          failure        = metricRegistry.meter(name(getClass(), "sent", "failure"));
  private final Meter          unregistered   = metricRegistry.meter(name(getClass(), "sent", "unregistered"));
  private final Meter          canonical      = metricRegistry.meter(name(getClass(), "sent", "canonical"));

  private final Sender            sender;
  private final UnregisteredQueue unregisteredQueue;
  private       ExecutorService   executor;

  public HttpGCMSender(UnregisteredQueue unregisteredQueue, String apiKey) {
    this.unregisteredQueue = unregisteredQueue;
    this.sender            = new Sender(apiKey, 50);
  }

  @Override
  public void sendMessage(GcmMessage message) {
    Message request = Message.newBuilder()
                             .withDestination(message.getGcmId())
                             .withDataPart(message.isReceipt() ? "receipt" : "message", message.getMessage())
                             .build();

    ListenableFuture<Result> future = sender.send(request, message);

    Futures.addCallback(future, new FutureCallback<Result>() {
      @Override
      public void onSuccess(Result result) {
        if (result.isUnregistered() || result.isInvalidRegistrationId()) {
          handleBadRegistration(result);
        } else if (result.hasCanonicalRegistrationId()) {
          handleCanonicalRegistrationId(result);
        } else if (!result.isSuccess()) {
          handleGenericError(result);
        } else {
          success.mark();
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        logger.warn("GCM Failed: " + throwable);
      }
    }, executor);
  }

  @Override
  public void start() {
    executor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void stop() throws IOException {
    this.sender.stop();
    this.executor.shutdown();
  }

  private void handleBadRegistration(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn("Got GCM unregistered notice! " + message.getGcmId());
    unregisteredQueue.put(new UnregisteredEvent(message.getGcmId(), message.getNumber(),
                                                message.getDeviceId(), System.currentTimeMillis()));
    unregistered.mark();
  }

  private void handleCanonicalRegistrationId(Result result) {
    logger.warn(String.format("Actually received 'CanonicalRegistrationId' ::: (canonical=%s), (original=%s)",
                              result.getCanonicalRegistrationId(), ((GcmMessage)result.getContext()).getGcmId()));
    canonical.mark();
  }

  private void handleGenericError(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn(String.format("Unrecoverable Error ::: (error=%s), (gcm_id=%s), " +
                              "(destination=%s), (device_id=%d)",
                              result.getError(), message.getGcmId(), message.getNumber(),
                              message.getDeviceId()));
    failure.mark();
  }
}
