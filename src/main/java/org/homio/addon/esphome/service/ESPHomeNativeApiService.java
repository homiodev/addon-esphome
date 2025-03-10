package org.homio.addon.esphome.service;

import com.google.protobuf.GeneratedMessage;
import io.esphome.api.BinarySensorStateResponse;
import io.esphome.api.ButtonCommandRequest;
import io.esphome.api.ClimateCommandRequest;
import io.esphome.api.ClimateCommandRequest.Builder;
import io.esphome.api.ClimateFanMode;
import io.esphome.api.ClimateMode;
import io.esphome.api.ClimatePreset;
import io.esphome.api.ClimateStateResponse;
import io.esphome.api.ClimateSwingMode;
import io.esphome.api.ConnectRequest;
import io.esphome.api.ConnectResponse;
import io.esphome.api.DeviceInfoRequest;
import io.esphome.api.DeviceInfoResponse;
import io.esphome.api.DisconnectRequest;
import io.esphome.api.DisconnectResponse;
import io.esphome.api.HelloRequest;
import io.esphome.api.HelloResponse;
import io.esphome.api.LightStateResponse;
import io.esphome.api.ListEntitiesBinarySensorResponse;
import io.esphome.api.ListEntitiesButtonResponse;
import io.esphome.api.ListEntitiesClimateResponse;
import io.esphome.api.ListEntitiesDoneResponse;
import io.esphome.api.ListEntitiesLightResponse;
import io.esphome.api.ListEntitiesNumberResponse;
import io.esphome.api.ListEntitiesRequest;
import io.esphome.api.ListEntitiesSelectResponse;
import io.esphome.api.ListEntitiesSensorResponse;
import io.esphome.api.ListEntitiesSwitchResponse;
import io.esphome.api.ListEntitiesTextSensorResponse;
import io.esphome.api.NumberCommandRequest;
import io.esphome.api.NumberStateResponse;
import io.esphome.api.PingRequest;
import io.esphome.api.PingResponse;
import io.esphome.api.SelectCommandRequest;
import io.esphome.api.SelectStateResponse;
import io.esphome.api.SensorStateClass;
import io.esphome.api.SensorStateResponse;
import io.esphome.api.SubscribeStatesRequest;
import io.esphome.api.SwitchCommandRequest;
import io.esphome.api.SwitchStateResponse;
import io.esphome.api.TextSensorStateResponse;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.esphome.ESPHomeEndpoint;
import org.homio.addon.esphome.api.CommunicationListener;
import org.homio.addon.esphome.api.EnumHelper;
import org.homio.addon.esphome.api.SensorNumberDeviceClass;
import org.homio.addon.esphome.api.comm.ConnectionSelector;
import org.homio.addon.esphome.api.comm.EncryptedStreamHandler;
import org.homio.addon.esphome.api.comm.PlainTextStreamHandler;
import org.homio.addon.esphome.api.comm.ProtocolAPIError;
import org.homio.addon.esphome.api.comm.ProtocolException;
import org.homio.addon.esphome.api.comm.StreamHandler;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.OptionModel;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.UNKNOWN;


@Log4j2
public class ESPHomeNativeApiService implements CommunicationListener, CommunicatorService {

  public static final String CHANNEL_TARGET_TEMPERATURE = "target_temperature";
  public static final String CHANNEL_FAN_MODE = "fan_mode";
  public static final String CHANNEL_CUSTOM_FAN_MODE = "custom_fan_mode";
  public static final String CHANNEL_PRESET = "preset";
  public static final String CHANNEL_CUSTOM_PRESET = "custom_preset";
  public static final String CHANNEL_SWING_MODE = "swing_mode";
  public static final String CHANNEL_CURRENT_TEMPERATURE = "current_temperature";
  public static final String CHANNEL_MODE = "mode";

  public static final int CONNECT_TIMEOUT = 20;
  private static final int API_VERSION_MAJOR = 1;
  private static final int API_VERSION_MINOR = 7;
  private final ESPHomeDeviceService service;
  private final String ipAddress;

  private final ConnectionSelector connectionSelector;
  private final String entityID;
  private final ESPHomeDeviceEntity entity;
  private final Map<Class<? extends GeneratedMessage>, MessageHandler> classToHandlerMap = new HashMap<>();
  private ConnectionState connectionState = ConnectionState.UNINITIALIZED;
  private StreamHandler frameHelper;
  private ThreadContext<Void> pingWatchdogFuture;
  private ThreadContext<Void> reconnectFuture;
  private boolean disposed;
  private Instant lastPong = Instant.now();

  @SneakyThrows
  public ESPHomeNativeApiService(@NotNull ESPHomeDeviceService service) {
    this.service = service;
    this.entity = service.getEntity();
    this.entityID = service.getEntityID();
    this.ipAddress = service.getEntity().getDeviceIpAddress();
    this.connectionSelector = new ConnectionSelector();
    registerMessageHandlers();
  }

  private static State toNumericState(ESPHomeEndpoint endpoint, float state, boolean missingState) {
    if (missingState || Float.isNaN(state)) {
      return null;
    } else {
      String deviceClass = endpoint.getDeviceClass();
      if (deviceClass != null) {
        SensorNumberDeviceClass sensorDeviceClass = SensorNumberDeviceClass.fromDeviceClass(deviceClass);
        if (sensorDeviceClass != null) {
          if (sensorDeviceClass.getItemType().startsWith("Number")) {
            return new DecimalType(state);
          } else {
            log.warn(
              "Expected SensorNumberDeviceClass '{}' to be of item type Number[:Dimension]. Returning undef",
              deviceClass);
            return null;
          }
        } else {
          return new DecimalType(state);
        }
      } else {
        return new DecimalType(state);
      }
    }
  }

  private static void buildClimateEndpoints(ESPHomeNativeApiService service, ListEntitiesClimateResponse rsp) {
    service.addEndpoint(CHANNEL_TARGET_TEMPERATURE, rsp, EndpointType.number, ep -> {
      ep.setUnit("°C");
      ep.setUpdateHandler(state ->
        service.sendClimate(rsp,
          builder -> builder.setHasCustomPreset(true).setCustomPreset(state.stringValue())));
    });
    if (rsp.getSupportsCurrentTemperature()) {
      service.addEndpoint(CHANNEL_CURRENT_TEMPERATURE, rsp, EndpointType.number, ep -> ep.setUnit("°C"));
    }
    if (rsp.getSupportedModesCount() > 0) {
      service.addEndpoint(CHANNEL_MODE, rsp, EndpointType.select, ep -> {
        List<String> range = rsp.getSupportedModesList().stream().map(EnumHelper::stripEnumPrefix).collect(Collectors.toList());
        ep.setRange(OptionModel.list(range));
        ep.setUpdateHandler(state ->
          service.sendClimate(rsp, builder ->
            builder.setHasMode(true).setMode(ClimateMode.valueOf(state.stringValue()))));
      });
    }
    if (rsp.getSupportedFanModesCount() > 0) {
      service.addEndpoint(CHANNEL_FAN_MODE, rsp, EndpointType.string, ep -> {
        List<String> range = rsp.getSupportedFanModesList().stream().map(EnumHelper::stripEnumPrefix).collect(Collectors.toList());
        ep.setRange(OptionModel.list(range));
        ep.setUpdateHandler(state ->
          service.sendClimate(rsp, builder ->
            builder.setHasFanMode(true).setFanMode(ClimateFanMode.valueOf(state.stringValue()))));
      });
    }
    if (rsp.getSupportedCustomFanModesCount() > 0) {
      service.addEndpoint(CHANNEL_CUSTOM_FAN_MODE, rsp, EndpointType.string, ep -> {
        ep.setRange(OptionModel.list(rsp.getSupportedCustomFanModesList()));
        ep.setUpdateHandler(state ->
          service.sendClimate(rsp,
            builder -> builder.setHasCustomFanMode(true).setCustomFanMode(state.stringValue())));
      });
    }
    if (rsp.getSupportedPresetsCount() > 0) {
      service.addEndpoint(CHANNEL_PRESET, rsp, EndpointType.string, ep -> {
        List<String> range = rsp.getSupportedPresetsList().stream().map(EnumHelper::stripEnumPrefix).collect(Collectors.toList());
        ep.setRange(OptionModel.list(range));
        ep.setUpdateHandler(state ->
          service.sendClimate(rsp,
            builder -> builder.setHasPreset(true).setPreset(ClimatePreset.valueOf(state.stringValue()))));
      });
    }
    if (rsp.getSupportedCustomPresetsCount() > 0) {
      service.addEndpoint(CHANNEL_CUSTOM_PRESET, rsp, EndpointType.string, ep -> {
        ep.setRange(OptionModel.list(rsp.getSupportedCustomPresetsList()));
        ep.setUpdateHandler(state ->
          service.sendClimate(rsp,
            builder -> builder.setHasCustomPreset(true).setCustomPreset(state.stringValue())));
      });
    }
    if (rsp.getSupportedSwingModesCount() > 0) {
      service.addEndpoint(CHANNEL_SWING_MODE, rsp, EndpointType.string, ep -> {
        ep.setRange(OptionModel.list(rsp.getSupportedCustomPresetsList()));
        ep.setUpdateHandler(state ->
          service.sendClimate(rsp,
            builder -> builder.setHasSwingMode(true).setSwingMode(ClimateSwingMode.valueOf(state.stringValue()))));
      });
    }
  }

  public void addEndpoint(int key,
                          @NotNull String endpointID,
                          String name,
                          String deviceClass,
                          @NotNull EndpointType endpointType,
                          Consumer<ESPHomeEndpoint> builder) {
    service.addEndpointOptional(String.valueOf(key), () -> {
      ESPHomeEndpoint espHomeEndpoint = new ESPHomeEndpoint(endpointID, endpointType, entity);
      espHomeEndpoint.setDescription(name);
      espHomeEndpoint.setDeviceClass(deviceClass);
      builder.accept(espHomeEndpoint);
      return espHomeEndpoint;
    });
  }

  public void addEndpoint(
    @NotNull String key,
    @NotNull ListEntitiesClimateResponse rsp,
    @NotNull EndpointType endpointType,
    Consumer<ESPHomeEndpoint> builder) {
    service.addEndpointOptional(key + "_" + rsp.getKey(), () -> {
      ESPHomeEndpoint espHomeEndpoint = new ESPHomeEndpoint(key, endpointType, entity);
      espHomeEndpoint.setDescription(rsp.getName());
      builder.accept(espHomeEndpoint);
      return espHomeEndpoint;
    });
  }

  public void initialize() {
    connectionSelector.start();
    scheduleConnect(0);
  }

  public void destroy() {
    connectionSelector.stop();
    cancelReconnectFuture();
    if (frameHelper != null) {
      cancelPingWatchdog();

      if (connectionState == ConnectionState.CONNECTED) {
        try {
          frameHelper.send(DisconnectRequest.getDefaultInstance());
        } catch (ProtocolAPIError e) {
          // Quietly ignore
        }
      } else {
        frameHelper.close();
      }
    }
    disposed = true;
  }

  @Override
  public ActionResponseModel refresh() {
    if (connectionState != ConnectionState.CONNECTED) {
      return ActionResponseModel.showError("W.ERROR.NOT_CONNECTED");
    }
    try {
      frameHelper.send(SubscribeStatesRequest.getDefaultInstance());
      return ActionResponseModel.fired();
    } catch (ProtocolAPIError ex) {
      log.error("[{}] Error sending refresh command to {}. Ex: {}", entityID, entity.getDeviceIpAddress(), ex.getMessage());
      return ActionResponseModel.showError(ex);
    }
  }

  @Override
  public void onPacket(GeneratedMessage message) throws ProtocolAPIError {
    switch (connectionState) {
      case UNINITIALIZED -> log.warn("[{}]: ESPHome. Received packet while uninitialized.", entityID);
      case HELLO_SENT -> handleHelloResponse(message);
      case LOGIN_SENT -> handleLoginResponse(message);
      case CONNECTED -> handleConnected(message);
    }
  }

  @Override
  public void onEndOfStream(String message) {
    service.setDeviceStatus(OFFLINE, message);
    setUndefToAllChannels();
    frameHelper.close();
    cancelPingWatchdog();
    connectionState = ConnectionState.UNINITIALIZED;
    scheduleConnect(CONNECT_TIMEOUT * 2);
  }

  @Override
  public void onParseError(String message) {
    service.setDeviceStatus(OFFLINE, message);
    setUndefToAllChannels();
    cancelPingWatchdog();
    frameHelper.close();
    connectionState = ConnectionState.UNINITIALIZED;
    scheduleConnect(CONNECT_TIMEOUT * 2);
  }

  @Override
  public void onConnect() throws ProtocolAPIError {
    log.debug("Connection established");
    HelloRequest helloRequest = HelloRequest.newBuilder().setClientInfo("openHAB")
      .setApiVersionMajor(API_VERSION_MAJOR).setApiVersionMinor(API_VERSION_MINOR).build();
    connectionState = ConnectionState.HELLO_SENT;
    frameHelper.send(helloRequest);
  }

  @SneakyThrows
  public void sendMessage(GeneratedMessage message) {
    try {
      log.debug("[{}]: ESPHome. Sending command", entityID);
      frameHelper.send(message);
    } catch (ProtocolAPIError e) {
      log.error("[{}]: ESPHome. Failed to send command", entityID, e);
    }
  }

  private void registerMessageHandlers() {
    for (MessageHandler handler : MessageHandler.values()) {
      classToHandlerMap.put(handler.listEntitiesClass, handler);
      classToHandlerMap.put(handler.stateClass, handler);
    }
  }

  private void updateState(int key, Function<ESPHomeEndpoint, State> stateHandler) {
    updateState(String.valueOf(key), stateHandler);
  }

  private void updateState(String key, Function<ESPHomeEndpoint, State> stateHandler) {
    ESPHomeEndpoint endpoint = service.getEndpoints().get(key);
    if (endpoint != null) {
      State state = stateHandler.apply(endpoint);
      if (state != null) {
        endpoint.setValue(state, true);
      } else {
        endpoint.setInitialValue(new StringType("N/A"));
      }
    }
  }

  private void scheduleConnect(int delay) {
    reconnectFuture = service.context().bgp().builder("esphome-connect-" + entityID)
      .delay(Duration.ofSeconds(delay))
      .execute(this::connect);
  }

  private void connect() {
    try {
      //  dynamicChannels.clear();
      log.info("[{}]: ESPHome. Trying to connect to {}:{}", entityID, ipAddress, entity.getNativeApiPort());
      service.setDeviceStatus(UNKNOWN, String.format("Connecting to %s:%d", ipAddress, entity.getNativeApiPort()));

      boolean useEncryption = entity.getNativeEncryptionKey() != null;
      if (!useEncryption) {
        log.warn("Using unencrypted connection. This is deprecated and will be removed in the future. Please use encryption.");
      }
      frameHelper = useEncryption ? new EncryptedStreamHandler(connectionSelector,
        this, entity.getNativeEncryptionKey().asString(), ipAddress)
        : new PlainTextStreamHandler(connectionSelector, this, ipAddress);
      //connection = new ESPHomeConnection(connectionSelector, handler, ipAddress);
      frameHelper.connect(new InetSocketAddress(ipAddress, entity.getNativeApiPort()));
    } catch (ProtocolException ex) {
      log.warn("[{}]: ESPHome. Error initial connection: {}", entityID, CommonUtils.getErrorMessage(ex));
      service.setDeviceStatus(OFFLINE, ex.getMessage());
      if (!disposed) { // Don't reconnect if we've been disposed
        scheduleConnect(CONNECT_TIMEOUT * 2);
      }
    }
  }

  private void handleConnected(GeneratedMessage message) throws ProtocolAPIError {
    service.updateLastSeen();
    log.debug("[{}]: ESPHome. Received message {}", entityID, message);
    if (message instanceof DeviceInfoResponse rsp) {
      if (!rsp.getMacAddress().equals(entity.getIeeeAddress())) {
        log.error("[{}]: ESPHome. MAC Address not match!!!", entityID);
      }
      if (entity.tryUpdate(rsp)) {
        service.context().db().save(entity);
      }
    } else if (message instanceof ListEntitiesDoneResponse) {
      // updateThing(editThing().withChannels(dynamicChannels).build());
      log.debug("[{}]: ESPHome. Device interrogation complete, done updating thing channels", entityID);
      // interrogated = true;
      frameHelper.send(SubscribeStatesRequest.getDefaultInstance());
    } else if (message instanceof PingRequest) {
      log.debug("[{}]: ESPHome. Responding to ping request", entityID);
      frameHelper.send(PingResponse.getDefaultInstance());
    } else if (message instanceof PingResponse) {
      log.debug("[{}]: ESPHome. Received ping response", entityID);
      lastPong = Instant.now();
    } else if (message instanceof DisconnectRequest) {
      frameHelper.send(DisconnectResponse.getDefaultInstance());
      remoteDisconnect();
    } else if (message instanceof DisconnectResponse) {
      frameHelper.close();
    } else {
      // Regular messages handled by message handlers
      MessageHandler abstractMessageHandler = classToHandlerMap.get(message.getClass());
      if (abstractMessageHandler != null) {
        if (message.getClass().getSimpleName().startsWith("ListEntities")) {
          abstractMessageHandler.endpointBuilder.accept(this, message);
        } else {
          try {
            abstractMessageHandler.stateHandler.accept(this, message);
          } catch (Exception ex) {
            log.warn("[{}]: Error updating state: {}", entityID, CommonUtils.getErrorMessage(ex));
          }
        }
      } else {
        log.warn("[{}]: ESPHome. Unhandled message of type {}. This is lack of support in the binding. Content: '{}'.",
          entityID, message.getClass().getName(), message);
      }
    }
  }

  private void handleLoginResponse(GeneratedMessage message) throws ProtocolAPIError {
    if (message instanceof ConnectResponse connectResponse) {
      log.debug("[{}]: ESPHome. Received login response {}", entityID, connectResponse);

      if (connectResponse.getInvalidPassword()) {
        log.error("[{}]: ESPHome. Invalid password", entityID);
        frameHelper.close();
        connectionState = ConnectionState.UNINITIALIZED;
        service.setDeviceStatus(OFFLINE, "Invalid password");
        return;
      }
      connectionState = ConnectionState.CONNECTED;
      service.setDeviceStatus(ONLINE, null);
      log.debug("[{}]: ESPHome. Device login complete, starting device interrogation", entityID);

      // Reset last pong
      lastPong = Instant.now();

      pingWatchdogFuture = service.context().bgp().builder("ping-watchdog-" + entityID)
        .intervalWithDelay(Duration.ofSeconds(entity.getPingInterval()))
        .execute(this::ping);

      frameHelper.send(DeviceInfoRequest.getDefaultInstance());
      frameHelper.send(ListEntitiesRequest.getDefaultInstance());
    }
  }

  private void ping() {
    if (lastPong.plusSeconds((long) entity.getMaxPingTimeout() * entity.getPingInterval()).isBefore(Instant.now())) {
      log.warn(
        "[{}]: ESPHome. Ping responses lacking Waited {} times {} seconds, total of {}. Assuming connection lost and "
        + "disconnecting",
        entityID, entity.getMaxPingTimeout(), entity.getPingInterval(),
        entity.getMaxPingTimeout() * entity.getPingInterval());
      pingWatchdogFuture.cancel(false);
      frameHelper.close();
      connectionState = ConnectionState.UNINITIALIZED;
      service.setDeviceStatus(OFFLINE, String.format("ESPHome did not respond to ping requests. %d pings sent with %d s delay",
        entity.getMaxPingTimeout(), entity.getPingInterval()));
      scheduleConnect(10);
    } else {

      try {
        log.debug("[{}]: ESPHome. Sending ping", entityID);
        frameHelper.send(PingRequest.getDefaultInstance());
      } catch (ProtocolAPIError e) {
        log.warn("[{}]: ESPHome. Error sending ping request", entityID, e);
      }
    }
  }

  private void handleHelloResponse(GeneratedMessage message) throws ProtocolAPIError {
    if (message instanceof HelloResponse helloResponse) {
      log.debug("[{}]: ESPHome. Received hello response {}", entityID, helloResponse);
      log.info("[{}]: ESPHome. Connected. Device '{}' running '{}' on protocol version '{}.{}'", entityID,
        helloResponse.getName(), helloResponse.getServerInfo(), helloResponse.getApiVersionMajor(),
        helloResponse.getApiVersionMinor());
      connectionState = ConnectionState.LOGIN_SENT;

      if (!entity.getNativeApiPassword().isEmpty()) {
        frameHelper.send(ConnectRequest.newBuilder().setPassword(entity.getNativeApiPassword().asString()).build());
      } else {
        frameHelper.send(ConnectRequest.getDefaultInstance());

      }
    }
    // Check if
  }

  private void setUndefToAllChannels() {
    // Update all channels to UNDEF to avoid stale values
    System.out.println("ssss");
    // getThing().getChannels().forEach(channel -> updateState(channel, UnDefType.UNDEF));
  }

  private void cancelReconnectFuture() {
    ContextBGP.cancel(reconnectFuture);
  }

  private void cancelPingWatchdog() {
    ContextBGP.cancel(pingWatchdogFuture);
  }

  private void remoteDisconnect() {
    frameHelper.close();
    setUndefToAllChannels();
    connectionState = ConnectionState.UNINITIALIZED;
    service.setDeviceStatus(OFFLINE, String.format("ESPHome device requested disconnect. Will reconnect in %d seconds", CONNECT_TIMEOUT));
    cancelPingWatchdog();
    scheduleConnect(CONNECT_TIMEOUT);
  }

  private void sendClimate(ListEntitiesClimateResponse rsp, Consumer<Builder> commandBuilder) {
    Builder builder = ClimateCommandRequest.newBuilder().setKey(rsp.getKey());
    commandBuilder.accept(builder);
    ClimateCommandRequest request = builder.build();
    sendMessage(request);
  }

  public enum MessageHandler {
    Number(ListEntitiesNumberResponse.class, NumberStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), null, EndpointType.number, ep -> {
        ep.setPattern("%s");
        ep.setUpdateHandler(state -> {
          NumberCommandRequest request = NumberCommandRequest.newBuilder().setKey(rsp.getKey()).setState(state.floatValue()).build();
          service.sendMessage(request);
        });
      }), (service, rsp) ->
      service.updateState(rsp.getKey(), ep -> toNumericState(ep, rsp.getState(), rsp.getMissingState()))),
    Button(ListEntitiesButtonResponse.class, ButtonCommandRequest.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), rsp.getDeviceClass(), EndpointType.bool, ep -> {
      }), (service, rsp) -> {
    }),
    Select(ListEntitiesSelectResponse.class, SelectStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), null, EndpointType.select, ep ->
        ep.setPattern("%s")), (service, rsp) ->
      service.updateState(rsp.getKey(), ep -> rsp.getMissingState() ? null : new StringType(rsp.getState()))),
    TextSensor(ListEntitiesTextSensorResponse.class, TextSensorStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), null, EndpointType.string, ep ->
        ep.setUpdateHandler(state -> {
          SelectCommandRequest request = SelectCommandRequest.newBuilder().setKey(rsp.getKey()).setState(state.stringValue()).build();
          service.sendMessage(request);
        })), (service, rsp) ->
      service.updateState(rsp.getKey(), ep -> rsp.getMissingState() ? StringType.EMPTY : new StringType(rsp.getState()))),
    Sensor(ListEntitiesSensorResponse.class, SensorStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), null, EndpointType.number, ep -> {
        ep.setDescription(rsp.getName());
        String deviceClass = rsp.getDeviceClass();
        if (!deviceClass.isEmpty()) {
          ep.setDeviceClass(deviceClass);
        } else if (rsp.getStateClass() != SensorStateClass.STATE_CLASS_NONE) {
          ep.setDeviceClass("generic_number");
        }
        String unitOfMeasurement = rsp.getUnitOfMeasurement();
        if (!"None".equals(unitOfMeasurement)) {
          ep.setUnit(unitOfMeasurement);
        }
        ep.setPattern("%." + rsp.getAccuracyDecimals() + "f " + (unitOfMeasurement.equals("%") ? "%unit%" : unitOfMeasurement));
      }), (service, rsp) ->
      service.updateState(rsp.getKey(), ep -> toNumericState(ep, rsp.getState(), rsp.getMissingState()))),
    BinarySensor(ListEntitiesBinarySensorResponse.class, BinarySensorStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), rsp.getDeviceClass(), EndpointType.bool, ep -> {
        ep.setUpdateHandler(state -> {
          SelectCommandRequest request = SelectCommandRequest.newBuilder().setKey(rsp.getKey()).setState(state.stringValue()).build();
          service.sendMessage(request);
        });
      }), (service, rsp) ->
      service.updateState(rsp.getKey(), ep -> rsp.getMissingState() ? null : OnOffType.of(rsp.getState()))),
    Switch(ListEntitiesSwitchResponse.class, SwitchStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), rsp.getDeviceClass(), EndpointType.bool, ep ->
        ep.setUpdateHandler(state -> {
          SwitchCommandRequest request = SwitchCommandRequest.newBuilder().setKey(rsp.getKey()).setState(state.boolValue()).build();
          service.sendMessage(request);
        })), (service, rsp) ->
      service.updateState(rsp.getKey(), ep -> OnOffType.of(rsp.getState()))),
    Climate(ListEntitiesClimateResponse.class, ClimateStateResponse.class,
      ESPHomeNativeApiService::buildClimateEndpoints,
      MessageHandler::handleClimateEndpoints),
    Light(ListEntitiesLightResponse.class, LightStateResponse.class, (service, rsp) ->
      service.addEndpoint(rsp.getKey(), rsp.getUniqueId(), rsp.getName(), null, EndpointType.color, ep -> {
      }), (service, rsp) ->
      log.warn("[{}]: Unhandled state for esp light {} - in fact the Light "
               + "component isn't really implemented yet. Contribution needed", service.entityID, rsp.getKey()));

    private final Class<? extends GeneratedMessage> listEntitiesClass;
    private final Class<? extends GeneratedMessage> stateClass;
    private final BiConsumer<ESPHomeNativeApiService, GeneratedMessage> endpointBuilder;
    private final BiConsumer<ESPHomeNativeApiService, GeneratedMessage> stateHandler;

    @SuppressWarnings("unchecked")
    <S extends GeneratedMessage, H extends GeneratedMessage> MessageHandler(
      @NotNull Class<S> listEntitiesClass,
      @NotNull Class<H> stateClass,
      @NotNull BiConsumer<ESPHomeNativeApiService, S> endpointBuilder,
      @NotNull BiConsumer<ESPHomeNativeApiService, H> stateHandler) {
      this.listEntitiesClass = listEntitiesClass;
      this.stateClass = stateClass;
      this.endpointBuilder = (BiConsumer<ESPHomeNativeApiService, GeneratedMessage>) endpointBuilder;
      this.stateHandler = (BiConsumer<ESPHomeNativeApiService, GeneratedMessage>) stateHandler;
    }

    private static void handleClimateEndpoints(ESPHomeNativeApiService service, ClimateStateResponse rsp) {
      service.updateState(CHANNEL_TARGET_TEMPERATURE + "_" + rsp.getKey(), endpoint ->
        new DecimalType(rsp.getTargetTemperature()));
      service.updateState(CHANNEL_CURRENT_TEMPERATURE + "_" + rsp.getKey(), endpoint ->
        new DecimalType(rsp.getCurrentTemperature()));
      service.updateState(CHANNEL_MODE + "_" + rsp.getKey(), endpoint ->
        new StringType(EnumHelper.stripEnumPrefix(rsp.getMode())));
      service.updateState(CHANNEL_FAN_MODE + "_" + rsp.getKey(), endpoint ->
        new StringType(EnumHelper.stripEnumPrefix(rsp.getFanMode())));
      service.updateState(CHANNEL_CUSTOM_FAN_MODE + "_" + rsp.getKey(), endpoint ->
        new StringType(rsp.getCustomFanMode()));
      service.updateState(CHANNEL_PRESET + "_" + rsp.getKey(), endpoint ->
        new StringType(EnumHelper.stripEnumPrefix(rsp.getPreset())));
      service.updateState(CHANNEL_CUSTOM_PRESET + "_" + rsp.getKey(), endpoint ->
        new StringType(rsp.getCustomPreset()));
      service.updateState(CHANNEL_SWING_MODE + "_" + rsp.getKey(), endpoint ->
        new StringType(EnumHelper.stripEnumPrefix(rsp.getSwingMode())));
    }
  }

  private enum ConnectionState {
    // Initial state, no connection
    UNINITIALIZED,
    // TCP connected to ESPHome, first handshake sent
    HELLO_SENT,

    // First handshake received, login sent (with password)
    LOGIN_SENT,

    // Connection established
    CONNECTED
  }
}
