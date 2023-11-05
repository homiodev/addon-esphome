package org.homio.addon.esphome.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum SensorNumberDeviceClass {

    GENERIC_NUMBER("generic_number", "Number", "zoom", null),
    ENUM("enum", "String", "text", null),
    TIMESTAMP("timestamp", "DateTime", "time", null),
    APPARENT_POWER("apparent_power", "Number:Power", "energy", null),
    AQI("aqi", "Number", "smoke", null),
    ATMOSPHERIC_PRESSURE("atmospheric_pressure", "Number:Pressure", "pressure", null),
    BATTERY("battery", "Number:Dimensionless", "batterylevel", null),
    CO("carbon_monoxide", "Number:Dimensionless", "smoke", "CO"),
    CO2("carbon_dioxide", "Number:Dimensionless", "carbondioxide", "CO2"),
    CURRENT("current", "Number:ElectricCurrent", "energy", "Current"),

    DATA_RATE("data_rate", "Number:DataTransferRate", null, null),
    DATA_SIZE("data_size", "Number:DataAmount", null, null),
    DISTANCE("distance", "Number:Length", null, null),
    DURATION("duration", "Number:Time", "time", null),
    ENERGY("energy", "Number:Energy", "energy", "Energy"),
    ENERGY_STORAGE("energy_storage", "Number:Energy", "energy", "Energy"),
    FREQUENCY("frequency", "Number:Frequency", null, "Frequency"),

    GAS("gas", "Number:Volume", "gas", "Gas"),
    HUMIDITY("humidity", "Number:Dimensionless", "humidity", "Humidity"),
    ILLUMINANCE("illuminance", "Number:Illuminance", "lightbulb", "Light"),
    IRRADIANCE("irradiance", "Number:Itensity", null, null),
    MOISTURE("moisture", "Number:Dimensionless", "water", "Humidity"),
    MONETARY("monetary", "Number", null, null), // TODO: Add Monetary type
    NITROGEN_DIOXIDE("nitrogen_dioxide", "Number:Dimensionless", "smoke", "null"),
    NITROGEN_MONOXIDE("nitrogen_monoxide", "Number", "smoke", null),
    NITROUS_OXIDE("nitrous_oxide", "Number", "smoke", null),

    OZONE("ozone", "Number", "smoke", null),
    PH("ph", "Number", null, null),
    PM1("pm1", "Number", "smoke", null),
    PM10("pm10", "Number", "smoke", null),
    PM25("pm25", "Number", "smoke", null),
    POWER_FACTOR("power_factor", "Number", "qualityofservice", null),
    POWER("power", "Number:Power", "energy", "Power"),
    PRECIPITATION("precipitation", "Number:Length", "rain", "Rain"),
    PRECIPITATION_RATE("precipitation_rate", "Number:Speed", "rain", "Rain"),
    PRESSURE("pressure", "Number:Pressure", "pressure", "Pressure"),
    REACTIVE_POWER("reactive_power", "Number:Power", "energy", "Power"),
    SIGNAL_STRENGTH("signal_strength", "Number:Power", "qualityofservice", null),

    SOUND_PRESSURE("sound_pressure", "Number:Dimensionless", "soundvolume", "SoundVolume"),
    SPEED("speed", "Number:Speed", "motion", null),
    SULPHUR_DIOXIDE("sulphur_dioxide", "Number", "smoke", null),
    TEMPERATURE("temperature", "Number:Temperature", "temperature", "Temperature"),
    VOLATILE_ORGANIC_COMPOUNDS("volatile_organic_compounds", "Number", "smoke", null),
    VOLATILE_ORGANIC_COMPOUNDS_PARTS("volatile_organic_compounds_parts", "Number:Dimensionless", "smoke", null),
    VOLTAGE("voltage", "Number:ElectricPotential", "energy", "Voltage"),
    VOLUME("volume", "Number:Volume", null, null),
    VOLUME_STORAGE("volume_storage", "Number:Volume", null, null),
    WATER("water", "Number:Volume", "water", "Water"),
    WEIGHT("weight", "Number:Force", null, null),
    WIND_SPEED("wind_speed", "Number:Speed", "wind", "Wind");

    private final String deviceClass;
    private final String itemType;
    private final String category;
    private final String semanticType;

    public static SensorNumberDeviceClass fromDeviceClass(String deviceClass) {
        for (SensorNumberDeviceClass sensorDeviceClass : SensorNumberDeviceClass.values()) {
            if (sensorDeviceClass.getDeviceClass().equals(deviceClass)) {
                return sensorDeviceClass;
            }
        }
        return null;
    }
}
