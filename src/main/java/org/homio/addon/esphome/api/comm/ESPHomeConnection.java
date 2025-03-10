package org.homio.addon.esphome.api.comm;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

@Log4j2
@RequiredArgsConstructor
public class ESPHomeConnection {

  private final ConnectionSelector connectionSelector;
  private final StreamHandler streamHandler;
  private final String hostname;

  private SocketChannel socketChannel;

  public synchronized void send(ByteBuffer buffer) throws ProtocolAPIError {
    if (socketChannel != null) {
      try {
        while (buffer.hasRemaining()) {
          log.trace("Writing data {} bytes", buffer.remaining());
          socketChannel.write(buffer);
        }

      } catch (IOException e) {
        throw new ProtocolAPIError(String.format("Error sending message: %s ", e));
      }
    } else {
      log.warn("Attempted to send data on a closed connection");
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
