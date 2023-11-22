package org.homio.addon.esphome.entity;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.homio.addon.esphome.ESPHomeEntrypoint.ESPHOME_COLOR;
import static org.homio.addon.esphome.ESPHomeEntrypoint.ESPHOME_ICON;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.esphome.api.DeviceInfoResponse;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.ESPHomeCompactModeSetting;
import org.homio.addon.esphome.service.ESPHomeDeviceService;
import org.homio.api.Context;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.WebAddress;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.UISidebarMenu.TopSidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

@Log4j2
@Getter
@Setter
@Entity
@NoArgsConstructor
@UISidebarMenu(icon = ESPHOME_ICON,
               order = 150,
               bg = ESPHOME_COLOR,
               parent = TopSidebarMenu.DEVICES,
               overridePath = "esphome",
               filter = {"*:fas fa-filter:#8DBA73", "status:fas fa-heart-crack:#C452C4"},
               sort = {
                   "name~#FF9800:fas fa-arrow-up-a-z:fas fa-arrow-down-z-a",
                   "status~#7EAD28:fas fa-turn-up:fas fa-turn-down",
                   "place~#9C27B0:fas fa-location-dot:fas fa-location-dot fa-rotate-180"
               })
public final class ESPHomeDeviceEntity extends DeviceBaseEntity implements
    DeviceEndpointsBehaviourContract,
    HasFirmwareVersion,
    ESPHomeNativeApiConfiguration,
    ESPHomeMQTTConfiguration,
    EntityService<ESPHomeDeviceService> {

    public static final String PREFIX = "esphome";

    @Override
    public String getModel() {
        return getJsonData("model", getDeviceIpAddress() + " - " + getPlatform());
    }

    @Override
    @UIField(order = 10, hideOnEmpty = true, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 100, hideOnEmpty = true, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public Communication getCommunicator() {
        return getJsonDataEnum("comm", Communication.NATIVE_API);
    }

    public void setCommunicator(Communication communication) {
        setJsonDataEnum("comm", communication);
    }

    @Override
    public String getFirmwareVersion() {
        return getJsonData("fv");
    }

    @UIField(order = 50, hideOnEmpty = true, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getPlatform() {
        return getJsonData("pl");
    }

    @UIField(order = 60, hideOnEmpty = true, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getBoard() {
        return getJsonData("br");
    }

    @UIField(order = 70, hideOnEmpty = true, hideInEdit = true)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public String getNetwork() {
        return getJsonData("nw");
    }

    @UIField(order = 100, hideOnEmpty = true, hideInEdit = true, type = UIFieldType.HTML)
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    public WebAddress getIpAddress() {
        return new WebAddress(getDeviceIpAddress());
    }

    @JsonIgnore
    public String getDeviceIpAddress() {
        return getJsonData("ip");
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        if (getDeviceIpAddress().isEmpty()) {
            return Set.of("ERROR.NO_HOST");
        }
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return Objects.hashCode(getIeeeAddress()) + Objects.hashCode(getName()) +
            getJsonDataHashCode("comm", "lp", "tp", "pd", "ip", "napwd", "nap", "pi", "mpt");
    }

    @Override
    public @NotNull Class<ESPHomeDeviceService> getEntityServiceItemClass() {
        return ESPHomeDeviceService.class;
    }

    @Override
    public @NotNull ESPHomeDeviceService createService(@NotNull Context context) {
        return new ESPHomeDeviceService(context, this);
    }

    @Override
    public @NotNull String getDeviceFullName() {
        return "%s(%s) [${%s}]".formatted(
            getTitle(),
            getIeeeAddress(),
            defaultIfEmpty(getPlace(), "W.ERROR.PLACE_NOT_SET"));
    }

    @Override
    public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
        return optService().map(ESPHomeDeviceService::findDevices).orElse(List.of());
    }

    @Override
    public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
        return optService().map(ESPHomeDeviceService::getEndpoints).orElse(Map.of());
    }

    @UIField(order = 3, disableEdit = true, label = "ieeeAddress")
    @UIFieldShowOnCondition("return !context.get('compactMode')")
    @UIFieldGroup("GENERAL")
    public String getIeeeAddressLabel() {
        return trimToEmpty(getIeeeAddress()).toUpperCase();
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public @NotNull String getIeeeAddress() {
        return Objects.requireNonNull(super.getIeeeAddress());
    }

    public boolean isCompactMode() {
        return context().setting().getValue(ESPHomeCompactModeSetting.class);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        uiInputBuilder.addSimpleUploadButton("UPLOAD_FONT", new Icon("fas fa-font"),
            new String[]{".ttf", ".pcf", ".bdf"}, (context, params) -> {
                MultipartFile[] files = (MultipartFile[]) params.get("files");
                for (MultipartFile file : files) {
                    getService().uploadFont(file);
                }
                return ActionResponseModel.success();
            });

        if (getCommunicator() == Communication.NATIVE_API) {
            uiInputBuilder.addSelectableButton("REFRESH", new Icon("fas fa-arrows-rotate", "#27C4C3"),
                (context, params) -> getService().getApiService().refresh());
        }
    }

    @Override
    public String getDefaultName() {
        return "ESPHome device";
    }

    public boolean tryUpdate(String mac, ObjectNode payload) {
        long entityHashCode = getEntityHashCode();
        setIeeeAddress(mac);
        setName(payload.path("name").asText());

        setJsonData("ip", payload.path("ip").asText());
        setJsonData("fv", payload.path("version").asText());
        setJsonData("pl", payload.path("platform").asText());
        setJsonData("br", payload.path("board").asText());
        setJsonData("nw", payload.path("network").asText());
        return entityHashCode != getEntityHashCode();
    }

    public boolean tryUpdate(DeviceInfoResponse rsp) {
        long entityHashCode = getEntityHashCode();
        setName(rsp.getName());
        setJsonData("fv", rsp.getEsphomeVersion());
        setJsonData("manufacturer", rsp.getManufacturer());
        setJsonData("model", rsp.getModel());
        return entityHashCode != getEntityHashCode();
    }

    @Override
    public String getImageIdentifier() {
        return getPlatform() + ".png";
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return PREFIX;
    }

    public enum Communication {
        MQTT, NATIVE_API
    }
}
