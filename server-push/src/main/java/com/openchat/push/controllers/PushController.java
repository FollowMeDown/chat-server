package com.openchat.push.controllers;

import com.codahale.metrics.annotation.Timed;
import com.openchat.push.auth.Server;
import com.openchat.push.entities.ApnMessage;
import com.openchat.push.entities.GcmMessage;
import com.openchat.push.senders.APNSender;
import com.openchat.push.senders.GCMSender;
import com.openchat.push.senders.TransientPushFailureException;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;

@Path("/api/v1/push")
public class PushController {

  private final APNSender apnSender;
  private final GCMSender gcmSender;

  public PushController(APNSender apnSender, GCMSender gcmSender) {
    this.apnSender = apnSender;
    this.gcmSender = gcmSender;
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/gcm")
  public void sendGcmPush(@Auth Server server, @Valid GcmMessage gcmMessage) {
    gcmSender.sendMessage(gcmMessage);
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/apn")
  public void sendApnPush(@Auth Server server, @Valid ApnMessage apnMessage)
      throws TransientPushFailureException
  {
    apnSender.sendMessage(apnMessage);
  }

}
