package com.openchat.push.senders;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.base.Optional;
import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;
import com.notnoop.exceptions.NetworkIOException;
import org.bouncycastle.openssl.PEMReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.push.entities.ApnMessage;
import com.openchat.push.entities.UnregisteredEvent;
import com.openchat.push.util.Constants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.lifecycle.Managed;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class APNSender implements Managed {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);

  private final Meter  pushMeter    = metricRegistry.meter(name(getClass(), "push"));
  private final Meter  failureMeter = metricRegistry.meter(name(getClass(), "failure"));
  private final Logger logger       = LoggerFactory.getLogger(APNSender.class);

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final JedisPool         jedisPool;
  private final UnregisteredQueue unregisteredQueue;
  private final String            apnCertificate;
  private final String            apnKey;
  private final boolean           feedbackEnabled;

  private ApnsService apnService;

  public APNSender(JedisPool jedisPool, UnregisteredQueue unregisteredQueue,
                   String apnCertificate, String apnKey, boolean feedbackEnabled)
  {
    this.jedisPool         = jedisPool;
    this.unregisteredQueue = unregisteredQueue;
    this.apnCertificate    = apnCertificate;
    this.apnKey            = apnKey;
    this.feedbackEnabled   = feedbackEnabled;
  }

  public void sendMessage(ApnMessage message)
      throws TransientPushFailureException
  {
    try {
      redisSet(message.getApnId(), message.getNumber(), message.getDeviceId());
      apnService.push(message.getApnId(), message.getMessage());
      pushMeter.mark();
    } catch (NetworkIOException nioe) {
      logger.warn("Network Error", nioe);
      failureMeter.mark();
      throw new TransientPushFailureException(nioe);
    }
  }

  private static byte[] initializeKeyStore(String pemCertificate, String pemKey)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException
  {
    PEMReader       reader           = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemCertificate.getBytes())));
    X509Certificate certificate      = (X509Certificate) reader.readObject();
    Certificate[]   certificateChain = {certificate};

    reader = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemKey.getBytes())));
    KeyPair keyPair = (KeyPair) reader.readObject();

    KeyStore keyStore = KeyStore.getInstance("pkcs12");
    keyStore.load(null);
    keyStore.setEntry("apn",
                      new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), certificateChain),
                      new KeyStore.PasswordProtection("insecure".toCharArray()));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    keyStore.store(baos, "insecure".toCharArray());

    return baos.toByteArray();
  }

  @Override
  public void start() throws Exception {
    byte[] keyStore = initializeKeyStore(apnCertificate, apnKey);

    this.apnService = APNS.newService()
                          .withCert(new ByteArrayInputStream(keyStore), "insecure")
                          .asQueued()
                          .withProductionDestination().build();

    if (feedbackEnabled) {
      this.executor.scheduleAtFixedRate(new FeedbackRunnable(), 0, 1, TimeUnit.HOURS);
    }
  }

  @Override
  public void stop() throws Exception {
    apnService.stop();
  }

  private void redisSet(String registrationId, String number, int deviceId) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.set("APN-" + registrationId.toLowerCase(), number + "." + deviceId);
      jedis.expire("APN-" + registrationId.toLowerCase(), (int) TimeUnit.HOURS.toSeconds(1));
    }
  }

  private Optional<String> redisGet(String registrationId) {
    try (Jedis jedis = jedisPool.getResource()) {
      String number = jedis.get("APN-" + registrationId.toLowerCase());
      return Optional.fromNullable(number);
    }
  }

  private class FeedbackRunnable implements Runnable {
    @Override
    public void run() {
      Map<String, Date> inactiveDevices = apnService.getInactiveDevices();

      for (String registrationId : inactiveDevices.keySet()) {
        Optional<String> device = redisGet(registrationId);

        if (device.isPresent()) {
          logger.warn("Got APN unregistered notice!");
          String[] parts    = device.get().split(".", 2);

          if (parts.length == 2) {
            String number    = parts[0];
            int    deviceId  = Integer.parseInt(parts[1]);
            long   timestamp = inactiveDevices.get(registrationId).getTime();

            unregisteredQueue.put(new UnregisteredEvent(registrationId, number, deviceId, timestamp));
          } else {
            logger.warn("APN unregister event for device with no parts: " + device.get());
          }
        } else {
          logger.warn("APN unregister event received for uncached ID: " + registrationId);
        }
      }
    }
  }
}
