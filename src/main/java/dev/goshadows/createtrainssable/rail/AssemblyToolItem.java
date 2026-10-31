package dev.goshadows.createtrainssable.rail;

import dev.goshadows.createtrainssable.CreateTrainsSable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Assembles a vehicle into a Sable sub-level, with the block that was clicked as the anchor.
 *
 * <p>This is the step that makes a build physical: the connected structure is gathered, moved into
 * a plot and handed to the physics engine, and from that moment the trucks on it are what hold it
 * on the rails. There is no copy of the vehicle left in the world and no contraption entity created,
 * so there is nothing to keep in sync afterwards.</p>
 *
 * <p>Assembly is refused when the structure is too big or when any part of it is already inside a
 * sub-level, because moving blocks that belong to another body would tear that body apart.</p>
 */
public class AssemblyToolItem extends Item {

    /** Upper bound for one assembly; the same order as Sable's own command. */
    private static final int CAPACITY = 60_000;

    public AssemblyToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // The client only reports the swing; the server decides.
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos origin = context.getClickedPos();

        SubLevelAssemblyHelper.GatherResult gathered =
                SubLevelAssemblyHelper.gatherConnectedBlocks(origin, level, CAPACITY, null);
        switch (gathered.assemblyState()) {
            case NO_BLOCKS -> {
                message(player, "There is nothing connected to assemble.", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            case TOO_MANY_BLOCKS -> {
                message(player, "This structure is too large to assemble (" + CAPACITY + " blocks).",
                        ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            case SUCCESS -> {
                // Fall through: the gather succeeded and the set is usable.
            }
        }

        ServerSubLevel subLevel;
        try {
            subLevel = SubLevelAssemblyHelper.assembleBlocks(level, origin, gathered.blocks(),
                    gathered.boundingBox());
        } catch (RuntimeException e) {
            CreateTrainsSable.LOGGER.error("Could not assemble the vehicle at {}", origin, e);
            message(player, "Assembly failed; see the log.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (subLevel == null) {
            message(player, "Sable refused to create a sub-level here.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }

        // The tool wears out instead of being a free, repeatable action.
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().hurtAndBreak(1, player,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        subLevel.setName("vehicle-" + subLevel.getUniqueId().toString().substring(0, 8));
        CreateTrainsSable.LOGGER.info("Assembled {} blocks at {} into sub-level {}",
                gathered.blocks().size(), origin, subLevel.getUniqueId());
        message(player, "Assembled " + gathered.blocks().size()
                + " blocks. The trucks now hold this vehicle on the rails.", ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }

    private static void message(Player player, String text, ChatFormatting colour) {
        if (player != null) {
            player.displayClientMessage(Component.literal(text).withStyle(colour), true);
        }
    }
}
