package win.demistorm.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import win.demistorm.Platform;
import win.demistorm.VRThrowingExtensions;

import java.util.HashMap;
import java.util.Map;

import static win.demistorm.VRThrowingExtensions.log;

// Server-side manager for lit TNT timers
// Handles fuse countdowns and explosions for players holding lit TNT
public class TNTServer {

    private static TNTServer instance;

    // Track fuse timers for each player (player → remaining ticks)
    private final Map<ServerPlayer, Integer> tntTimers = new HashMap<>();

    // How many ticks before lit TNT explodes (slightly longer than vanilla 80)
    private static final int TNT_FUSE_TICKS = 100;

    // Explosion power (same as vanilla TNT)
    private static final float EXPLOSION_POWER = 4.0f;

    private TNTServer() {}

    // Get singleton instance
    public static TNTServer instance() {
        if (instance == null) {
            instance = new TNTServer();
        }
        return instance;
    }

    // Start TNT fuse timer for a player
    public void startTNTTimer(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;

        tntTimers.put(player, TNT_FUSE_TICKS);
        log.debug("[TNTServer] Started {}-tick fuse timer for {}", TNT_FUSE_TICKS, player.getName().getString());

        // Note: TNT ignition sound would play here but SoundEvents.TNT_PRIMED is a Holder<SoundEvent>
        // The PrimedTnt entity will play its own sound when spawned/thrown
    }

    // Cancel TNT fuse timer for a player
    public void cancelTNTTimer(ServerPlayer player) {
        if (tntTimers.remove(player) != null) {
            log.debug("[TNTServer] Cancelled fuse timer for {}", player.getName().getString());
        }
    }

    // Get remaining fuse ticks for a player (returns 0 if no active timer)
    public int getRemainingTicks(ServerPlayer player) {
        return tntTimers.getOrDefault(player, 0);
    }

    // Check if player has an active TNT timer
    public boolean hasActiveTimer(ServerPlayer player) {
        return tntTimers.containsKey(player);
    }

    // Register server tick handler to update timers
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

            // Debug: show remaining time every 20 ticks (1 second)
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
        // Note: Using TNT explosion type which doesn't destroy blocks as aggressively
        level.explode(player, player.getX(), player.getY(), player.getZ(),
                EXPLOSION_POWER, Level.ExplosionInteraction.TNT);

        // Consume 1 TNT from player's main hand if they still have it
        if (player.getMainHandItem().is(Items.TNT)) {
            player.getMainHandItem().shrink(1);
            if (player.getMainHandItem().isEmpty()) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        // Note: Explosion sound will be played by the explosion itself
    }
}
