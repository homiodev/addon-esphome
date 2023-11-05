package org.homio.addon.esphome.service;

import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.UNKNOWN;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.esphome.ESPHomeEndpoint;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity.Communication;
import org.homio.api.Context;
import org.homio.api.ContextInstall.PythonEnv;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.DecimalType;
import org.homio.api.state.StringType;
import org.homio.api.util.Lang;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

public class ESPHomeDeviceService extends ServiceInstance<ESPHomeDeviceEntity> {

    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
        new ConfigDeviceDefinitionService("esphome-devices.json");

    private final @Getter Map<String, ESPHomeEndpoint> endpoints = new ConcurrentHashMap<>();

    private List<ConfigDeviceDefinition> models;
    private @Getter CommunicatorService apiService;

    public ESPHomeDeviceService(@NotNull Context context, @NotNull ESPHomeDeviceEntity entity) {
        super(context, entity, true);
        context.ui().updateItem(entity);
        context.ui().toastr().success(Lang.getServerMessage("ENTITY_CREATED", "${%s}".formatted(entity.getTitle())));
    }

    public void setDeviceStatus(@NotNull Status status, @Nullable String message) {
        if (status != entity.getStatus()) {
            entity.setStatus(status, message);
            endpoints.get(ENDPOINT_DEVICE_STATUS).setValue(new StringType(status.name()), true);
        }
    }

    public void updateLastSeen() {
        endpoints.get(ENDPOINT_LAST_SEEN).setValue(new DecimalType(System.currentTimeMillis()), true);
    }

    @SneakyThrows
    public void uploadFont(MultipartFile file) {
        PythonEnv env = context.install().pythonEnv("esphome");
        String name = StringUtils.defaultString(file.getOriginalFilename(), file.getName());
        Path dir = Files.createDirectories(env.getPath().resolve("config").resolve("fonts"));
        file.transferTo(dir.resolve(name));
    }

    @Override
    public void destroy(boolean forRestart, Exception ex) throws Exception {
        if (apiService != null) {
            apiService.destroy();
        }
        downLinkQualityToZero();
    }

    public void addEndpointOptional(String key, Supplier<ESPHomeEndpoint> endpointProducer) {
        ESPHomeEndpoint endpoint = endpoints.get(key);
        if (endpoint == null) {
            endpoint = endpointProducer.get();
            endpoints.put(key, endpoint);
        }
    }

    public String getGroupDescription() {
        if (StringUtils.isEmpty(entity.getName()) || entity.getName().equals(entity.getIeeeAddress())) {
            return entity.getIeeeAddress();
        }
        return "${%s} [%s]".formatted(entity.getName(), entity.getIeeeAddress());
    }

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        if (models == null) {
            models = CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(null, endpoints.keySet());
        }
        return models;
    }

    @Override
    @SneakyThrows
    protected void firstInitialize() {
        createOrUpdateDeviceGroup();
        createRequireEndpoints();
        initialize();
    }

    @Override
    protected void initialize() {
        setDeviceStatus(OFFLINE, null);
        apiService = entity.getCommunicator() == Communication.NATIVE_API ?
            new ESPHomeNativeApiService(this) :
            new ESPHomeMQTTApiService(this);
        apiService.initialize();
    }

    private void createRequireEndpoints() {
        addEndpointOptional(ENDPOINT_LAST_SEEN, () -> new ESPHomeEndpoint(ENDPOINT_LAST_SEEN, EndpointType.number, entity));

        addEndpointOptional(ENDPOINT_DEVICE_STATUS, () ->
            new ESPHomeEndpoint(ENDPOINT_DEVICE_STATUS, EndpointType.select, entity, builder ->
                builder.setRange(OptionModel.list(Status.set(ONLINE, OFFLINE, UNKNOWN)))));

        for (ConfigDeviceEndpoint endpoint : CONFIG_DEVICE_SERVICE.getDeviceEndpoints().values()) {
            addEndpointOptional(endpoint.getName(), () -> buildEndpoint(endpoint, endpoint.getName()));
        }
    }

    private ESPHomeEndpoint buildEndpoint(ConfigDeviceEndpoint endpoint, String key) {
        EndpointType endpointType = EndpointType.valueOf(endpoint.getMetadata().optString("type", "string"));
        return new ESPHomeEndpoint(key, endpointType, entity, endpoint1 -> {
            String path = endpoint.getMetadata().optString("path", null);
            if (path != null) {
                String[] pathItems = path.split("/");
                endpoint1.setDataReader(payload -> {
                    for (String pathItem : pathItems) {
                        payload = payload.path(pathItem);
                    }
                    if (!payload.isMissingNode()) {
                        return endpointType.getNodeReader().apply(payload);
                    }
                    return null;
                });
            }
        });
    }

    private void createOrUpdateDeviceGroup() {
        context.var().createSubGroup("esphome", entity.getIeeeAddress(), entity.getDeviceFullName(), builder ->
            builder.setIcon(entity.getEntityIcon()).setDescription(getGroupDescription()).setLocked(true));
    }

    private void downLinkQualityToZero() {
        Optional.ofNullable(endpoints.get(DeviceEndpoint.ENDPOINT_SIGNAL)).ifPresent(endpoint -> {
            if (!endpoint.getValue().stringValue().equals("0")) {
                endpoint.setValue(new DecimalType(0), false);
            }
        });
    }
}
