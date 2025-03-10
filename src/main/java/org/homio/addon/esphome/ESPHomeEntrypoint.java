package org.homio.addon.esphome;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.addon.esphome.entity.ESPHomeProjectEntity;
import org.homio.api.AddonConfiguration;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.net.URL;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Log4j2
@Component
@AddonConfiguration
@RequiredArgsConstructor
public class ESPHomeEntrypoint implements AddonEntrypoint {

  public static final String ESPHOME_ICON = "fas fa-microchip";
  public static final String ESPHOME_COLOR = "#494D4F";
  private final Context context;

  @Override
  public void init() {
    ensureEntityExists(context);
    context.ui().console().registerPluginName("esphome");
    context.setting().listenValue(ESPHomeCompactModeSetting.class, "esphome-compact-mode",
      (value) -> context.ui().updateItems(ESPHomeDeviceEntity.class));
  }

  @SneakyThrows
  public @NotNull URL getAddonImageURL() {
    return getResource("images/esphome.png");
  }

  public void ensureEntityExists(Context context) {
    ESPHomeProjectEntity entity = context.db().get(ESPHomeProjectEntity.class, PRIMARY_DEVICE);
    if (entity == null) {
      entity = new ESPHomeProjectEntity();
      entity.setEntityID(PRIMARY_DEVICE);
      entity.setName("ESPHome");
      entity.setMqttEntity(context.service().getPrimaryMqttEntity());
      context.db().save(entity, false);
    }
  }
}
