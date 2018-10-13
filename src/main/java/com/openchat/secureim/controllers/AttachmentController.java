package com.openchat.secureim.controllers;

import com.amazonaws.HttpMethod;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.entities.AttachmentDescriptor;
import com.openchat.secureim.entities.AttachmentUri;
import com.openchat.secureim.federation.FederatedClientManager;
import com.openchat.secureim.federation.NoSuchPeerException;
import com.openchat.secureim.limits.RateLimiters;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.util.Conversions;
import com.openchat.secureim.s3.UrlSigner;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.stream.Stream;

import io.dropwizard.auth.Auth;


@Path("/v1/attachments")
public class AttachmentController {

  private final Logger logger = LoggerFactory.getLogger(AttachmentController.class);

  private static final String[] UNACCELERATED_REGIONS = {"+20", "+971", "+968", "+974"};

  private final RateLimiters           rateLimiters;
  private final FederatedClientManager federatedClientManager;
  private final UrlSigner              urlSigner;

  public AttachmentController(RateLimiters rateLimiters,
                              FederatedClientManager federatedClientManager,
                              UrlSigner urlSigner)
  {
    this.rateLimiters           = rateLimiters;
    this.federatedClientManager = federatedClientManager;
    this.urlSigner              = urlSigner;
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public AttachmentDescriptor allocateAttachment(@Auth Account account)
      throws RateLimitExceededException
  {
    if (account.isRateLimited()) {
      rateLimiters.getAttachmentLimiter().validate(account.getNumber());
    }

    long attachmentId = generateAttachmentId();
    URL  url          = urlSigner.getPreSignedUrl(attachmentId, HttpMethod.PUT, Stream.of(UNACCELERATED_REGIONS).anyMatch(region -> account.getNumber().startsWith(region)));

    return new AttachmentDescriptor(attachmentId, url.toExternalForm());

  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{attachmentId}")
  public AttachmentUri redirectToAttachment(@Auth                      Account account,
                                            @PathParam("attachmentId") long    attachmentId,
                                            @QueryParam("relay")       Optional<String> relay)
      throws IOException
  {
    try {
      if (!relay.isPresent()) {
        return new AttachmentUri(urlSigner.getPreSignedUrl(attachmentId, HttpMethod.GET, Stream.of(UNACCELERATED_REGIONS).anyMatch(region -> account.getNumber().startsWith(region))));
      } else {
        return new AttachmentUri(federatedClientManager.getClient(relay.get()).getSignedAttachmentUri(attachmentId));
      }
    } catch (NoSuchPeerException e) {
      logger.info("No such peer: " + relay);
      throw new WebApplicationException(Response.status(404).build());
    }
  }

  private long generateAttachmentId() {
    byte[] attachmentBytes = new byte[8];
    new SecureRandom().nextBytes(attachmentBytes);

    attachmentBytes[0] = (byte)(attachmentBytes[0] & 0x7F);
    return Conversions.byteArrayToLong(attachmentBytes);
  }
}
