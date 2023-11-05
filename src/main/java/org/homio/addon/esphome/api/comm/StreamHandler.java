package org.homio.addon.esphome.api.comm;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.google.protobuf.GeneratedMessageV3;

public interface StreamHandler {

    void processReceivedData(ByteBuffer buffer) throws ProtocolException, IOException;

    byte[] encodeFrame(GeneratedMessageV3 message);

    void endOfStream();

    void onParseError(Exception e);
}
