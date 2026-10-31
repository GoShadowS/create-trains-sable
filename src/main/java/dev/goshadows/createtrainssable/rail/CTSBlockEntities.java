package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entities of the rail system. */
public final class CTSBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateTrainsSable.MOD_ID);

    /** Shared by both trucks: the physics is the same, only the block decides whether it is driven. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TruckBlockEntity>> TRUCK =
            BLOCK_ENTITIES.register("truck",
                    () -> BlockEntityType.Builder.of(TruckBlockEntity::new,
                            CTSBlocks.TRACTION_TRUCK.get(), CTSBlocks.ROLLING_TRUCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpringCouplingBlockEntity>>
            COUPLING = BLOCK_ENTITIES.register("coupling",
            () -> BlockEntityType.Builder.of(SpringCouplingBlockEntity::new,
                    CTSBlocks.SPRING_COUPLING.get()).build(null));

    private CTSBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
