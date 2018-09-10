package com.openchat.secureim.websocket;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.openchat.secureim.entities.MessageProtos.ProvisioningUuid;
import com.openchat.secureim.storage.PubSubListener;
import com.openchat.secureim.storage.PubSubManager;
import com.openchat.secureim.storage.PubSubProtos.PubSubMessage;
import com.openchat.websocket.WebSocketClient;
import com.openchat.websocket.messages.WebSocketResponseMessage;

public class ProvisioningConnection implements PubSubListener {

  private final PubSubManager       pubSubManager;
  private final ProvisioningAddress provisioningAddress;
  private final WebSocketClient     client;

  public ProvisioningConnection(PubSubManager pubSubManager, WebSocketClient client) {
    this.pubSubManager       = pubSubManager;
    this.client              = client;
    this.provisioningAddress = ProvisioningAddress.generate();
  }

  @Override
  public void onPubSubMessage(PubSubMessage outgoingMessage) {
    if (outgoingMessage.getType() == PubSubMessage.Type.DELIVER) {
      Optional<byte[]> body = Optional.of(outgoingMessage.getContent().toByteArray());

      ListenableFuture<WebSocketResponseMessage> response = client.sendRequest("PUT", "/v1/message", body);

      Futures.addCallback(response, new FutureCallback<WebSocketResponseMessage>() {
        @Override
        public void onSuccess(WebSocketResponseMessage webSocketResponseMessage) {
          pubSubManager.unsubscribe(provisioningAddress, ProvisioningConnection.this);
          client.close(1001, "All you get.");
        }

        @Override
        public void onFailure(Throwable throwable) {
          pubSubManager.unsubscribe(provisioningAddress, ProvisioningConnection.this);
          client.close(1001, "That's all!");
        }
      });
    }
  }

  public void onConnected() {
    this.pubSubManager.subscribe(provisioningAddress, this);
    this.client.sendRequest("PUT", "/v1/address", Optional.of(ProvisioningUuid.newBuilder()
                                                                              .setUuid(provisioningAddress.getAddress())
                                                                              .build()
                                                                              .toByteArray()));
  }

  public void onConnectionLost() {
    this.pubSubManager.unsubscribe(provisioningAddress, this);
    this.client.close(1001, "Done");
  }
}
