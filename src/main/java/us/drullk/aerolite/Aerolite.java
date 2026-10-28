package us.drullk.aerolite;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import tamaized.beanification.BeanContext;

@Mod(Aerolite.MODID)
public class Aerolite {

    public static final String MODID = "aerolite";
    public static final Logger LOGGER = LogUtils.getLogger();

    static {
        BeanContext.init(MODID);
    }

    public Aerolite(IEventBus modEventBus, ModContainer modContainer) {

    }

}
