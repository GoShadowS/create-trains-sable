package dev.goshadows.createtrainssable;

import com.mojang.logging.LogUtils;
import dev.goshadows.createtrainssable.bogey.CTSBlockEntities;
import dev.goshadows.createtrainssable.bogey.CTSBlocks;
import dev.goshadows.createtrainssable.bogey.CTSCreativeTab;
import dev.goshadows.createtrainssable.bogey.CTSItems;
import dev.goshadows.createtrainssable.config.CTSConfig;
import dev.goshadows.createtrainssable.derailment.DerailmentController;
import dev.goshadows.createtrainssable.integration.BackendLoader;
import dev.goshadows.createtrainssable.integration.PhysicsBackend;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CreateTrainsSable.MOD_ID)
public final class CreateTrainsSable {

    public static final String MOD_ID = "createtrainssable";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static volatile DerailmentController controller;

    public CreateTrainsSable(IEventBus modBus, ModContainer modContainer) {
        CTSBlocks.register(modBus);
        CTSBlockEntities.register(modBus);
        CTSItems.register(modBus);
        CTSCreativeTab.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, CTSConfig.SPEC, MOD_ID + "-server.toml");
        PhysicsBackend backend = BackendLoader.load();
        controller = new DerailmentController(backend);
        LOGGER.info("Create Trains Sable ready; physics backend: {}", backend.description());
    }

    /** Null only before FML constructs the mod, which mixins never see in practice. */
    public static DerailmentController controller() {
        return controller;
    }
}
