package org.homio.addon.esphome.api;

import com.google.protobuf.GeneratedMessageV3;
import java.io.IOException;
import org.homio.addon.esphome.api.comm.ProtocolAPIError;

public interface PacketListener {

    void onPacket(GeneratedMessageV3 message) throws ProtocolAPIError, IOException;

    void onEndOfStream();

    void onParseError();
}
