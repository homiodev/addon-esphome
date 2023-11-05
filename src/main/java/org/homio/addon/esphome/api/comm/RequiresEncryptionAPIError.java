package org.homio.addon.esphome.api.comm;

public class RequiresEncryptionAPIError extends ProtocolException {

    public RequiresEncryptionAPIError(String message) {
        super(message);
    }
}
