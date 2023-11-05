package org.homio.addon.esphome;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.addon.esphome.service.ESPHomeDeviceService;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Getter
public class ESPHomeEndpoint extends BaseDeviceEndpoint<ESPHomeDeviceEntity> {

    private @Setter @Nullable Function<JsonNode, State> dataReader;
    private @Setter @Nullable String statusClass;
    private @Setter @Nullable String deviceClass;
    private @Setter @Nullable String description;

    private @Setter @Nullable String commandClass;
    private @Setter @Nullable String commandField;
    private @Setter @Nullable int commandKey;
    private @Setter @Nullable String pattern;
    private @Setter @Nullable float step;

    public ESPHomeEndpoint(@NotNull String endpointEntityID,
        @NotNull EndpointType endpointType,
        @NotNull ESPHomeDeviceEntity device) {
        this(endpointEntityID, endpointType, device, builder -> {});
    }

    public ESPHomeEndpoint(@NotNull String endpointEntityID,
        @NotNull EndpointType endpointType,
        @NotNull ESPHomeDeviceEntity device,
        @NotNull Consumer<ESPHomeEndpoint> builder) {
        super("ESPHome", device.context());
        ConfigDeviceEndpoint configEndpoint = ESPHomeDeviceService.CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        setIcon(new Icon(
            "fa fa-fw fa-" + (configEndpoint == null ? "tablet-screen-button" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()));

        init(
            ESPHomeDeviceService.CONFIG_DEVICE_SERVICE,
            endpointEntityID,
            device,
            false,
            false,
            endpointEntityID,
            endpointType);

        builder.accept(this);

        getOrCreateVariable();
    }

    public void mqttUpdate(JsonNode payload) {
        if (dataReader != null) {
            State state = dataReader.apply(payload);
            if (state != null) {
                this.setValue(state, true);
            }
        }
    }

    @Override
    public void writeValue(@NotNull State state) {
        /*switch (expose.getType()) {
            case NUMBER_TYPE -> fireAction(state.intValue());
            case BINARY_TYPE, SWITCH_TYPE -> fireAction(state.boolValue());
            default -> fireAction(state.stringValue());
        }*/
    }

    @Override
    public void readValue() {
    }

    @Override
    public String getVariableGroupID() {
        return "esphome-" + getDeviceID();
    }
}
