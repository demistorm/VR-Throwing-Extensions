package win.demistorm.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.demistorm.ConfigHelper;
import win.demistorm.VRThrowingExtensions;

import java.util.Set;

// Handles block placement when blocks are thrown
public final class PlaceEffect {

    public record BlockThrowResult(boolean shouldHandle, boolean shouldPlaceBlock, boolean throwWholeStack) {
    }

    // Light blocks that can be placed when "Only Place Lights" is enabled
    private static final Set<Item> LIGHT_BLOCKS = Set.of(
            Items.TORCH,
            Items.SOUL_TORCH,
            Items.LANTERN,
            Items.SOUL_LANTERN,
            Items.REDSTONE_TORCH
    );

    public static BlockThrowResult determineBlockThrowLogic(ItemStack stack, net.minecraft.world.entity.player.Player player, boolean useBindHeld, boolean playerCrouched) {
        // Get active config
        ConfigHelper.Data config = ConfigHelper.getActiveConfig(player.getUUID());

        // Check if feature is enabled
        if (!config.placeBlocksOnThrow) {
            return new BlockThrowResult(false, false, false);
        }

        // Check if this is a placeable block
        if (isPlaceableBlock(stack)) {
            return new BlockThrowResult(false, false, false);
        }

        // Check crouch behavior based on config
        boolean shouldFeatureBeActive = switch (config.crouchBehaviorPlaceBlocks) {
            case NORMAL -> !playerCrouched;    // Feature active when NOT crouching
            case INVERTED -> playerCrouched;   // Feature active when crouching
        };

        if (!shouldFeatureBeActive) {
            return new BlockThrowResult(false, false, false);
        }

        // Check if only lights mode is enabled and item isn't a light source
        if (config.onlyPlaceLights && !LIGHT_BLOCKS.contains(stack.getItem())) {
            return new BlockThrowResult(false, false, false);
        }

        // All checks passed, determine placement behavior
        boolean shouldPlace = !useBindHeld;  // Don't place when using bind held
        return new BlockThrowResult(true, shouldPlace, false);
    }

    public static boolean placeBlock(Level level, Player player, ItemStack stack, BlockHitResult hitResult) {
        if (isPlaceableBlock(stack)) {
            return false;
        }

        // Buckets (with placeable liquids) use the dispenser pattern instead of useOn()
        Item item = stack.getItem();
        if (item instanceof DispensibleContainerItem && !(item instanceof BlockItem)) {
            return placeLiquid(level, stack, hitResult);
        }

        try {
            VRThrowingExtensions.log.debug("[PlaceEffect] Attempting to place {} via vanilla useOn()",
                    stack.getItem().getDescriptionId());

            UseOnContext context = new UseOnContext(
                    level,
                    player,
                    InteractionHand.MAIN_HAND,
                    stack,
                    hitResult
            );

            net.minecraft.world.InteractionResult result = stack.getItem().useOn(context);

            boolean success = result.consumesAction();

            if (success) {
                VRThrowingExtensions.log.debug("[PlaceEffect] Successfully placed {} via vanilla useOn()",
                        stack.getItem().getDescriptionId());
            } else {
                VRThrowingExtensions.log.debug("[PlaceEffect] Placement failed or cancelled for {}",
                        stack.getItem().getDescriptionId());
            }

            return success;

        } catch (Exception e) {
            VRThrowingExtensions.log.error("[PlaceEffect] Failed to place item from stack {}", stack, e);
            return false;
        }
    }

    // Place liquid from a bucket item using the same pattern dispensers use
    private static boolean placeLiquid(Level level, ItemStack stack, BlockHitResult hitResult) {
        DispensibleContainerItem container = (DispensibleContainerItem) stack.getItem();
        BlockPos targetPos = hitResult.getBlockPos().relative(hitResult.getDirection());

        try {
            VRThrowingExtensions.log.debug("[PlaceEffect] Attempting to place liquid from {} at {}",
                    stack.getItem().getDescriptionId(), targetPos);

            boolean success = container.emptyContents(null, level, targetPos, hitResult);

            if (success) {
                container.checkExtraContent(null, level, stack, targetPos);
                // Drop empty bucket where the liquid was placed
                Vec3 dropPos = hitResult.getLocation();
                ItemEntity emptyBucket = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z,
                        new ItemStack(Items.BUCKET));
                level.addFreshEntity(emptyBucket);

                VRThrowingExtensions.log.debug("[PlaceEffect] Successfully placed liquid from {}",
                        stack.getItem().getDescriptionId());
            } else {
                VRThrowingExtensions.log.debug("[PlaceEffect] Liquid placement failed for {}",
                        stack.getItem().getDescriptionId());
            }

            return success;

        } catch (Exception e) {
            VRThrowingExtensions.log.error("[PlaceEffect] Failed to place liquid from stack {}", stack, e);
            return false;
        }
    }

    public static boolean isPlaceableBlock(ItemStack stack) {
        Item item = stack.getItem();
        return !(item instanceof BlockItem) && !(item instanceof HangingEntityItem)
                && !(item instanceof DispensibleContainerItem);
    }

    private PlaceEffect() {}
}