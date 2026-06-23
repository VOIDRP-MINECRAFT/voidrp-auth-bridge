package ru.voidrp.authbridge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voidrp.authbridge.bootstrap.ModBootstrap;

@Mod(VoidRpAuthBridge.MODID)
public final class VoidRpAuthBridge {
    public static final String MODID = "voidrp_auth_bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(VoidRpAuthBridge.class);

    private final ModBootstrap bootstrap;

    public VoidRpAuthBridge(IEventBus modBus) {
        this.bootstrap = ModBootstrap.createDefault();
        this.bootstrap.initialize(modBus);
        LOGGER.info("{} skeleton loaded", MODID);
    }

    public ModBootstrap bootstrap() {
        return bootstrap;
    }
}
