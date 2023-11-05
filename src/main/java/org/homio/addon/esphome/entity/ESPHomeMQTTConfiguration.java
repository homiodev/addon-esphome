package org.homio.addon.esphome.entity;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;

public interface ESPHomeMQTTConfiguration extends HasJsonData {

    String getName();

    @UIField(order = 110, required = true)
    @UIFieldShowOnCondition("return !context.get('compactMode') && context.get('communicator') == 'MQTT'")
    default String getTopicPrefix() {
        return getJsonData("tp", getName());
    }

    default void setTopicPrefix(String value) {
        setJsonData("tp", value);
    }

    @UIField(order = 120, required = true)
    @UIFieldShowOnCondition("return !context.get('compactMode') && context.get('communicator') == 'MQTT'")
    default String getLogPrefix() {
        return getJsonData("lp", getName());
    }

    default void setLogPrefix(String value) {
        setJsonData("lp", value);
    }
}
