package com.openchat.secureim.sms;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.configuration.NexmoConfiguration;
import com.openchat.secureim.util.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import static com.codahale.metrics.MetricRegistry.name;

public class NexmoSmsSender {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          smsMeter       = metricRegistry.meter(name(getClass(), "sms", "delivered"));
  private final Meter          voxMeter       = metricRegistry.meter(name(getClass(), "vox", "delivered"));
  private final Logger         logger         = LoggerFactory.getLogger(NexmoSmsSender.class);

  private static final String NEXMO_SMS_URL =
      "https://rest.nexmo.com/sms/json?api_key=%s&api_secret=%s&from=%s&to=%s&text=%s";

  private static final String NEXMO_VOX_URL =
      "https://rest.nexmo.com/tts/json?api_key=%s&api_secret=%s&to=%s&text=%s";

  private final String apiKey;
  private final String apiSecret;
  private final String number;

  public NexmoSmsSender(NexmoConfiguration config) {
    this.apiKey    = config.getApiKey();
    this.apiSecret = config.getApiSecret();
    this.number    = config.getNumber();
  }

  public void deliverSmsVerification(String destination, String verificationCode) throws IOException {
    URL url = new URL(String.format(NEXMO_SMS_URL, apiKey, apiSecret, number, destination,
                                    URLEncoder.encode(SmsSender.SMS_VERIFICATION_TEXT + verificationCode, "UTF-8")));

    URLConnection connection = url.openConnection();
    connection.setDoInput(true);
    connection.connect();

    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    while (reader.readLine() != null) {}
    reader.close();
    smsMeter.mark();
  }

  public void deliverVoxVerification(String destination, String message) throws IOException {
    URL url = new URL(String.format(NEXMO_VOX_URL, apiKey, apiSecret, destination,
                                    URLEncoder.encode(SmsSender.VOX_VERIFICATION_TEXT + message, "UTF-8")));

    URLConnection connection = url.openConnection();
    connection.setDoInput(true);
    connection.connect();

    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    String line;
    while ((line = reader.readLine()) != null) {
      logger.debug(line);
    }
    reader.close();
    voxMeter.mark();
  }


}
