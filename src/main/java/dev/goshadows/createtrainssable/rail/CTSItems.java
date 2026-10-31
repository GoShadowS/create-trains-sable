package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of the rail system. */
public final class CTSItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateTrainsSable.MOD_ID);

    public static final DeferredItem<BlockItem> TRACTION_TRUCK = ITEMS.register("traction_truck",
            () -> new BlockItem(CTSBlocks.TRACTION_TRUCK.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> ROLLING_TRUCK = ITEMS.register("rolling_truck",
            () -> new BlockItem(CTSBlocks.ROLLING_TRUCK.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SPRING_COUPLING = ITEMS.register("spring_coupling",
            () -> new BlockItem(CTSBlocks.SPRING_COUPLING.get(), new Item.Properties()));

    /** Turns a built vehicle into a Sable sub-level. */
    public static final DeferredItem<AssemblyToolItem> ASSEMBLY_TOOL = ITEMS.register("assembly_tool",
            () -> new AssemblyToolItem(new Item.Properties().stacksTo(1).durability(64)));

    private CTSItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
