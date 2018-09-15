package com.openchat.secureim.sms;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.factory.MessageFactory;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import com.openchat.secureim.configuration.TwilioConfiguration;
import com.openchat.secureim.util.Constants;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.codahale.metrics.MetricRegistry.name;

public class TwilioSmsSender {

  public static final String SAY_TWIML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                         "<Response>\n" +
                                         "    <Say voice=\"woman\" language=\"en\" loop=\"3\">" + SmsSender.VOX_VERIFICATION_TEXT + "%s.</Say>\n" +
                                         "</Response>";

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          smsMeter       = metricRegistry.meter(name(getClass(), "sms", "delivered"));
  private final Meter          voxMeter       = metricRegistry.meter(name(getClass(), "vox", "delivered"));

  private final String accountId;
  private final String accountToken;
  private final String number;
  private final String localDomain;

  public TwilioSmsSender(TwilioConfiguration config) {
    this.accountId    = config.getAccountId();
    this.accountToken = config.getAccountToken();
    this.number       = config.getNumber();
    this.localDomain  = config.getLocalDomain();
  }

  public void deliverSmsVerification(String destination, String verificationCode)
      throws IOException, TwilioRestException
  {
    TwilioRestClient    client         = new TwilioRestClient(accountId, accountToken);
    MessageFactory      messageFactory = client.getAccount().getMessageFactory();
    List<NameValuePair> messageParams  = new LinkedList<>();
    messageParams.add(new BasicNameValuePair("To", destination));
    messageParams.add(new BasicNameValuePair("From", number));
    messageParams.add(new BasicNameValuePair("Body", SmsSender.SMS_VERIFICATION_TEXT + verificationCode));

    try {
      messageFactory.create(messageParams);
    } catch (RuntimeException damnYouTwilio) {
      throw new IOException(damnYouTwilio);
    }

    smsMeter.mark();
  }

  public void deliverVoxVerification(String destination, String verificationCode)
      throws IOException, TwilioRestException
  {
    TwilioRestClient    client      = new TwilioRestClient(accountId, accountToken);
    CallFactory         callFactory = client.getAccount().getCallFactory();
    Map<String, String> callParams  = new HashMap<>();
    callParams.put("To", destination);
    callParams.put("From", number);
    callParams.put("Url", "https://" + localDomain + "/v1/accounts/voice/twiml/" + verificationCode);

    try {
      callFactory.create(callParams);
    } catch (RuntimeException damnYouTwilio) {
      throw new IOException(damnYouTwilio);
    }

    voxMeter.mark();
  }
}
