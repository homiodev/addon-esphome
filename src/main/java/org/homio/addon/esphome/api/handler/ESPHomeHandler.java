package org.homio.addon.esphome.api.handler;

/**
 * The {@link ESPHomeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Arne Seime - Initial contribution
 */

public class ESPHomeHandler /*extends BaseThingHandler implements PacketListener*/ {

    /*private final ConnectionSelector connectionSelector;
    private final ESPChannelTypeProvider dynamicChannelTypeProvider;
    private @Nullable ESPHomeConfiguration config;
    private @Nullable ESPHomeConnection connection;

    private final List<Channel> dynamicChannels = new ArrayList<>();

    private boolean disposed = false;
    private boolean interrogated;

    @Override
    public void handleRemoval() {
        dynamicChannelTypeProvider.removeChannelTypesForThing(thing.getUID());
        super.handleRemoval();
    }

    @Override
    public synchronized void handleCommand(ChannelUID channelUID, Command command) {

        if (connectionState != ConnectionState.CONNECTED) {
            log.warn("[{}] Not connected, ignoring command {}", config.hostname, command);
            return;
        }

        if (command == RefreshType.REFRESH) {
            try {
                connection.send(SubscribeStatesRequest.getDefaultInstance());
            } catch (ProtocolAPIError e) {
                log.error("[{}] Error sending command {} to channel {}: {}", config.hostname, command, channelUID,
                        e.getMessage());
            }
            return;
        }

        Optional<Channel> optionalChannel = thing.getChannels().stream().filter(e -> e.getUID().equals(channelUID))
                .findFirst();
        optionalChannel.ifPresent(channel -> {
            try {
                String commandClass = (String) channel.getConfiguration().get(BindingConstants.COMMAND_CLASS);
                if (commandClass == null) {
                    log.warn("[{}] No command class for channel {}", config.hostname, channelUID);
                    return;
                }

                AbstractMessageHandler<? extends GeneratedMessage, ? extends GeneratedMessage> abstractMessageHandler = commandTypeToHandlerMap
                        .get(commandClass);
                if (abstractMessageHandler == null) {
                    log.warn("[{}] No message handler for command class {}", config.hostname, commandClass);
                } else {
                    int key = ((BigDecimal) channel.getConfiguration().get(BindingConstants.COMMAND_KEY)).intValue();
                    abstractMessageHandler.handleCommand(channel, command, key);
                }

            } catch (Exception e) {
                log.error("[{}] Error sending command {} to channel {}: {}", config.hostname, command, channelUID,
                        e.getMessage(), e);
            }
        });
    }

    @Override
    public void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
    }

    public void addChannelType(ChannelType channelType) {
        dynamicChannelTypeProvider.putChannelType(channelType);
    }

    public void addChannel(Channel channel) {
        dynamicChannels.add(channel);
    }

    public boolean isInterrogated() {
        return interrogated;
    }

    public List<Channel> getDynamicChannels() {
        return dynamicChannels;
    }*/
}
