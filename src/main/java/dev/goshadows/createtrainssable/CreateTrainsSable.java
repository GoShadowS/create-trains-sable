package dev.goshadows.createtrainssable;

import com.mojang.logging.LogUtils;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.rail.CTSBlockEntities;
import dev.goshadows.createtrainssable.rail.CTSBlocks;
import dev.goshadows.createtrainssable.rail.CTSCreativeTab;
import dev.goshadows.createtrainssable.rail.CTSItems;
import dev.goshadows.createtrainssable.selftest.SelfTest;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Entry point.
 *
 * <p>The mod has one job: a vehicle built by the player becomes a Sable structure and is driven
 * through the wheel/rail contact of its trucks. There is no second representation of a train, no
 * conversion step and nothing to keep in sync, so the mod only registers its blocks, its items and
 * its configuration here.</p>
 */
@Mod(CreateTrainsSable.MOD_ID)
public final class CreateTrainsSable {

    public static final String MOD_ID = "createtrainssable";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateTrainsSable(IEventBus modBus, ModContainer modContainer) {
        CTSBlocks.register(modBus);
        CTSBlockEntities.register(modBus);
        CTSItems.register(modBus);
        CTSCreativeTab.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, CTSConfig.SPEC, MOD_ID + "-server.toml");
        if (Boolean.getBoolean(SelfTest.ENABLED_PROPERTY)) {
            // Only the physics harness asks for this; a normal game never registers the listeners.
            NeoForge.EVENT_BUS.addListener(SelfTest::onServerStarted);
            NeoForge.EVENT_BUS.addListener(SelfTest::onServerTick);
            LOGGER.info("Create Trains Sable: physics self-test armed");
        }
        LOGGER.info("Create Trains Sable: rail contact ready; trucks drive their own body");
    }
}
