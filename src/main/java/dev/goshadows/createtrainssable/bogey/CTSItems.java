package dev.goshadows.createtrainssable.bogey;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of the physics bogey system. */
public final class CTSItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateTrainsSable.MOD_ID);

    public static final DeferredItem<BlockItem> PHYSICS_BOGEY = ITEMS.register("physics_bogey",
            () -> new BlockItem(CTSBlocks.PHYSICS_BOGEY.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> UNPOWERED_PHYSICS_BOGEY =
            ITEMS.register("unpowered_physics_bogey",
                    () -> new BlockItem(CTSBlocks.UNPOWERED_PHYSICS_BOGEY.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> AUTOMATIC_COUPLER = ITEMS.register("automatic_coupler",
            () -> new BlockItem(CTSBlocks.AUTOMATIC_COUPLER.get(), new Item.Properties()));

    private CTSItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
