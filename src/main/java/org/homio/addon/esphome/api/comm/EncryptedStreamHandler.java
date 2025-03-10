package org.homio.addon.esphome.api.comm;

import com.google.protobuf.GeneratedMessage;
import com.southernstorm.noise.protocol.CipherStatePair;
import com.southernstorm.noise.protocol.HandshakeState;
import io.esphome.api.ApiOptions;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.api.CommunicationListener;

import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

@Log4j2
public class EncryptedStreamHandler extends StreamHandler {
  private final static String NOISE_PROTOCOL = "Noise_NNpsk0_25519_ChaChaPoly_SHA256";
  private final String encryptionKeyBase64;
  private final String expectedServername;
  @Getter
  private final ESPHomeConnection connection;
  private HandshakeState client;
  private CipherStatePair cipherStatePair;
  private NoiseProtocolState state;

  public EncryptedStreamHandler(ConnectionSelector connectionSelector, CommunicationListener listener,
                                String encryptionKeyBase64, String hostname) {
    super(listener);
    this.encryptionKeyBase64 = encryptionKeyBase64;
    this.expectedServername = hostname;
    this.connection = new ESPHomeConnection(connectionSelector, this, hostname);
  }

  private static short bytesToShort(final byte[] data) {
    short value = (short) (data[0] & 0xff);
    value <<= 8;
    value |= data[1] & 0xff;
    return value;
  }

  @Override
  public void connect(InetSocketAddress espHomeAddress) throws ProtocolException {
    try {
      client = new HandshakeState(NOISE_PROTOCOL, HandshakeState.INITIATOR);

      // Set preshared key
      byte[] key = Base64.getDecoder().decode(encryptionKeyBase64);
      assert key.length == 32;
      client.setPreSharedKey(key, 0, key.length);

      // Set prologue
      byte[] prologue = "NoiseAPIInit".getBytes(StandardCharsets.US_ASCII);
      byte[] prologuePadded = new byte[prologue.length + 2]; // 2 nulls at the end
      System.arraycopy(prologue, 0, prologuePadded, 0, prologue.length);
      client.setPrologue(prologuePadded, 0, prologuePadded.length);

      client.start();

      connection.connect(espHomeAddress);
      state = NoiseProtocolState.HELLO;

      connection.send(createFrame(new byte[0]));

    } catch (NoSuchAlgorithmException e) {
      throw new ProtocolAPIError("Error initializing encryption", e);
    }
  }

  @Override
  protected void headerReceived(byte[] headerData) throws ProtocolException {
    try {
      if (headerData[0] != PROTOCOL_ENCRYPTED) {
        if (headerData[0] == PROTOCOL_PLAINTEXT) {
          handleAndClose(new RequiresEncryptionAPIError(DEVICE_REQUIRES_PLAINTEXT));
          return;
        } else {
          handleAndClose(new ProtocolAPIError(INVALID_PROTOCOL_PREAMBLE));
          return;
        }
      }

      // Unwrap outer frame
      int protoPacketLength = bytesToShort(Arrays.copyOfRange(headerData, 1, 3));
      if (internalBuffer.remaining() >= protoPacketLength) {
        byte[] packetData = readBytes(protoPacketLength);

        switch (state) {
          case HELLO:
            handleHello(packetData);
            break;
          case HANDSHAKE:
            handleHandshake(packetData);
            break;
          case READY:
            handleReady(packetData);
            break;
        }

        internalBuffer.compact();
        processBuffer();
      } else {
        internalBuffer.position(internalBuffer.limit());
        internalBuffer.limit(internalBuffer.capacity());
      }
    } catch (ShortBufferException e) {
      throw new ProtocolAPIError(e.getMessage());
    }
  }

  private void handleHello(byte[] packetData) throws ProtocolAPIError, ShortBufferException {
    if (packetData[0] != PROTOCOL_ENCRYPTED) {
      listener.onParseError(DEVICE_REQUIRES_PLAINTEXT);
    } else {
      // Verify server name
      byte[] serverName = Arrays.copyOfRange(packetData, 1, packetData.length - 1);
      String server = new String(serverName, StandardCharsets.US_ASCII);

      if (expectedServername != null && !expectedServername.equals(server)) {
        listener.onParseError(DEVICE_NAME_MISMATCH);
        return;
      }

      final byte[] noiseHandshakeBuffer = new byte[64];
      final int noiseHandshakeLength;

      // Client handshake written to buffer
      noiseHandshakeLength = client.writeMessage(noiseHandshakeBuffer, 0, new byte[0], 0, 0);

      // Prepend with empty byte in array
      byte[] payload = new byte[noiseHandshakeLength + 1];
      System.arraycopy(noiseHandshakeBuffer, 0, payload, 1, noiseHandshakeLength);

      ByteBuffer frame = createFrame(payload);
      state = NoiseProtocolState.HANDSHAKE;
      connection.send(frame);
    }
  }

  private ByteBuffer createFrame(byte[] payload) {
    int frameLength = payload.length;
    ByteBuffer buffer = ByteBuffer.allocate(frameLength + 3);
    buffer.put((byte) 1);
    buffer.putShort((short) frameLength);
    buffer.put(payload);
    buffer.flip();

    return buffer;
  }

  private void handleHandshake(byte[] packetData) throws ProtocolException {
    if (packetData[0] != 0) {
      byte[] explanation = Arrays.copyOfRange(packetData, 1, packetData.length);
      listener.onParseError(ENCRYPTION_KEY_INVALID);
    } else {
      try {
        byte[] handshakeRsp = Arrays.copyOfRange(packetData, 1, packetData.length);
        byte[] payload = new byte[64];
        client.readMessage(handshakeRsp, 0, handshakeRsp.length, payload, 0);

        cipherStatePair = client.split();
        state = NoiseProtocolState.READY;
        listener.onConnect();
      } catch (ShortBufferException | BadPaddingException e) {
        throw new ProtocolAPIError(e.getMessage());
      }
    }
  }

  private void handleReady(byte[] packetData) {
    try {
      byte[] decrypted = decryptPacket(packetData);
      int messageType = (decrypted[0] << 8) | decrypted[1];
      byte[] messageData = Arrays.copyOfRange(decrypted, 4, decrypted.length);
      decodeProtoMessage(messageType, messageData);
    } catch (Exception e) {
      listener.onParseError(PACKET_ERROR);
    }
  }

  public ByteBuffer encodeFrame(GeneratedMessage message) throws ProtocolAPIError {
    try {
      byte[] protoBytes = message.toByteArray();
      int type = message.getDescriptorForType().getOptions().getExtension(ApiOptions.id);
      byte[] typeAndLength = new byte[]{(byte) (type >> 8 & 0xFF), (byte) (type & 0xFF),
        (byte) (protoBytes.length >> 8 & 0xFF), (byte) (protoBytes.length & 0xFF)};
      byte[] frameUnencrypted = concatArrays(typeAndLength, protoBytes);
      byte[] frameEncrypted = encryptPacket(frameUnencrypted);

      return createFrame(frameEncrypted);

    } catch (Exception e) {
      throw new ProtocolAPIError(e.getMessage());
    }
  }

  private byte[] encryptPacket(byte[] msg) throws Exception {
    byte[] encrypted = new byte[msg.length + 128];
    final int cipherTextLength = cipherStatePair.getSender().encryptWithAd(null, msg, 0, encrypted, 0, msg.length);

    byte[] result = new byte[cipherTextLength];
    System.arraycopy(encrypted, 0, result, 0, cipherTextLength);
    return result;
  }

  private byte[] decryptPacket(byte[] msg) throws Exception {
    byte[] decrypted = new byte[msg.length + 128];
    final int cipherTextLength = cipherStatePair.getReceiver().decryptWithAd(null, msg, 0, decrypted, 0,
      msg.length);

    byte[] result = new byte[cipherTextLength];
    System.arraycopy(decrypted, 0, result, 0, cipherTextLength);
    return result;
  }

  private enum NoiseProtocolState {
    HELLO,
    HANDSHAKE,
    READY
  }
}
