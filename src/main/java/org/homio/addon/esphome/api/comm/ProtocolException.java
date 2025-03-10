package org.homio.addon.esphome.api.comm;

public abstract class ProtocolException extends Exception {

  protected ProtocolException(String message) {
    super(message);
  }

  protected ProtocolException(String message, Throwable e) {
    super(message, e);
  }
}
