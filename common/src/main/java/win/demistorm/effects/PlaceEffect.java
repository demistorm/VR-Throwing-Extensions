package win.demistorm.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.demistorm.ConfigHelper;
import win.demistorm.VRThrowingExtensions;

import java.util.Set;

// Handles block placement when blocks are thrown
public final class PlaceEffect {

    // Result of block throw determination
    public static class BlockThrowResult {
        public final boolean shouldHandle;      // Should PlaceEffect handle this throw?
        public final boolean shouldPlaceBlock;  // Should it place on impact?
        public final boolean throwWholeStack;

        public BlockThrowResult(boolean shouldHandle, boolean shouldPlaceBlock, boolean throwWholeStack) {
            this.shouldHandle = shouldHandle;
            this.shouldPlaceBlock = shouldPlaceBlock;
            this.throwWholeStack = throwWholeStack;
        }
    }

    // Hardcoded list of light blocks
    private static final Set<Item> LIGHT_BLOCKS = Set.of(
        Items.TORCH,
        Items.SOUL_TORCH,
        Items.LANTERN,
        Items.SOUL_LANTERN,
        Items.REDSTONE_TORCH
    );

    // Determine block throw logic
    public static BlockThrowResult determineBlockThrowLogic(ItemStack stack, boolean useBindHeld, boolean playerCrouched) {
        // Check 1: Is the feature enabled in config?
        if (!ConfigHelper.ACTIVE.placeBlocksOnThrow) {
            return new BlockThrowResult(false, false, false);
        }

        // Check 2: Is this a placeable block?
        if (!isPlaceableBlock(stack)) {
            return new BlockThrowResult(false, false, false);
        }

        // Check 3: Is player crouching? (Use existing projectile logic)
        if (playerCrouched) {
            return new BlockThrowResult(false, false, false);
        }

        // Check 4: If only lights mode is enabled, check if in list
        if (ConfigHelper.ACTIVE.onlyPlaceLights && !LIGHT_BLOCKS.contains(stack.getItem())) {
            return new BlockThrowResult(false, false, false);
        }

        // All checks passed (determine placement behavior)
        boolean shouldPlace = !useBindHeld;  // Place unless useBindHeld
        return new BlockThrowResult(true, shouldPlace, false);
    }

    // Place a block at the hit position
    public static boolean placeBlock(Level level, ItemStack stack, BlockHitResult hitResult, Vec3 impactPos) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        try {
            // Get the block to place
            Block block = blockItem.getBlock();
            BlockState blockState = block.defaultBlockState();

            // Calculate placement position based on hit face
            BlockPos hitPos = hitResult.getBlockPos();
            Direction face = hitResult.getDirection();
            BlockPos placePos = hitPos.relative(face);

            // Placement validation
            if (!level.getBlockState(placePos).canBeReplaced()) {
                VRThrowingExtensions.log.debug("[PlaceEffect] Cannot place block at {} - not replaceable", placePos);
                return false;
            }

            if (!blockState.canSurvive(level, placePos)) {
                VRThrowingExtensions.log.debug("[PlaceEffect] Cannot place block at {} - cannot survive", placePos);
                return false;
            }

            // Place the block
            level.setBlockAndUpdate(placePos, blockState);

            VRThrowingExtensions.log.debug("[PlaceEffect] Placed block {} at {}", blockItem.getDescriptionId(), placePos);
            return true;

        } catch (Exception e) {
            VRThrowingExtensions.log.error("[PlaceEffect] Failed to place block from stack {}", stack, e);
            return false;
        }
    }

    // Check if an item is a placeable block
    public static boolean isPlaceableBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    private PlaceEffect() {}
}