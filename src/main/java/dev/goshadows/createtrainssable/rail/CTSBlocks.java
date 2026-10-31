package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The three blocks of the rail system.
 *
 * <p>The names are deliberately descriptive of what the block does rather than of how it is made:
 * a driven truck has a motor, a rolling truck does not, and a coupling holds two vehicles together.</p>
 */
public final class CTSBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateTrainsSable.MOD_ID);

    /** Driven axle: rotation in, motion out. */
    public static final DeferredBlock<TruckBlock> TRACTION_TRUCK = BLOCKS.register("traction_truck",
            () -> new TruckBlock(truckProperties(), true));

    /** Idler axle: carries weight, coasts, brakes. */
    public static final DeferredBlock<TruckBlock> ROLLING_TRUCK = BLOCKS.register("rolling_truck",
            () -> new TruckBlock(truckProperties(), false));

    /** Hook that links two vehicles with a spring and a damper. */
    public static final DeferredBlock<SpringCouplingBlock> SPRING_COUPLING =
            BLOCKS.register("spring_coupling", () -> new SpringCouplingBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(2.0f, 6.0f)
                            .sound(SoundType.COPPER)));

    private CTSBlocks() {
    }

    private static BlockBehaviour.Properties truckProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.0f, 9.0f)
                .sound(SoundType.NETHERITE_BLOCK)
                .noOcclusion();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
