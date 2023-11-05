package org.homio.addon.esphome;

import lombok.Getter;
import org.homio.addon.esphome.entity.ESPHomeDeviceEntity;
import org.homio.api.Context;
import org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ESPHomeBlocks extends Scratch3BaseDeviceBlocks {

    public Scratch3ESPHomeBlocks(Context context, ESPHomeEntrypoint entrypoint) {
        super("#8D8D8D", context, entrypoint, ESPHomeDeviceEntity.PREFIX);
    }
}
