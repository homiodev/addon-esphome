package org.homio.addon.esphome.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.addon.esphome.ESPHomeFrontendConsolePlugin;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.addon.esphome.entity.ESPHomeProjectEntity;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ProcessContext;
import org.homio.api.ContextInstall.PythonEnv;
import org.homio.api.ContextService.MQTTEntityService;
import org.homio.api.console.ConsolePluginFrame.FrameConfiguration;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.homio.addon.esphome.ESPHomeEntrypoint.ESPHOME_COLOR;
import static org.homio.addon.esphome.ESPHomeEntrypoint.ESPHOME_ICON;

@Getter
public class ESPHomeProjectService extends ServiceInstance<ESPHomeProjectEntity> {

  public static ESPHomeProjectService INSTANCE;

  private MQTTEntityService mqttEntityService;
  private Map<String, ESPHomeDeviceEntity> existedDevices = new HashMap<>();
  private ProcessContext processContext;
  private String version;

  public ESPHomeProjectService(@NotNull Context context, @NotNull ESPHomeProjectEntity entity) {
    super(context, entity, true, "ESPHome project");
    INSTANCE = this;
    mqttEntityService = entity.getMqttEntityService();
  }

  @NotNull
  public static Set<String> buildTopics(@NotNull List<String> topicList, String... topics) {
    Set<String> result = new HashSet<>();
    for (String tl : topicList) {
      result.add(tl.endsWith("/#") ? tl : tl + "/#");
    }
    for (String topic : topics) {
      result.add(topic.endsWith("/#") ? topic : topic + "/#");
    }
    return result;
  }

  @NotNull
  private static String getMacAddress(@NotNull String key, @NotNull JsonNode payload) {
    String unformattedMAC = payload.get(key).asText();
    return unformattedMAC.toUpperCase().replaceAll("(.{2})", "$1:").substring(0, 17);
  }

  public void dispose(@Nullable Exception ignore) {
    updateNotificationBlock();
  }

  @Override
  public void destroy(boolean forRestart, Exception ex) {
    this.dispose(ex);
    context.service().unRegisterUrlProxy("esphome");
    context.ui().console().unRegisterPlugin("esphome");
    mqttEntityService.removeListener("esphome-discovery");
  }

  @Override
  protected void initialize() {
    mqttEntityService = entity.getMqttEntityService();
    Set<String> topics = buildTopics(entity.getDiscoveryPrefix(), "esphome", "homeassistant");
    mqttEntityService.addPayloadListener(topics, "esphome-discovery", entityID, log,
      (topic, payload) -> handleDiscovery(payload));

    String url = context.service().registerUrlProxy("esphome", "http://localhost:6052", builder -> {
    });
    context.ui().console().registerPlugin("esphome",
      new ESPHomeFrontendConsolePlugin(context, new FrameConfiguration(url)));
  }

  @Override
  @SneakyThrows
  protected void firstInitialize() {
    context.var().createGroup("esphome", "ESPHome", builder ->
      builder.setLocked(true).setIcon(new Icon(ESPHOME_ICON, ESPHOME_COLOR)));

    existedDevices = context.db().findAll(ESPHomeDeviceEntity.class)
      .stream()
      .collect(Collectors.toMap(ESPHomeDeviceEntity::getIeeeAddress, t -> t));
    PythonEnv env = context.install().pythonEnv("esphome");
    // every run update to latest release version
    env.install("-U esphome");
    env.install("tornado esptool pillow");
    Path configPath = env.createDirectory(Paths.get("config"));
    Path espHome = env.getScript(Paths.get("esphome.exe"));
    version = context.hardware().execute(espHome + " version");
    if (version.startsWith("Version:")) {
      version = version.substring("Version:".length()).trim();
    }

    processContext = context.bgp().processBuilder(entity, log)
      .execute("%s dashboard %s".formatted(espHome, configPath));

    initialize();
  }

  private synchronized void handleDiscovery(ObjectNode payload) {
    if (payload.has("mac")) {
      discoveryBoard(payload);
    } else if (payload.has("dev")) {
      discoveryDeviceEntity(payload);
    }
  }

  private void discoveryDeviceEntity(ObjectNode payload) {
    JsonNode boardInfo = payload.get("dev");
    String mac = getMacAddress("ids", boardInfo);

    ESPHomeDeviceEntity device = new ESPHomeDeviceEntity();
    if (existedDevices.containsKey(mac)) {
      device = existedDevices.get(mac);
    } else {
      device.setIeeeAddress(mac);
      device.setName(boardInfo.get("name").asText());
      existedDevices.put(device.getDeviceIpAddress(), context.db().save(device));
    }
    CommunicatorService apiService = device.getService().getApiService();
    if (apiService instanceof ESPHomeMQTTApiService service) {
      service.addDevice(payload);
    } else {
      log.error("[{}]: Unable to register ESPHome device because no MQTT communicator: {}", entityID, payload);
    }
  }

  private void discoveryBoard(ObjectNode payload) {
    String mac = getMacAddress("mac", payload);
    ESPHomeDeviceEntity device = new ESPHomeDeviceEntity();
    if (existedDevices.containsKey(mac)) {
      device = existedDevices.get(mac);
    }
    if (device.tryUpdate(mac, payload)) {
      existedDevices.put(device.getDeviceIpAddress(), context.db().save(device));
    }
  }
}
