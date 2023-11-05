package org.homio.addon.esphome.api.comm;

import com.google.protobuf.GeneratedMessageV3;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ESPHomeConnection {

    private final ConnectionSelector connectionSelector;
    private SocketChannel socketChannel;
    private StreamHandler streamHandler;
    private String hostname;

    public ESPHomeConnection(ConnectionSelector connectionSelector, PlainTextStreamHandler streamHandler,
        String hostname) {
        this.streamHandler = streamHandler;
        this.connectionSelector = connectionSelector;
        this.hostname = hostname;
    }

    public synchronized void send(GeneratedMessageV3 message) throws ProtocolAPIError {
        try {
            log.debug("[{}] Sending message: {}", hostname, message.getClass().getSimpleName());
            byte[] serializedMessage = streamHandler.encodeFrame(message);
            ByteBuffer buffer = ByteBuffer.wrap(serializedMessage);
            while (buffer.hasRemaining()) {
                log.trace("Writing data");
                socketChannel.write(buffer);
            }
        } catch (IOException e) {
            throw new ProtocolAPIError(String.format("[%s] Error sending message: %s ", hostname, e));
        }
    }

    public void connect(InetSocketAddress address) throws ProtocolAPIError {
        try {

            socketChannel = SocketChannel.open(address);
            socketChannel.configureBlocking(false);
            connectionSelector.register(socketChannel, streamHandler);

            log.info("[{}] Opening socket to {} at port {}.", hostname, hostname, address.getPort());

        } catch (Exception e) {
            throw new ProtocolAPIError("Failed to connect to '" + hostname + "' port " + address.getPort(), e);
        }
    }

    public void close() {
        log.info("[{}] Disconnecting socket.", hostname);
        try {
            if (socketChannel != null) {
                connectionSelector.unregister(socketChannel);
                socketChannel.close();
                socketChannel = null;
            }
        } catch (IOException e) {
            log.debug("[{}] Error closing connection", hostname, e);
        }
    }
}
