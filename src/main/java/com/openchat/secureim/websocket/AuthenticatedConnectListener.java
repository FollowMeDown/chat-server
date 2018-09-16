package com.openchat.secureim.websocket;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.push.PushSender;
import com.openchat.secureim.push.ReceiptSender;
import com.openchat.secureim.storage.Account;
import com.openchat.secureim.storage.AccountsManager;
import com.openchat.secureim.storage.Device;
import com.openchat.secureim.storage.MessagesManager;
import com.openchat.secureim.storage.PubSubManager;
import com.openchat.secureim.util.Constants;
import com.openchat.secureim.util.Util;
import com.openchat.websocket.session.WebSocketSessionContext;
import com.openchat.websocket.setup.WebSocketConnectListener;

import static com.codahale.metrics.MetricRegistry.name;

public class AuthenticatedConnectListener implements WebSocketConnectListener {

  private static final Logger         logger            = LoggerFactory.getLogger(WebSocketConnection.class);
  private static final MetricRegistry metricRegistry    = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private static final Histogram      durationHistogram = metricRegistry.histogram(name(WebSocketConnection.class, "connected_duration"));


  private final AccountsManager accountsManager;
  private final PushSender      pushSender;
  private final ReceiptSender   receiptSender;
  private final MessagesManager messagesManager;
  private final PubSubManager   pubSubManager;

  public AuthenticatedConnectListener(AccountsManager accountsManager, PushSender pushSender,
                                      ReceiptSender receiptSender,  MessagesManager messagesManager,
                                      PubSubManager pubSubManager)
  {
    this.accountsManager = accountsManager;
    this.pushSender      = pushSender;
    this.receiptSender   = receiptSender;
    this.messagesManager = messagesManager;
    this.pubSubManager   = pubSubManager;
  }

  @Override
  public void onWebSocketConnect(WebSocketSessionContext context) {
    final Account             account     = context.getAuthenticated(Account.class).get();
    final Device              device      = account.getAuthenticatedDevice().get();
    final long                connectTime = System.currentTimeMillis();
    final WebsocketAddress    address     = new WebsocketAddress(account.getNumber(), device.getId());
    final WebSocketConnection connection  = new WebSocketConnection(pushSender, receiptSender,
                                                                    messagesManager, account, device,
                                                                    context.getClient());

    updateLastSeen(account, device);
    pubSubManager.subscribe(address, connection);

    context.addListener(new WebSocketSessionContext.WebSocketEventListener() {
      @Override
      public void onWebSocketClose(WebSocketSessionContext context, int statusCode, String reason) {
        pubSubManager.unsubscribe(address, connection);
        durationHistogram.update(System.currentTimeMillis() - connectTime);
      }
    });
  }

  private void updateLastSeen(Account account, Device device) {
    if (device.getLastSeen() != Util.todayInMillis()) {
      device.setLastSeen(Util.todayInMillis());
      accountsManager.update(account);
    }
  }
}

