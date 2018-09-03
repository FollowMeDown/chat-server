package com.openchat.secureim.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import org.eclipse.jetty.websocket.api.CloseStatus;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.auth.AccountAuthenticator;
import com.openchat.secureim.entities.AcknowledgeWebsocketMessage;
import com.openchat.secureim.entities.IncomingWebsocketMessage;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.Device;
import com.openchat.secureim.storage.PubSubListener;
import com.openchat.secureim.storage.PubSubManager;
import com.openchat.secureim.storage.PubSubMessage;
import com.openchat.secureim.storage.StoredMessageManager;
import com.openchat.secureim.websocket.WebsocketAddress;
import com.openchat.secureim.websocket.WebsocketMessage;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.basic.BasicCredentials;

public class WebsocketController implements WebSocketListener, PubSubListener {

  private static final Logger            logger          = LoggerFactory.getLogger(WebsocketController.class);
  private static final ObjectMapper      mapper          = new ObjectMapper();
  private static final Map<Long, String> pendingMessages = new HashMap<>();

  private final AccountAuthenticator accountAuthenticator;
  private final StoredMessageManager storedMessageManager;
  private final PubSubManager        pubSubManager;

  private Account account;
  private Device  device;
  private Session session;

  private long pendingMessageSequence;

  public WebsocketController(AccountAuthenticator accountAuthenticator,
                             StoredMessageManager storedMessageManager,
                             PubSubManager pubSubManager)
  {
    this.accountAuthenticator = accountAuthenticator;
    this.storedMessageManager = storedMessageManager;
    this.pubSubManager        = pubSubManager;
  }

  @Override
  public void onWebSocketConnect(Session session) {
    try {
      UpgradeRequest        request    = session.getUpgradeRequest();
      Map<String, String[]> parameters = request.getParameterMap();
      String[]              usernames  = parameters.get("login"   );
      String[]              passwords  = parameters.get("password");

      if (usernames == null || usernames.length == 0 ||
          passwords == null || passwords.length == 0)
      {
        session.close(new CloseStatus(401, "Unauthorized"));
        return;
      }

      BasicCredentials  credentials = new BasicCredentials(usernames[0], passwords[0]);
      Optional<Account> account     = accountAuthenticator.authenticate(credentials);

      if (!account.isPresent()) {
        session.close(new CloseStatus(401, "Unauthorized"));
        return;
      }

      this.account = account.get();
      this.device  = account.get().getAuthenticatedDevice().get();
      this.session = session;

      this.session.setIdleTimeout(10 * 60 * 1000);
      this.pubSubManager.subscribe(new WebsocketAddress(this.account.getId(),
                                                        this.device.getId()), this);

      handleQueryDatabase();
    } catch (AuthenticationException e) {
      try { session.close(500, "Server Error");} catch (IOException e1) {}
    } catch (IOException ioe) {
      logger.info("Abrupt session close.");
    }
  }

  @Override
  public void onWebSocketText(String body) {
    try {
      IncomingWebsocketMessage incomingMessage = mapper.readValue(body, IncomingWebsocketMessage.class);

      switch (incomingMessage.getType()) {
        case IncomingWebsocketMessage.TYPE_ACKNOWLEDGE_MESSAGE:
          handleMessageAck(body);
          break;
        default:
          close(new CloseStatus(410, "Unknown Type"));
      }
    } catch (IOException e) {
      logger.debug("Parse", e);
      close(new CloseStatus(410, "Badly Formatted"));
    }
  }

  @Override
  public void onWebSocketClose(int i, String s) {
    pubSubManager.unsubscribe(new WebsocketAddress(account.getId(), device.getId()), this);

    List<String> remainingMessages = new LinkedList<>();

    synchronized (pendingMessages) {
      Long[] pendingKeys = pendingMessages.keySet().toArray(new Long[0]);
      Arrays.sort(pendingKeys);

      for (long pendingKey : pendingKeys) {
        remainingMessages.add(pendingMessages.get(pendingKey));
      }

      pendingMessages.clear();
    }

    storedMessageManager.storeMessages(account, device, remainingMessages);
  }


  @Override
  public void onPubSubMessage(PubSubMessage outgoingMessage) {
    switch (outgoingMessage.getType()) {
      case PubSubMessage.TYPE_DELIVER:
        handleDeliverOutgoingMessage(outgoingMessage.getContents());
        break;
      case PubSubMessage.TYPE_QUERY_DB:
        handleQueryDatabase();
        break;
      default:
        logger.warn("Unknown pubsub message: " + outgoingMessage.getType());
    }
  }

  private void handleDeliverOutgoingMessage(String message) {
    try {
      long messageSequence;

      synchronized (pendingMessages) {
        messageSequence = pendingMessageSequence++;
        pendingMessages.put(messageSequence, message);
      }

      WebsocketMessage websocketMessage = new WebsocketMessage(messageSequence, message);
      session.getRemote().sendStringByFuture(mapper.writeValueAsString(websocketMessage));
    } catch (IOException e) {
      logger.debug("Response failed", e);
      close(null);
    }
  }

  private void handleMessageAck(String message) {
    try {
      AcknowledgeWebsocketMessage ack = mapper.readValue(message, AcknowledgeWebsocketMessage.class);

      synchronized (pendingMessages) {
        pendingMessages.remove(ack.getId());
      }
    } catch (IOException e) {
      logger.warn("Mapping", e);
    }
  }

  private void handleQueryDatabase() {
    List<String> messages = storedMessageManager.getOutgoingMessages(account, device);

    for (String message : messages) {
      handleDeliverOutgoingMessage(message);
    }
  }

  @Override
  public void onWebSocketBinary(byte[] bytes, int i, int i2) {
    logger.info("Received binary message!");
  }

  @Override
  public void onWebSocketError(Throwable throwable) {
    logger.info("onWebSocketError", throwable);
  }


  private void close(CloseStatus closeStatus) {
    try {
      if (this.session != null) {
        if (closeStatus != null) this.session.close(closeStatus);
        else                     this.session.close();
      }
    } catch (IOException e) {
      logger.info("close()", e);
    }
  }
}
