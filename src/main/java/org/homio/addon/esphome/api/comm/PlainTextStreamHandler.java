package org.homio.addon.esphome.api.comm;

import com.google.protobuf.GeneratedMessage;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.api.CommunicationListener;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.homio.addon.esphome.api.comm.VarIntConverter.bytesToInt;

@Log4j2
public class PlainTextStreamHandler extends StreamHandler {

  public static final int VAR_INT_MARKER = 0x80;

  public PlainTextStreamHandler(ConnectionSelector connectionSelector, CommunicationListener listener, String ipAddress) {
    super(listener);
    this.connection = new ESPHomeConnection(connectionSelector, this, ipAddress);
  }

  @Override
  protected void headerReceived(byte[] headerData) throws ProtocolException {
    if (headerData[0] != PROTOCOL_PLAINTEXT) {
      if (headerData[0] == PROTOCOL_ENCRYPTED) {
        handleAndClose(new RequiresEncryptionAPIError(DEVICE_REQUIRES_ENCRYPTION));
        return;
      }
      handleAndClose(new ProtocolAPIError(INVALID_PROTOCOL_PREAMBLE));
      return;
    }

    byte[] encodedProtoPacketLenghtBuffer;
    byte[] encodedMessageTypeBuffer;

    if ((headerData[1] & VAR_INT_MARKER) == VAR_INT_MARKER) {
      // Length is longer than 1 byte
      encodedProtoPacketLenghtBuffer = Arrays.copyOfRange(headerData, 1, 3);
      encodedMessageTypeBuffer = new byte[0];
    } else {
      // This is the most common case with 99% of messages
      // needing a single byte for length and type which means
      // we avoid 2 calls to readexactly
      encodedProtoPacketLenghtBuffer = Arrays.copyOfRange(headerData, 1, 2);
      encodedMessageTypeBuffer = Arrays.copyOfRange(headerData, 2, 3);
    }

    // If the message is long, we need to read the rest of the length
    while ((encodedProtoPacketLenghtBuffer[encodedProtoPacketLenghtBuffer.length - 1]
            & VAR_INT_MARKER) == VAR_INT_MARKER) {
      byte[] additionalLength = readBytes(1);
      if (additionalLength.length == 0) {
        internalBuffer.position(internalBuffer.limit());
        internalBuffer.limit(internalBuffer.capacity());
        return;
      }
      encodedProtoPacketLenghtBuffer = concatArrays(encodedProtoPacketLenghtBuffer, additionalLength);
    }

    // If the message length was longer than 1 byte, we need to read the message type
    while (encodedMessageTypeBuffer.length == 0
           || (encodedMessageTypeBuffer[encodedMessageTypeBuffer.length - 1] & VAR_INT_MARKER) == VAR_INT_MARKER) {
      byte[] additionalencodedMessageTypeBuffer = readBytes(1);
      if (additionalencodedMessageTypeBuffer.length == 0) {
        internalBuffer.position(internalBuffer.limit());
        internalBuffer.limit(internalBuffer.capacity());
        return;
      }
      encodedMessageTypeBuffer = concatArrays(encodedMessageTypeBuffer, additionalencodedMessageTypeBuffer);
    }

    Integer protoPacketLength = bytesToInt(encodedProtoPacketLenghtBuffer);
    Integer messageType = bytesToInt(encodedMessageTypeBuffer);

    if (protoPacketLength == 0) {
      decodeProtoMessage(messageType, new byte[0]);
      internalBuffer.compact();
      // If we have more data, continue processing
      processBuffer();

    } else if (internalBuffer.remaining() >= protoPacketLength) {
      // We have enough data in the buffer to read the whole packet
      byte[] packetData = readBytes(protoPacketLength);
      decodeProtoMessage(messageType, packetData);
      internalBuffer.compact();
      processBuffer();
    } else {
      internalBuffer.position(internalBuffer.limit());
      internalBuffer.limit(internalBuffer.capacity());
    }
  }

  public ByteBuffer encodeFrame(GeneratedMessage message) {
    byte[] protoBytes = message.toByteArray();
    byte[] idVarUint = VarIntConverter
      .intToBytes(message.getDescriptorForType().getOptions().getExtension(io.esphome.api.ApiOptions.id));
    byte[] protoBytesLengthVarUint = VarIntConverter.intToBytes(protoBytes.length);

    byte[] frame = new byte[1 + idVarUint.length + protoBytesLengthVarUint.length + protoBytes.length];
    System.arraycopy(protoBytesLengthVarUint, 0, frame, 1, protoBytesLengthVarUint.length);
    System.arraycopy(idVarUint, 0, frame, protoBytesLengthVarUint.length + 1, idVarUint.length);
    System.arraycopy(protoBytes, 0, frame, idVarUint.length + protoBytesLengthVarUint.length + 1,
      protoBytes.length);
    return ByteBuffer.wrap(frame);
  }

  @Override
  public void connect(InetSocketAddress espHomeAddress) throws ProtocolException {
    connection.connect(espHomeAddress);
    listener.onConnect();
  }
}
