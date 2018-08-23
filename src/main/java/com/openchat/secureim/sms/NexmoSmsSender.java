package com.openchat.secureim.sms;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.configuration.NexmoConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class NexmoSmsSender {

  private final Meter  smsMeter = Metrics.newMeter(NexmoSmsSender.class, "sms", "delivered", TimeUnit.MINUTES);
  private final Meter  voxMeter = Metrics.newMeter(NexmoSmsSender.class, "vox", "delivered", TimeUnit.MINUTES);
  private final Logger logger   = LoggerFactory.getLogger(NexmoSmsSender.class);

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
