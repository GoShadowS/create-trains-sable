package dev.goshadows.createtrainssable.bogey;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entities of the physics bogey system. */
public final class CTSBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateTrainsSable.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicsBogeyBlockEntity>>
            PHYSICS_BOGEY = BLOCK_ENTITIES.register("physics_bogey",
            () -> BlockEntityType.Builder.of(PhysicsBogeyBlockEntity::new,
                    CTSBlocks.PHYSICS_BOGEY.get(), CTSBlocks.UNPOWERED_PHYSICS_BOGEY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomaticCouplerBlockEntity>>
            AUTOMATIC_COUPLER = BLOCK_ENTITIES.register("automatic_coupler",
            () -> BlockEntityType.Builder.of(AutomaticCouplerBlockEntity::new,
                    CTSBlocks.AUTOMATIC_COUPLER.get()).build(null));

    private CTSBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
