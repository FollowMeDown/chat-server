package com.openchat.secureim.controllers;

import com.google.common.base.Optional;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.entities.AccountCount;
import com.openchat.secureim.entities.AttachmentUri;
import com.openchat.secureim.entities.ClientContact;
import com.openchat.secureim.entities.ClientContacts;
import com.openchat.secureim.entities.IncomingMessageList;
import com.openchat.secureim.entities.PreKey;
import com.openchat.secureim.entities.UnstructuredPreKeyList;
import com.openchat.secureim.federation.FederatedPeer;
import com.openchat.secureim.federation.NonLimitedAccount;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.AccountsManager;
import com.openchat.secureim.util.Util;

import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Path("/v1/federation")
public class FederationController {

  private final Logger logger = LoggerFactory.getLogger(FederationController.class);

  private static final int ACCOUNT_CHUNK_SIZE = 10000;

  private final AccountsManager      accounts;
  private final AttachmentController attachmentController;
  private final KeysController       keysController;
  private final MessageController    messageController;

  public FederationController(AccountsManager      accounts,
                              AttachmentController attachmentController,
                              KeysController       keysController,
                              MessageController     messageController)
  {
    this.accounts             = accounts;
    this.attachmentController = attachmentController;
    this.keysController       = keysController;
    this.messageController    = messageController;
  }

  @Timed
  @GET
  @Path("/attachment/{attachmentId}")
  @Produces(MediaType.APPLICATION_JSON)
  public AttachmentUri getSignedAttachmentUri(@Auth                      FederatedPeer peer,
                                              @PathParam("attachmentId") long attachmentId)
      throws IOException
  {
    return attachmentController.redirectToAttachment(new NonLimitedAccount("Unknown", peer.getName()),
                                                     attachmentId, Optional.<String>absent());
  }

  @Timed
  @GET
  @Path("/key/{number}")
  @Produces(MediaType.APPLICATION_JSON)
  public PreKey getKey(@Auth                FederatedPeer peer,
                       @PathParam("number") String number)
      throws IOException
  {
    try {
      return keysController.get(new NonLimitedAccount("Unknown", peer.getName()), number, Optional.<String>absent());
    } catch (RateLimitExceededException e) {
      logger.warn("Rate limiting on federated channel", e);
      throw new IOException(e);
    }
  }

  @Timed
  @GET
  @Path("/key/{number}/{device}")
  @Produces(MediaType.APPLICATION_JSON)
  public UnstructuredPreKeyList getKeys(@Auth                FederatedPeer peer,
                                        @PathParam("number") String number,
                                        @PathParam("device") String device)
      throws IOException
  {
    try {
      return keysController.getDeviceKey(new NonLimitedAccount("Unknown", peer.getName()),
                                         number, device, Optional.<String>absent());
    } catch (RateLimitExceededException e) {
      logger.warn("Rate limiting on federated channel", e);
      throw new IOException(e);
    }
  }

  @Timed
  @PUT
  @Path("/messages/{source}/{destination}")
  public void sendMessages(@Auth                     FederatedPeer peer,
                           @PathParam("source")      String source,
                           @PathParam("destination") String destination,
                           @Valid                    IncomingMessageList messages)
      throws IOException
  {
    try {
      messages.setRelay(null);
      messageController.sendMessage(new NonLimitedAccount(source, peer.getName()), destination, messages);
    } catch (RateLimitExceededException e) {
      logger.warn("Rate limiting on federated channel", e);
      throw new IOException(e);
    }
  }

  @Timed
  @GET
  @Path("/user_count")
  @Produces(MediaType.APPLICATION_JSON)
  public AccountCount getUserCount(@Auth FederatedPeer peer) {
    return new AccountCount((int)accounts.getCount());
  }

  @Timed
  @GET
  @Path("/user_tokens/{offset}")
  @Produces(MediaType.APPLICATION_JSON)
  public ClientContacts getUserTokens(@Auth                FederatedPeer peer,
                                      @PathParam("offset") int offset)
  {
    List<Account>       accountList    = accounts.getAll(offset, ACCOUNT_CHUNK_SIZE);
    List<ClientContact> clientContacts = new LinkedList<>();

    for (Account account : accountList) {
      byte[]        token         = Util.getContactToken(account.getNumber());
      ClientContact clientContact = new ClientContact(token, null, account.getSupportsSms());

      if (!account.isActive()) {
        clientContact.setInactive(true);
      }

      clientContacts.add(clientContact);
    }

    return new ClientContacts(clientContacts);
  }
}
