package org.homio.addon.esphome.entity;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldTab;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.util.SecureString;

public interface ESPHomeNativeApiConfiguration extends HasJsonData {

    @UIField(order = 1)
    @UIFieldPort
    @UIFieldTab("NATIVE_API")
    @UIFieldShowOnCondition("return !context.get('compactMode') && context.get('communicator') != 'MQTT'")
    default SecureString getNativeApiPassword() {
        return getJsonSecure("napwd", "");
    }

    default void setNativeApiPassword(String value) {
        setJsonDataSecure("napwd", value);
    }

    @UIField(order = 2)
    @UIFieldPort
    @UIFieldTab("NATIVE_API")
    @UIFieldShowOnCondition("return !context.get('compactMode') && context.get('communicator') != 'MQTT'")
    default int getNativeApiPort() {
        return getJsonData("nap", 6053);
    }

    default void setNativeApiPort(int value) {
        setJsonData("nap", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 10, max = 60)
    @UIFieldTab("NATIVE_API")
    @UIFieldShowOnCondition("return !context.get('compactMode') && context.get('communicator') != 'MQTT'")
    default int getPingInterval() {
        return getJsonData("pi", 10);
    }

    default void setPingInterval(int value) {
        setJsonData("pi", value);
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 1, max = 10)
    @UIFieldTab("NATIVE_API")
    @UIFieldShowOnCondition("return !context.get('compactMode') && context.get('communicator') != 'MQTT'")
    default int getMaxPingTimeout() {
        return getJsonData("mpt", 4);
    }

    default void setMaxPingTimeout(int value) {
        setJsonData("mpt", value);
    }
}
