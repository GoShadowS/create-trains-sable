package dev.goshadows.createtrainssable.bogey;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One creative tab for the mod's own blocks. */
public final class CTSCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateTrainsSable.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createtrainssable.main"))
                    .icon(() -> CTSItems.PHYSICS_BOGEY.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CTSItems.PHYSICS_BOGEY.get());
                        output.accept(CTSItems.UNPOWERED_PHYSICS_BOGEY.get());
                        output.accept(CTSItems.AUTOMATIC_COUPLER.get());
                    })
                    .build());

    private CTSCreativeTab() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
