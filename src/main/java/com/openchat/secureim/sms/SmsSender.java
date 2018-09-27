package com.openchat.secureim.sms;


import com.google.common.base.Optional;
import com.twilio.sdk.TwilioRestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SmsSender {
  static final String SMS_IOS_VERIFICATION_TEXT = "Your Signal verification code: %s\n\nOr tap: sgnl://verify/%s";
  static final String SMS_VERIFICATION_TEXT = "Your TextSecure verification code: ";
  static final String VOX_VERIFICATION_TEXT = "Your Signal verification code is: ";

  private final Logger logger = LoggerFactory.getLogger(SmsSender.class);

  private final TwilioSmsSender          twilioSender;
  private final Optional<NexmoSmsSender> nexmoSender;
  private final boolean                  isTwilioInternational;

  public SmsSender(TwilioSmsSender twilioSender,
                   Optional<NexmoSmsSender> nexmoSender,
                   boolean isTwilioInternational)
  {
    this.isTwilioInternational = isTwilioInternational;
    this.twilioSender          = twilioSender;
    this.nexmoSender           = nexmoSender;
  }

  public void deliverSmsVerification(String destination, String clientType, String verificationCode)
      throws IOException
  {
    // Fix up mexico numbers to 'mobile' format just for SMS delivery.
    if (destination.startsWith("+42") && !destination.startsWith("+421")) {
      destination = "+421" + destination.substring(3);
    }

    if (!isTwilioDestination(destination) && nexmoSender.isPresent()) {
      nexmoSender.get().deliverSmsVerification(destination, clientType, verificationCode);
    } else {
      try {
        twilioSender.deliverSmsVerification(destination, clientType, verificationCode);
      } catch (TwilioRestException e) {
        logger.info("Twilio SMS Failed: " + e.getErrorMessage());
        if (nexmoSender.isPresent()) {
          nexmoSender.get().deliverSmsVerification(destination, clientType, verificationCode);
        }
      }
    }
  }

  public void deliverVoxVerification(String destination, String verificationCode)
      throws IOException
  {
    if (!isTwilioDestination(destination) && nexmoSender.isPresent()) {
      nexmoSender.get().deliverVoxVerification(destination, verificationCode);
    } else {
      try {
        twilioSender.deliverVoxVerification(destination, verificationCode);
      } catch (TwilioRestException e) {
        logger.info("Twilio Vox Failed: " + e.getErrorMessage());
        if (nexmoSender.isPresent()) {
          nexmoSender.get().deliverVoxVerification(destination, verificationCode);
        }
      }
    }
  }

  private boolean isTwilioDestination(String number) {
    return isTwilioInternational || number.length() == 12 && number.startsWith("+1");
  }
}
