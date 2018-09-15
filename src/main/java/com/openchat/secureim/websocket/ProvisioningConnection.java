package com.openchat.secureim.websocket;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.dispatch.DispatchChannel;
import com.openchat.secureim.entities.MessageProtos.ProvisioningUuid;
import com.openchat.secureim.storage.PubSubProtos.PubSubMessage;
import com.openchat.websocket.WebSocketClient;
import com.openchat.websocket.messages.WebSocketResponseMessage;

public class ProvisioningConnection implements DispatchChannel {

  private final Logger logger = LoggerFactory.getLogger(ProvisioningConnection.class);

  private final WebSocketClient client;

  public ProvisioningConnection(WebSocketClient client) {
    this.client = client;
  }

  @Override
  public void onDispatchMessage(String channel, byte[] message) {
    try {
      PubSubMessage outgoingMessage = PubSubMessage.parseFrom(message);

      if (outgoingMessage.getType() == PubSubMessage.Type.DELIVER) {
        Optional<byte[]> body = Optional.of(outgoingMessage.getContent().toByteArray());

        ListenableFuture<WebSocketResponseMessage> response = client.sendRequest("PUT", "/v1/message", body);

        Futures.addCallback(response, new FutureCallback<WebSocketResponseMessage>() {
          @Override
          public void onSuccess(WebSocketResponseMessage webSocketResponseMessage) {
            client.close(1001, "All you get.");
          }

          @Override
          public void onFailure(Throwable throwable) {
            client.close(1001, "That's all!");
          }
        });
      }
    } catch (InvalidProtocolBufferException e) {
      logger.warn("Protobuf Error: ", e);
    }
  }

  @Override
  public void onDispatchSubscribed(String channel) {
    this.client.sendRequest("PUT", "/v1/address", Optional.of(ProvisioningUuid.newBuilder()
                                                                              .setUuid(channel)
                                                                              .build()
                                                                              .toByteArray()));
  }

  @Override
  public void onDispatchUnsubscribed(String channel) {
    this.client.close(1001, "Closed");
  }
}
