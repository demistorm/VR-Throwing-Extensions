package win.demistorm.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import win.demistorm.ConfigHelper;
import win.demistorm.VRThrowingExtensions;

import java.lang.reflect.Method;
import java.util.Set;

// Handles block placement when blocks are thrown
public final class PlaceEffect {

    // Reflection cache for accessing protected fields
    private static java.lang.reflect.Field wallBlockField = null;
    private static boolean reflectionInitialized = false;

    private static void initReflection() {
        if (reflectionInitialized) return;

        try {
            // Access the protected wallBlock field from StandingAndWallBlockItem
            wallBlockField = StandingAndWallBlockItem.class.getDeclaredField("wallBlock");
            wallBlockField.setAccessible(true);
            VRThrowingExtensions.log.debug("[PlaceEffect] Successfully initialized reflection for wallBlock field");
        } catch (Exception e) {
            VRThrowingExtensions.log.error("[PlaceEffect] Failed to initialize reflection", e);
            wallBlockField = null;
        }

        reflectionInitialized = true;
    }

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

    // Light blocks that can be placed when "Only Place Lights" is enabled
    private static final Set<Item> LIGHT_BLOCKS = Set.of(
        Items.TORCH,
        Items.SOUL_TORCH,
        Items.LANTERN,
        Items.SOUL_LANTERN,
        Items.REDSTONE_TORCH
    );

    public static BlockThrowResult determineBlockThrowLogic(ItemStack stack, boolean useBindHeld, boolean playerCrouched) {
        // Check if feature is enabled
        if (!ConfigHelper.ACTIVE.placeBlocksOnThrow) {
            return new BlockThrowResult(false, false, false);
        }

        // Check if this is a placeable block
        if (!isPlaceableBlock(stack)) {
            return new BlockThrowResult(false, false, false);
        }

        // Respect existing throw behavior - crouching prevents placement
        if (playerCrouched) {
            return new BlockThrowResult(false, false, false);
        }

        // Check if only lights mode is enabled and item isn't a light source
        if (ConfigHelper.ACTIVE.onlyPlaceLights && !LIGHT_BLOCKS.contains(stack.getItem())) {
            return new BlockThrowResult(false, false, false);
        }

        // All checks passed - determine placement behavior
        boolean shouldPlace = !useBindHeld;  // Don't place when using bind held
        return new BlockThrowResult(true, shouldPlace, false);
    }

    public static boolean placeBlock(Level level, Player player, ItemStack stack, BlockHitResult hitResult, Vec3 impactPos) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }

        try {
            BlockPos hitPos = hitResult.getBlockPos();
            Direction face = hitResult.getDirection();
            BlockPos placePos = hitPos.relative(face);

            VRThrowingExtensions.log.debug("[PlaceEffect] Attempting to place {} on face {}",
                blockItem.getDescriptionId(), face);

            // Check if target position can be replaced
            if (!level.getBlockState(placePos).canBeReplaced()) {
                VRThrowingExtensions.log.debug("[PlaceEffect] Cannot place block at {} - not replaceable", placePos);
                return false;
            }

            BlockPlaceContext context = new BlockPlaceContext(
                player,
                InteractionHand.MAIN_HAND,
                stack,
                hitResult
            );

            initReflection();

            BlockState blockState = null;

            if (blockItem instanceof StandingAndWallBlockItem && wallBlockField != null) {
                // Use reflection to get the wallBlock field and implement wall/floor logic
                try {
                    StandingAndWallBlockItem standingBlockItem = (StandingAndWallBlockItem) blockItem;
                    Block wallBlock = (Block) wallBlockField.get(standingBlockItem);

                    Direction hitFace = hitResult.getDirection();

                    // Replicate StandingAndWallBlockItem placement logic
                    for (Direction direction : context.getNearestLookingDirections()) {
                        if (direction != Direction.UP) { // attachmentDirection is UP for torches
                            BlockState proposedState;

                            if (direction == Direction.UP) {
                                // Floor placement - use standing block
                                proposedState = standingBlockItem.getBlock().getStateForPlacement(context);
                            } else {
                                // Wall placement - use wall block
                                proposedState = wallBlock.getStateForPlacement(context);
                            }

                            if (proposedState != null && proposedState.canSurvive(level, placePos)) {
                                blockState = proposedState;
                                VRThrowingExtensions.log.debug("[PlaceEffect] Using custom wall/floor logic for {}", blockItem.getDescriptionId());
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    VRThrowingExtensions.log.error("[PlaceEffect] Failed to access wallBlock via reflection", e);
                    blockState = null;
                }
            } else {
                // Standard block placement
                blockState = blockItem.getBlock().getStateForPlacement(context);
            }

            // Fallback to default if placement failed
            if (blockState == null) {
                blockState = blockItem.getBlock().defaultBlockState();
                VRThrowingExtensions.log.debug("[PlaceEffect] Using default block state as fallback");
            }

            if (!blockState.canSurvive(level, placePos)) {
                VRThrowingExtensions.log.debug("[PlaceEffect] Cannot place block at {} - cannot survive", placePos);
                return false;
            }

            level.setBlockAndUpdate(placePos, blockState);

            VRThrowingExtensions.log.debug("[PlaceEffect] Placed block {} at {}", blockItem.getDescriptionId(), placePos);
            return true;

        } catch (Exception e) {
            VRThrowingExtensions.log.error("[PlaceEffect] Failed to place block from stack {}", stack, e);
            return false;
        }
    }

    public static boolean isPlaceableBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    private PlaceEffect() {}
}