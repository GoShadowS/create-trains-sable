package dev.goshadows.createtrainssable.bogey;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks of the physics bogey system. */
public final class CTSBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateTrainsSable.MOD_ID);

    public static final DeferredBlock<PhysicsBogeyBlock> PHYSICS_BOGEY = BLOCKS.register("physics_bogey",
            () -> new PhysicsBogeyBlock(bogeyProperties(), true));

    public static final DeferredBlock<PhysicsBogeyBlock> UNPOWERED_PHYSICS_BOGEY =
            BLOCKS.register("unpowered_physics_bogey",
                    () -> new PhysicsBogeyBlock(bogeyProperties(), false));

    public static final DeferredBlock<AutomaticCouplerBlock> AUTOMATIC_COUPLER =
            BLOCKS.register("automatic_coupler",
                    () -> new AutomaticCouplerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.COPPER)));

    private CTSBlocks() {
    }

    private static BlockBehaviour.Properties bogeyProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 9.0f)
                .sound(SoundType.NETHERITE_BLOCK)
                .noOcclusion();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    public static Block[] all() {
        return new Block[]{PHYSICS_BOGEY.get(), UNPOWERED_PHYSICS_BOGEY.get(), AUTOMATIC_COUPLER.get()};
    }
}
