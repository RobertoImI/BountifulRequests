package org.crafterscr.bountifulrequests;

import com.mojang.logging.LogUtils;
import org.crafterscr.bountifulrequests.menu.ModMenus;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Punto de entrada de Bountiful Requests Addon.
 *
 * No modificamos Bountiful directamente. Todas las integraciones se hacen
 * usando su API/clases públicas y mixins puntuales.
 */
@Mod(BountifulRequests.MOD_ID)
public final class BountifulRequests {

    public static final String MOD_ID = "bountifulrequests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BountifulRequests(IEventBus modBus) {
        ModMenus.REGISTER.register(modBus);

        LOGGER.info("Bountiful Requests Addon initialized.");
    }
}