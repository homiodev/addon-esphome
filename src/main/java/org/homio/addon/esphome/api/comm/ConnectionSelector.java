package org.homio.addon.esphome.api.comm;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class ConnectionSelector {

  public static final int READ_BUFFER_SIZE = 2048;

  private final Selector selector;

  private boolean keepRunning = true;

  private boolean selectorOpen;
  private final Map<SocketChannel, StreamHandler> connectionMap = new ConcurrentHashMap<>();

  public ConnectionSelector() throws IOException {
    selector = Selector.open();
    selectorOpen = true;
  }

  public void start() {

    Thread selectorThread = new Thread(() -> {
      log.debug("Starting selector thread");
      while (keepRunning) {
        try {
          selector.select(1000);
          // token representing the registration of a SelectableChannel with a Selector
          Set<SelectionKey> keys = selector.selectedKeys();
          log.trace("Selected keys: {}", keys.size());
          Iterator<SelectionKey> keyIterator = keys.iterator();
          while (keyIterator.hasNext()) {
            SelectionKey readyKey = keyIterator.next();
            processKey(readyKey);
            keyIterator.remove();
          }
        } catch (ClosedSelectorException e) {
          log.debug("Selector closed");
          keepRunning = false;
        } catch (Exception e) {
          log.warn("Error while selecting", e);
          keepRunning = false;
        }
      }
      log.debug(
        "Selector thread stopped. This should only happen on bundle stop, not during regular operation. See previous log statements for more "
        + "information.");
    });
    selectorThread.setName("ESPHome Reader");
    selectorThread.start();
  }

  public void stop() {
    if (selectorOpen) {
      keepRunning = false;
      selector.wakeup();
      try {
        selector.close();
      } catch (IOException e) {
        log.debug("Error closing selector", e);
      }
      selectorOpen = false;
    }
  }

  public void register(SocketChannel socketChannel, StreamHandler packetStreamReader) {
    connectionMap.put(socketChannel, packetStreamReader);
    try {
      SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
      key.attach(packetStreamReader);
      selector.wakeup();
    } catch (IOException e) {
      log.warn("Error while registering channel", e);
    }
  }

  public void unregister(SocketChannel socketChannel) {
    connectionMap.remove(socketChannel);

    try {
      socketChannel.close();
    } catch (IOException e) {
      log.warn("Error while closing channel", e);
    }
  }

  private void processKey(SelectionKey readyKey) {
    StreamHandler streamHandler = (StreamHandler) readyKey.attachment();
    log.trace("Processing key {}", readyKey);
    // Tests whether this key's channel is ready to accept a new socket connection
    try {
      if (readyKey.isReadable()) {
        SocketChannel channel = (SocketChannel) readyKey.channel();
        ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        int read = channel.read(buffer);
        if (read == -1) {
          log.debug("End of stream, closing");
          channel.keyFor(selector).cancel();
          streamHandler.endOfStream("No more bytes available in connection stream");
        } else {
          if (read == READ_BUFFER_SIZE) {
            log.warn(
              "Socket read provided more data than buffer capacity of {}. Buffer capacity should be increased. Things still work, but performance is suboptimal. File a report on github to the developer",
              READ_BUFFER_SIZE);
          }
          processReceivedData(streamHandler, buffer, channel);
        }

      } else {
        log.trace("Key not readable");
      }
    } catch (IOException e) {
      log.debug("Socket exception", e);
      streamHandler.endOfStream(e.getMessage());
    }
  }

  private void processReceivedData(StreamHandler streamHandler, ByteBuffer buffer, SocketChannel channel)
    throws IOException {
    try {
      log.trace("Received data");
      streamHandler.processReceivedData(buffer);
    } catch (Exception e) {
      channel.close();
      streamHandler.onParseError(e);
    }
  }
}
