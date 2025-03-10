package org.homio.addon.esphome.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.ESPHomeEntrypoint;
import org.homio.addon.esphome.service.ESPHomeProjectService;
import org.homio.api.Context;
import org.homio.api.ContextService;
import org.homio.api.ContextService.MQTTEntityService;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.entity.types.StorageEntity;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldLinkToEntity;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.color.UIFieldColorRef;
import org.homio.api.ui.field.inline.UIFieldInlineEntities;
import org.homio.api.ui.field.inline.UIFieldInlineEntityWidth;
import org.homio.api.ui.field.selection.UIFieldEntityTypeSelection;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.SelectionSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.homio.addon.esphome.ESPHomeEntrypoint.ESPHOME_COLOR;
import static org.homio.addon.esphome.ESPHomeEntrypoint.ESPHOME_ICON;

@SuppressWarnings({"JpaAttributeTypeInspection", "JpaAttributeMemberSignatureInspection"})
@Log4j2
@Entity
@UISidebarChildren(icon = ESPHOME_ICON, color = ESPHOME_COLOR)
public class ESPHomeProjectEntity extends MicroControllerBaseEntity
  implements EntityService<ESPHomeProjectService>,
  HasFirmwareVersion,
  HasStatusAndMsg, HasEntityLog {

  @UIField(order = 9999, disableEdit = true, hideInEdit = true)
  @UIFieldInlineEntities(bg = "#27FF000D")
  public List<DeviceEntity> getDevices() {
    return optService()
      .map(service ->
        service.getExistedDevices().values()
          .stream()
          .sorted()
          .map(DeviceEntity::new)
          .collect(Collectors.toList()))
      .orElse(Collections.emptyList());
  }

  @UIField(order = 20, required = true, inlineEditWhenEmpty = true)
  @UIFieldEntityTypeSelection(type = ContextService.MQTT_SERVICE)
  @UIFieldLinkToEntity(value = StorageEntity.class, applyTitle = true)
  public String getMqttEntity() {
    return getJsonData("mqtt");
  }

  public void setMqttEntity(String value) {
    setJsonData("mqtt", value);
  }

  @JsonIgnore
  public @Nullable MQTTEntityService getMqttEntityService() {
    SelectionSource selection = DataSourceUtil.getSelection(getMqttEntity());
    return selection.getValue(context());
  }

  @Override
  public String getDefaultName() {
    return "ESPHome";
  }

  @Override
  public @Nullable Set<String> getConfigurationErrors() {
    Set<String> errors = new HashSet<>();
    try {
      if (getMqttEntityService() == null) {
        errors.add("ERROR.NO_MQTT_SELECTED");
      }

    } catch (Exception ex) {
      errors.add("ERROR.WRONG_MQTT_BROKER");
    }
    return errors;
  }

  @Override
  public long getEntityServiceHashCode() {
    return getJsonDataHashCode("mqtt");
  }

  @UIField(order = 100, required = true, type = UIFieldType.Chips)
  public List<String> getDiscoveryPrefix() {
    return getJsonDataList("dp");
  }

  public void setDiscoveryPrefix(String value) {
    setJsonData("dp", value);
  }

  @Override
  public @Nullable String getFirmwareVersion() {
    return optService().map(ESPHomeProjectService::getVersion).orElse(null);
  }

  @Override
  public @NotNull Class<ESPHomeProjectService> getEntityServiceItemClass() {
    return ESPHomeProjectService.class;
  }

  @Override
  @SneakyThrows
  public @NotNull ESPHomeProjectService createService(@NotNull Context context) {
    return new ESPHomeProjectService(context, this);
  }

  @Override
  public void logBuilder(EntityLogBuilder entityLogBuilder) {
    entityLogBuilder.addTopicFilterByEntityID(ESPHomeEntrypoint.class.getPackage());
  }

  @Override
  public boolean isDisableDelete() {
    return true;
  }

  @Override
  public boolean isDisableEdit() {
    return true;
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return "esphomeprj";
  }

  @Getter
  @NoArgsConstructor
  public static class DeviceEntity {

    @UIField(order = 1)
    @UIFieldInlineEntityWidth(35)
    @UIFieldLinkToEntity(ESPHomeDeviceEntity.class)
    private String ieeeAddress;

    @UIField(order = 2)
    @UIFieldColorRef("color")
    private String name;

    @UIField(order = 4)
    @UIFieldInlineEntityWidth(10)
    private int endpointsCount;

    private String color;

    public DeviceEntity(ESPHomeDeviceEntity entity) {
      color = entity.getStatus().getColor();
      name = entity.getName();
      ieeeAddress = entity.getIeeeAddress();
      endpointsCount = entity.getEndpoints().size();
    }
  }
}
