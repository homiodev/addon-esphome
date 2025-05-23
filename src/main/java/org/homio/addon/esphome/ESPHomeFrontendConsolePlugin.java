package org.homio.addon.esphome;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.homio.api.Context;
import org.homio.api.console.ConsolePluginFrame;

@Getter
@RequiredArgsConstructor
public class ESPHomeFrontendConsolePlugin implements ConsolePluginFrame {

  private final @Accessors(fluent = true) Context context;
  private final FrameConfiguration value;

  @Override
  public int order() {
    return 500;
  }

  @Override
  public String getParentTab() {
    return "esphome";
  }
}
