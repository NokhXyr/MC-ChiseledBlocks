/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Bitsmith.MOD_ID)
public final class Bitsmith {
    public static final String MOD_ID = "bitsmith";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Bitsmith(IEventBus modEventBus) {
        LOGGER.debug("Bitsmith initialising");
    }
}
