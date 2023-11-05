package org.homio.addon.esphome.api.comm;

import static org.homio.addon.esphome.api.comm.VarIntConverter.bytesToInt;

import com.google.protobuf.GeneratedMessageV3;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.api.PacketListener;

@Log4j2
public class PlainTextStreamHandler implements StreamHandler {

    public static final int PREAMBLE = 0x00;
    public static final int ENCRYPTION_REQUIRED = 0x01;
    public static final int VAR_INT_MARKER = 0x80;

    private final PacketListener listener;

    private ByteBuffer buffer = ByteBuffer.allocate(1024);

    private MessageTypeToClassConverter messageTypeToClassConverter = new MessageTypeToClassConverter();

    public PlainTextStreamHandler(PacketListener listener) {
        this.listener = listener;
    }

    public void headerReceived(byte[] headerData) throws ProtocolException {
        if (headerData[0] != PREAMBLE) {
            if (headerData[0] == ENCRYPTION_REQUIRED) {
                handleAndClose(new RequiresEncryptionAPIError("Connection requires encryption"));
                return;
            }

            handleAndClose(new ProtocolAPIError(String.format("Invalid preamble %02x", headerData[0])));
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
                buffer.position(buffer.limit());
                buffer.limit(buffer.capacity());
                return;
            }
            encodedProtoPacketLenghtBuffer = concatArrays(encodedProtoPacketLenghtBuffer, additionalLength);
        }

        // If the message length was longer than 1 byte, we need to read the message type
        while (encodedMessageTypeBuffer.length == 0
            || (encodedMessageTypeBuffer[encodedMessageTypeBuffer.length - 1] & VAR_INT_MARKER) == VAR_INT_MARKER) {
            byte[] additionalencodedMessageTypeBuffer = readBytes(1);
            if (additionalencodedMessageTypeBuffer.length == 0) {
                buffer.position(buffer.limit());
                buffer.limit(buffer.capacity());
                return;
            }
            encodedMessageTypeBuffer = concatArrays(encodedMessageTypeBuffer, additionalencodedMessageTypeBuffer);
        }

        Integer protoPacketLength = bytesToInt(encodedProtoPacketLenghtBuffer);
        Integer messageType = bytesToInt(encodedMessageTypeBuffer);

        if (protoPacketLength == 0) {
            decodeProtoMessage(messageType, new byte[0]);
            buffer.compact();
            // If we have more data, continue processing
            processBuffer();

        } else if (buffer.remaining() >= protoPacketLength) {
            // We have enough data in the buffer to read the whole packet
            byte[] packetData = readBytes(protoPacketLength);
            decodeProtoMessage(messageType, packetData);
            buffer.compact();
            processBuffer();
        } else {
            buffer.position(buffer.limit());
            buffer.limit(buffer.capacity());
        }
    }

    public void processReceivedData(ByteBuffer buffer) throws ProtocolException, IOException {
        // Copy new data into buffer
        byte[] newData = new byte[buffer.position()];
        buffer.flip();
        buffer.get(newData);
        this.buffer.put(newData, 0, newData.length);

        processBuffer();
    }

    public byte[] encodeFrame(GeneratedMessageV3 message) {
        byte[] protoBytes = message.toByteArray();
        byte[] idVarUint = VarIntConverter
            .intToBytes(message.getDescriptorForType().getOptions().getExtension(io.esphome.api.ApiOptions.id));
        byte[] protoBytesLengthVarUint = VarIntConverter.intToBytes(protoBytes.length);

        byte[] frame = new byte[1 + idVarUint.length + protoBytesLengthVarUint.length + protoBytes.length];
        System.arraycopy(protoBytesLengthVarUint, 0, frame, 1, protoBytesLengthVarUint.length);
        System.arraycopy(idVarUint, 0, frame, protoBytesLengthVarUint.length + 1, idVarUint.length);
        System.arraycopy(protoBytes, 0, frame, idVarUint.length + protoBytesLengthVarUint.length + 1,
            protoBytes.length);
        return frame;
    }

    @Override
    public void endOfStream() {
        listener.onEndOfStream();
    }

    @Override
    public void onParseError(Exception e) {
        log.error("Error parsing packet", e);
        listener.onParseError();
    }

    private void processBuffer() throws ProtocolException {
        buffer.limit(buffer.position());
        buffer.position(0);
        if (buffer.remaining() > 2) {
            byte[] headerData = readBytes(3);
            headerReceived(headerData);
        } else {
            buffer.position(buffer.limit());
            buffer.limit(buffer.capacity());
        }
    }

    private byte[] readBytes(int numBytes) {
        if (buffer.remaining() < numBytes) {
            return new byte[0];
        }
        byte[] data = new byte[numBytes];
        buffer.get(data);
        return data;
    }

    private void decodeProtoMessage(int messageType, byte[] bytes) {
        log.debug("Received packet of type {} with data {}", messageType, bytes);

        try {
            Method parseMethod = messageTypeToClassConverter.getMethod(messageType);
            if (parseMethod != null) {
                GeneratedMessageV3 invoke = (GeneratedMessageV3) parseMethod.invoke(null, bytes);
                if (invoke != null) {
                    listener.onPacket(invoke);
                } else {
                    log.warn("Received null packet of type {}", parseMethod);
                }
            }
        } catch (ProtocolAPIError | IllegalAccessException | InvocationTargetException | IOException e) {
            log.warn("Error parsing packet", e);
            listener.onParseError();
        }
    }

    private byte[] concatArrays(byte[] length, byte[] additionalLength) {
        byte[] result = new byte[length.length + additionalLength.length];
        System.arraycopy(length, PREAMBLE, result, PREAMBLE, length.length);
        System.arraycopy(additionalLength, PREAMBLE, result, length.length, additionalLength.length);
        return result;
    }

    private void handleAndClose(ProtocolException connectionRequiresEncryption) throws ProtocolException {
        // close socket
        listener.onParseError();
        throw connectionRequiresEncryption;
    }
}
