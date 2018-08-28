package com.openchat.secureim.federation;


import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.bouncycastle.openssl.PEMReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openchat.secureim.entities.AccountCount;
import com.openchat.secureim.entities.AttachmentUri;
import com.openchat.secureim.entities.ClientContact;
import com.openchat.secureim.entities.ClientContacts;
import com.openchat.secureim.entities.MessageResponse;
import com.openchat.secureim.entities.RelayMessage;
import com.openchat.secureim.entities.UnstructuredPreKeyList;
import com.openchat.secureim.util.Base64;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

public class FederatedClient {

  private final Logger logger = LoggerFactory.getLogger(FederatedClient.class);

  private static final String USER_COUNT_PATH     = "/v1/federation/user_count";
  private static final String USER_TOKENS_PATH    = "/v1/federation/user_tokens/%d";
  private static final String RELAY_MESSAGE_PATH  = "/v1/federation/message";
  private static final String PREKEY_PATH         = "/v1/federation/key/%s";
  private static final String ATTACHMENT_URI_PATH = "/v1/federation/attachment/%d";

  private final FederatedPeer peer;
  private final Client        client;
  private final String        authorizationHeader;

  public FederatedClient(String federationName, FederatedPeer peer)
      throws IOException
  {
    try {
      this.client              = Client.create(getClientConfig(peer));
      this.peer                = peer;
      this.authorizationHeader = getAuthorizationHeader(federationName, peer);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (KeyStoreException | KeyManagementException | CertificateException e) {
      throw new IOException(e);
    }
  }

  public URL getSignedAttachmentUri(long attachmentId) throws IOException {
    try {
      WebResource resource = client.resource(peer.getUrl())
                                   .path(String.format(ATTACHMENT_URI_PATH, attachmentId));

      return resource.accept(MediaType.APPLICATION_JSON)
                     .header("Authorization", authorizationHeader)
                     .get(AttachmentUri.class)
                     .getLocation();
    } catch (UniformInterfaceException | ClientHandlerException e) {
      logger.warn("Bad URI", e);
      throw new IOException(e);
    }
  }

  public UnstructuredPreKeyList getKeys(String destination)  {
    try {
      WebResource resource = client.resource(peer.getUrl()).path(String.format(PREKEY_PATH, destination));
      return resource.accept(MediaType.APPLICATION_JSON)
                     .header("Authorization", authorizationHeader)
                     .get(UnstructuredPreKeyList.class);
    } catch (UniformInterfaceException | ClientHandlerException e) {
      logger.warn("PreKey", e);
      return null;
    }
  }

  public int getUserCount() {
    try {
      WebResource  resource = client.resource(peer.getUrl()).path(USER_COUNT_PATH);
      AccountCount count    = resource.accept(MediaType.APPLICATION_JSON)
                                      .header("Authorization", authorizationHeader)
                                      .get(AccountCount.class);

      return count.getCount();
    } catch (UniformInterfaceException | ClientHandlerException e) {
      logger.warn("User Count", e);
      return 0;
    }
  }

  public List<ClientContact> getUserTokens(int offset) {
    try {
      WebResource    resource = client.resource(peer.getUrl()).path(String.format(USER_TOKENS_PATH, offset));
      ClientContacts contacts = resource.accept(MediaType.APPLICATION_JSON)
                                        .header("Authorization", authorizationHeader)
                                        .get(ClientContacts.class);

      return contacts.getContacts();
    } catch (UniformInterfaceException | ClientHandlerException e) {
      logger.warn("User Tokens", e);
      return null;
    }
  }

  public MessageResponse sendMessages(List<RelayMessage> messages)
      throws IOException
  {
    try {
      WebResource    resource = client.resource(peer.getUrl()).path(RELAY_MESSAGE_PATH);
      ClientResponse response = resource.type(MediaType.APPLICATION_JSON)
                                        .header("Authorization", authorizationHeader)
                                        .entity(messages)
                                        .put(ClientResponse.class);

      if (response.getStatus() != 200 && response.getStatus() != 204) {
        throw new IOException("Bad response: " + response.getStatus());
      }

      return response.getEntity(MessageResponse.class);
    } catch (UniformInterfaceException | ClientHandlerException e) {
      logger.warn("sendMessage", e);
      throw new IOException(e);
    }
  }

  private String getAuthorizationHeader(String federationName, FederatedPeer peer) {
    return "Basic " + Base64.encodeBytes((federationName + ":" + peer.getAuthenticationToken()).getBytes());
  }

  private ClientConfig getClientConfig(FederatedPeer peer)
      throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, CertificateException
  {
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
    trustManagerFactory.init(initializeTrustStore(peer.getName(), peer.getCertificate()));

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustManagerFactory.getTrustManagers(), SecureRandom.getInstance("SHA1PRNG"));

    ClientConfig config = new DefaultClientConfig();
    config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
                               new HTTPSProperties(new StrictHostnameVerifier(), sslContext));
    config.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);

    return config;
  }

  private KeyStore initializeTrustStore(String name, String pemCertificate)
      throws CertificateException
  {
    try {
      PEMReader       reader      = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemCertificate.getBytes())));
      X509Certificate certificate = (X509Certificate) reader.readObject();

      if (certificate == null) {
        throw new CertificateException("No certificate found in parsing!");
      }

      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null);
      keyStore.setCertificateEntry(name, certificate);

      return keyStore;
    } catch (IOException | KeyStoreException e) {
      throw new CertificateException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public String getPeerName() {
    return peer.getName();
  }
}
