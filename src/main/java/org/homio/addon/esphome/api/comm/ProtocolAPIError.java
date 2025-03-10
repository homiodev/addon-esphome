package org.homio.addon.esphome.api.comm;

public class ProtocolAPIError extends ProtocolException {

  public ProtocolAPIError(String message) {
    super(message);
  }

  public ProtocolAPIError(String message, Throwable e) {
    super(message, e);
  }
}
