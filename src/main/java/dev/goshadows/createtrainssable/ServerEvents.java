package dev.goshadows.createtrainssable;

import dev.goshadows.createtrainssable.derailment.DerailmentController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** All decisions run after Create has ticked its railway manager, on the logical server. */
@EventBusSubscriber(modid = CreateTrainsSable.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        DerailmentController current = CreateTrainsSable.controller();
        if (current == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Create ticks the global railway manager from the overworld only.
        current.tick(level, level.dimension() == Level.OVERWORLD);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DerailmentController current = CreateTrainsSable.controller();
        if (current != null) {
            current.onServerStopped();
        }
    }
}
