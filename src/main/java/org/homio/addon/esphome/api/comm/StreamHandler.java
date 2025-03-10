package org.homio.addon.esphome.api.comm;

import com.google.protobuf.GeneratedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.api.CommunicationListener;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

@Log4j2
@RequiredArgsConstructor
public abstract class StreamHandler {

  public static final int PROTOCOL_PLAINTEXT = 0x00;
  public static final int PROTOCOL_ENCRYPTED = 0x01;
  protected static final String ENCRYPTION_KEY_INVALID = "Invalid api encryption key";
  protected static final String DEVICE_NAME_MISMATCH = "ESPHome device reported a different esphome.name than configured for the thing";
  protected static final String DEVICE_REQUIRES_PLAINTEXT = "Device is configured with plaintext api endpoint, but binding is using encryption.";
  protected static final String DEVICE_REQUIRES_ENCRYPTION = "Device is configured with encrypted api endpoint, but binding isn't using encryption.";
  protected static final String INVALID_PROTOCOL_PREAMBLE = "Invalid protocol preamble - this indicates a new major protocol change has arrived, but this binding does not support it yet";
  protected static final String PACKET_ERROR = "Error parsing packet";
  protected final ByteBuffer internalBuffer = ByteBuffer.allocate(1024);
  protected final MessageTypeToClassConverter messageTypeToClassConverter = new MessageTypeToClassConverter();

  protected final CommunicationListener listener;
  protected ESPHomeConnection connection;

  protected static byte[] concatArrays(byte[] length, byte[] additionalLength) {
    byte[] result = new byte[length.length + additionalLength.length];
    System.arraycopy(length, 0, result, 0, length.length);
    System.arraycopy(additionalLength, 0, result, length.length, additionalLength.length);
    return result;
  }

  public void processReceivedData(ByteBuffer newDataBuffer) throws ProtocolException, IOException {
    // Copy new data into buffer
    newDataBuffer.flip();
    internalBuffer.put(newDataBuffer);
    processBuffer();
  }

  protected void processBuffer() throws ProtocolException {
    internalBuffer.limit(internalBuffer.position());
    internalBuffer.position(0);
    if (internalBuffer.remaining() > 2) {
      byte[] headerData = readBytes(3);
      headerReceived(headerData);
    } else {
      internalBuffer.position(internalBuffer.limit());
      internalBuffer.limit(internalBuffer.capacity());
    }
  }

  protected byte[] readBytes(int numBytes) {
    if (internalBuffer.remaining() < numBytes) {
      return new byte[0];
    }
    byte[] data = new byte[numBytes];
    internalBuffer.get(data);
    return data;
  }

  protected abstract void headerReceived(byte[] headerData) throws ProtocolException;

  abstract ByteBuffer encodeFrame(GeneratedMessage message) throws ProtocolAPIError;

  public void endOfStream(String message) {
    listener.onEndOfStream(message);
  }

  public void onParseError(Exception e) {
    log.error("Error parsing packet", e);
    listener.onParseError(e.getMessage());
  }

  public abstract void connect(InetSocketAddress espHomeAddress) throws ProtocolException;

  public void send(GeneratedMessage message) throws ProtocolAPIError {
    try {
      if (connection != null) {
        connection.send(encodeFrame(message));
      } else {
        log.debug("Connection is null, cannot send message");
      }
    } catch (ProtocolAPIError e) {
      log.warn("Error sending message", e);
    }
  }

  public void close() {
    if (connection != null) {
      connection.close();
    }
  }

  protected void handleAndClose(ProtocolException ex) throws ProtocolException {
    // close socket
    listener.onParseError(ex.getMessage());
    throw ex;
  }

  protected void decodeProtoMessage(int messageType, byte[] bytes) {
    log.debug("Received packet of type {} with data {}", messageType, bytes);

    try {
      Method parseMethod = messageTypeToClassConverter.getMethod(messageType);
      if (parseMethod != null) {
        GeneratedMessage invoke = (GeneratedMessage) parseMethod.invoke(null, bytes);
        if (invoke != null) {
          listener.onPacket(invoke);
        } else {
          log.warn("Received null packet of type {}", parseMethod);
        }
      }
    } catch (Exception e) {
      log.warn("Error parsing packet", e);
      listener.onParseError(PACKET_ERROR);
    }
  }
}
