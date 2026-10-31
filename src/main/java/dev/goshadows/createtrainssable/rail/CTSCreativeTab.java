package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own creative tab. */
public final class CTSCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateTrainsSable.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createtrainssable.main"))
                    .icon(() -> CTSItems.TRACTION_TRUCK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(CTSItems.TRACTION_TRUCK.get());
                        output.accept(CTSItems.ROLLING_TRUCK.get());
                        output.accept(CTSItems.SPRING_COUPLING.get());
                        output.accept(CTSItems.ASSEMBLY_TOOL.get());
                    })
                    .build());

    private CTSCreativeTab() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
