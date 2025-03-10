package org.homio.addon.esphome.api;

import com.google.protobuf.GeneratedMessage;
import org.homio.addon.esphome.api.comm.ProtocolAPIError;

import java.io.IOException;

public interface CommunicationListener {

  void onPacket(GeneratedMessage message) throws ProtocolAPIError, IOException;

  void onEndOfStream(String message);

  void onParseError(String message);

  void onConnect() throws ProtocolAPIError;
}
