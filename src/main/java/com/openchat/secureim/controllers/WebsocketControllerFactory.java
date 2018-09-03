package com.openchat.secureim.controllers;

import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.auth.AccountAuthenticator;
import com.openchat.secureim.storage.PubSubManager;
import com.openchat.secureim.storage.StoredMessageManager;


public class WebsocketControllerFactory extends WebSocketServlet implements WebSocketCreator {

  private final Logger logger = LoggerFactory.getLogger(WebsocketControllerFactory.class);

  private final StoredMessageManager storedMessageManager;
  private final PubSubManager        pubSubManager;
  private final AccountAuthenticator accountAuthenticator;

  public WebsocketControllerFactory(AccountAuthenticator accountAuthenticator,
                                    StoredMessageManager storedMessageManager,
                                    PubSubManager pubSubManager)
  {
    this.accountAuthenticator = accountAuthenticator;
    this.storedMessageManager = storedMessageManager;
    this.pubSubManager        = pubSubManager;
  }

  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.setCreator(this);
  }

  @Override
  public Object createWebSocket(UpgradeRequest upgradeRequest, UpgradeResponse upgradeResponse) {
    return new WebsocketController(accountAuthenticator, storedMessageManager, pubSubManager);
  }
}
