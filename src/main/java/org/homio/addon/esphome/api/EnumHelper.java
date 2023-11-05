package org.homio.addon.esphome.api;

import io.esphome.api.ClimateFanMode;
import io.esphome.api.ClimateMode;
import io.esphome.api.ClimatePreset;
import io.esphome.api.ClimateSwingMode;

public class EnumHelper {

    public static String stripEnumPrefix(ClimateSwingMode mode) {
        String toRemove = "CLIMATE_SWING";
        return mode.toString().substring(toRemove.length() + 1);
    }

    public static String stripEnumPrefix(ClimateFanMode mode) {
        String toRemove = "CLIMATE_FAN";
        return mode.toString().substring(toRemove.length() + 1);
    }

    public static String stripEnumPrefix(ClimateMode climateMode) {
        String toRemove = "CLIMATE_MODE";
        return climateMode.toString().substring(toRemove.length() + 1);
    }

    public static String stripEnumPrefix(ClimatePreset climatePreset) {
        String toRemove = "CLIMATE_PRESET";
        return climatePreset.toString().substring(toRemove.length() + 1);
    }
}
