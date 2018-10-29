package com.openchat.protocal.ecc;

import com.openchat.curve25519.Curve25519KeyPair;
import com.openchat.protocal.InvalidKeyException;
import com.openchat.curve25519.Curve25519;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Curve {

  public  static final int DJB_TYPE   = 0x05;

  public static boolean isNative() {
    return Curve25519.isNative();
  }

  public static ECKeyPair generateKeyPair() {
    SecureRandom      secureRandom = getSecureRandom();
    Curve25519KeyPair keyPair      = Curve25519.generateKeyPair(secureRandom);

    return new ECKeyPair(new DjbECPublicKey(keyPair.getPublicKey()),
                         new DjbECPrivateKey(keyPair.getPrivateKey()));
  }

  public static ECPublicKey decodePoint(byte[] bytes, int offset)
      throws InvalidKeyException
  {
    int type = bytes[offset] & 0xFF;

    switch (type) {
      case Curve.DJB_TYPE:
        byte[] keyBytes = new byte[32];
        System.arraycopy(bytes, offset+1, keyBytes, 0, keyBytes.length);
        return new DjbECPublicKey(keyBytes);
      default:
        throw new InvalidKeyException("Bad key type: " + type);
    }
  }

  public static ECPrivateKey decodePrivatePoint(byte[] bytes) {
    return new DjbECPrivateKey(bytes);
  }

  public static byte[] calculateAgreement(ECPublicKey publicKey, ECPrivateKey privateKey)
      throws InvalidKeyException
  {
    if (publicKey.getType() != privateKey.getType()) {
      throw new InvalidKeyException("Public and private keys must be of the same type!");
    }

    if (publicKey.getType() == DJB_TYPE) {
      return Curve25519.calculateAgreement(((DjbECPublicKey)publicKey).getPublicKey(),
                                           ((DjbECPrivateKey)privateKey).getPrivateKey());
    } else {
      throw new InvalidKeyException("Unknown type: " + publicKey.getType());
    }
  }

  public static boolean verifySignature(ECPublicKey signingKey, byte[] message, byte[] signature)
      throws InvalidKeyException
  {
    if (signingKey.getType() == DJB_TYPE) {
      return Curve25519.verifySignature(((DjbECPublicKey)signingKey).getPublicKey(), message, signature);
    } else {
      throw new InvalidKeyException("Unknown type: " + signingKey.getType());
    }
  }

  public static byte[] calculateSignature(ECPrivateKey signingKey, byte[] message)
      throws InvalidKeyException
  {
    if (signingKey.getType() == DJB_TYPE) {
      return Curve25519.calculateSignature(getSecureRandom(), ((DjbECPrivateKey)signingKey).getPrivateKey(), message);
    } else {
      throw new InvalidKeyException("Unknown type: " + signingKey.getType());
    }
  }

  private static SecureRandom getSecureRandom() {
    try {
      return SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
