package org.homio.addon.esphome.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.esphome.ESPHomeEndpoint;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.state.DecimalType;
import org.homio.hquery.ProgressBar;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.homio.addon.esphome.service.ESPHomeProjectService.buildTopics;
import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public class ESPHomeMQTTApiService implements CommunicatorService {

  private final ESPHomeDeviceEntity entity;
  private final String entityID;
  private final Context context;
  private final ESPHomeDeviceService service;

  private final @Getter ObjectNode attributes = OBJECT_MAPPER.createObjectNode();
  private final Map<String, ESPHomeEndpoint> endpointToTopic = new ConcurrentHashMap<>();
  private ProgressBar otaUpdateProgressBar;

  public ESPHomeMQTTApiService(ESPHomeDeviceService service) {
    this.service = service;
    this.context = service.context();
    this.entity = service.getEntity();
    this.entityID = entity.getEntityID();
  }

  @Override
  public void initialize() {
    initializeMQTT();

    context.bgp().ping(entity.getIeeeAddress(), entity.getDeviceIpAddress(), available ->
      service.setDeviceStatus(available ? ONLINE : OFFLINE, null));
  }

  @Override
  public void destroy() {
    ESPHomeProjectService.INSTANCE.getMqttEntityService().removeListener("esphome-" + entityID);
    context.bgp().unPing(entity.getIeeeAddress(), null);
  }

  public void addDevice(ObjectNode payload) {
    String id = payload.path("uniq_id").asText();
    String name = payload.path("name").asText();
    if (StringUtils.isEmpty(id)) {
      id = name;
    }
    String deviceTopic = payload.get("stat_t").asText();
    ESPHomeEndpoint endpoint = new ESPHomeEndpoint(id, EndpointType.number, entity, builder -> {
      builder.setStatusClass(payload.path("stat_cla").asText());
      builder.setDeviceClass(payload.path("dev_cla").asText());
      builder.setUnit(payload.path("unit_of_meas").asText());
      builder.setMin(findNumber(payload, "min", "min_hum", "min_temp", "min_mirs"));
      builder.setMax(findNumber(payload, "max", "max_hum", "max_temp", "max_mirs"));
      if ("temperature".equals(builder.getDeviceClass())) {
        builder.setIcon(new Icon("fas fa-temperature-three-quarters", "#429DC4"));
      }
      builder.setDescription(name);
      builder.setDataReader(jsonNode -> new DecimalType(jsonNode.get("raw").asDouble()));
    });
    String value = ESPHomeProjectService.INSTANCE.getMqttEntityService().getLastValue(deviceTopic);
    if (value != null) {
      endpoint.mqttUpdate(OBJECT_MAPPER.createObjectNode().put("raw", value));
    }
    service.getEndpoints().put(id, endpoint);
    endpointToTopic.put(deviceTopic, endpoint);
  }

  public void put(String key, String value) {
    if (key.equals("LWT")) {
      service.setDeviceStatus("Online".equals(value) ? Status.ONLINE : Status.OFFLINE, null);
    }
    attributes.put(key, value);
  }

  private void initializeMQTT() {
    Set<String> topics = buildTopics(List.of(entity.getTopicPrefix(), entity.getLogPrefix()));
    ESPHomeProjectService.INSTANCE.getMqttEntityService().addPayloadListener(
      topics, "esphome-" + entity.getIeeeAddress(), entityID, log, (topic, payload) -> {
        service.updateLastSeen();
        ESPHomeEndpoint endpoint = endpointToTopic.get(topic);
        if (endpoint != null) {
          endpoint.mqttUpdate(payload);
          return;
        }
        String[] items = topic.split("/");
        String entry = items[items.length - 1];
        switch (entry) {
          case "debug":
            handleDebug(payload);
            break;
          case "status":
            service.setDeviceStatus(payload.get("raw").asText().equals("online") ? ONLINE : OFFLINE, null);
            break;
          default:
            log.warn("Unhandled entry: {}. Payload: {}", entry, payload);
        }
      });
  }

  private void handleDebug(ObjectNode payload) {
    String debugText = payload.get("raw").asText().replaceAll("\u001B\\[[;\\d]*m*", "");
    if (debugText.contains("Starting OTA Update")) {
      otaUpdateProgressBar = context.ui().progress().createProgressBar(entityID, false);
      otaUpdateProgressBar.progress(1, debugText);
    } else if (debugText.contains("OTA in progress")) {
      Pattern pattern = Pattern.compile("\\d+\\.\\d+");
      Matcher matcher = pattern.matcher(debugText);
      if (matcher.find()) {
        otaUpdateProgressBar.progress(Double.parseDouble(matcher.group()), "OTA in progress");
      }
    } else if (debugText.contains("OTA update finished")) {
      otaUpdateProgressBar.done();
    }
  }

  private Float findNumber(ObjectNode payload, String... keys) {
    for (String key : keys) {
      if (payload.has(key)) {
        return (float) payload.get(key).asDouble();
      }
    }
    return null;
  }
}
