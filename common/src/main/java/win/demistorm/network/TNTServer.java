package win.demistorm.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import win.demistorm.Platform;
import win.demistorm.VRThrowingExtensions;

import java.util.HashMap;
import java.util.Map;

import static win.demistorm.VRThrowingExtensions.log;

// Server-side manager for lit TNT timers
// Handles fuse countdowns and explosions for players holding lit TNT
public class TNTServer {

    private static TNTServer instance;

    // Track fuse timers for each player (player to remaining ticks)
    private final Map<ServerPlayer, Integer> tntTimers = new HashMap<>();
    // Track which hand has the lit TNT (player to hand)
    private final Map<ServerPlayer, InteractionHand> tntHands = new HashMap<>();

    // How many ticks before lit TNT explodes
    private static final int TNT_FUSE_TICKS = 100;

    // Explosion power (same as vanilla TNT)
    private static final float EXPLOSION_POWER = 4.0f;

    private TNTServer() {}

    public static TNTServer instance() {
        if (instance == null) {
            instance = new TNTServer();
        }
        return instance;
    }

    // Start TNT fuse timer for a player
    public void startTNTTimer(ServerPlayer player, InteractionHand hand) {
        if (player == null || !player.isAlive()) return;

        tntTimers.put(player, TNT_FUSE_TICKS);
        tntHands.put(player, hand);
        log.debug("[TNTServer] Started {}-tick fuse timer for {} in {}", TNT_FUSE_TICKS, player.getName().getString(), hand);
    }

    // Cancel TNT fuse timer for a player
    public void cancelTNTTimer(ServerPlayer player) {
        if (tntTimers.remove(player) != null) {
            tntHands.remove(player);
            log.debug("[TNTServer] Cancelled fuse timer for {}", player.getName().getString());
        }
    }

    public int getRemainingTicks(ServerPlayer player) {
        return tntTimers.getOrDefault(player, 0);
    }

    public boolean hasActiveTimer(ServerPlayer player) {
        return tntTimers.containsKey(player);
    }

    public void registerTickHandler() {
        Platform.registerServerPlayerPostTickListener(this::updatePlayer);
        log.info("[TNTServer] Registered tick handler for fuse timer updates");
    }

    // Update timer for a single player (called each server tick)
    private void updatePlayer(ServerPlayer player) {
        if (!hasActiveTimer(player)) return;

        // Player died or left, clean up
        if (!player.isAlive()) {
            cancelTNTTimer(player);
            return;
        }

        int remaining = getRemainingTicks(player) - 1;

        if (remaining <= 0) {
            // Fuse expired, explode
            explodeTNT(player);
            cancelTNTTimer(player);
        } else {
            // Update timer
            tntTimers.put(player, remaining);

            if (VRThrowingExtensions.debugMode && remaining % 20 == 0) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("TNT fuse: " + remaining + " ticks"), true);
            }
        }
    }

    // Explode TNT at player's position
    private void explodeTNT(ServerPlayer player) {
        Level level = player.level();

        log.debug("[TNTServer] TNT exploded at {}'s position", player.getName().getString());

        // Create explosion with vanilla TNT force
        level.explode(player, player.getX(), player.getY(), player.getZ(),
                EXPLOSION_POWER, Level.ExplosionInteraction.TNT);

        // Consume 1 TNT from hand if they still have it
        InteractionHand hand = tntHands.getOrDefault(player, InteractionHand.MAIN_HAND);
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.is(Items.TNT)) {
            heldStack.shrink(1);
            if (heldStack.isEmpty()) {
                player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(Items.AIR));
            }
        }

        // Note: Explosion sound will be played by the explosion itself
    }
}
