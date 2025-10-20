package win.demistorm.network;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import win.demistorm.ModCompat;
import win.demistorm.ThrownProjectileEntity;

import static win.demistorm.VRThrowingExtensions.log;

// Processes incoming network packets
public final class NetworkHandlers {

    // Client threw something
    public static void handleThrow(Player player, ThrowData data) {
        if (player == null || !player.isAlive()) return;

        ItemStack heldStack = player.getMainHandItem();
        if (heldStack.isEmpty() || ModCompat.throwingDisabled(heldStack)) return;

        ThrownProjectileEntity proj = new ThrownProjectileEntity(
                player.level(), player, heldStack, data.wholeStack());

        // Make sure item syncs properly on first spawn
        proj.setItem(heldStack.copyWithCount(1));

        Vec3 pos = new Vec3(data.posX(), data.posY(), data.posZ());
        Vec3 vel = new Vec3(data.velX(), data.velY(), data.velZ());
        proj.setPos(pos);
        proj.setOriginalThrowPos(pos);
        proj.setDeltaMovement(vel);
        proj.setHandRoll(data.rollDeg());

        log.debug("[Server] Spawning thrown proj {} with item {}", proj.getId(), proj.getItem());

        player.level().addFreshEntity(proj);

        float attackDamage = ThrownProjectileEntity.stackBaseDamage(heldStack);
        log.debug("[Network] Thrown item attack damage = {}", attackDamage);

        if (attackDamage <= 1.0F) {
            if (!player.level().isClientSide()) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.6f, 1.05f);
            }
        } else {
            if (!player.level().isClientSide()) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.6f, 1.33f);
            }
        }

        if (data.wholeStack()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            if (heldStack.getCount() > 1) heldStack.shrink(1);
            else player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    // Client started or stopped catching
    public static void handleCatch(Player player, CatchData data) {
        if (player == null || !player.isAlive()) return;

        ServerLevel world = (ServerLevel) player.level();
        if (!(world.getEntity(data.entityId()) instanceof ThrownProjectileEntity projectile)) {
            return;
        }

        if (data.startCatch()) {
            projectile.startCatch();
        } else {
            projectile.cancelCatch();
        }
    }

    // Client updated catch velocity (catch effect)
    public static void handleCatchUpdate(Player player, CatchUpdateData data) {
        if (player == null || !player.isAlive()) return;

        ServerLevel world = (ServerLevel) player.level();
        if (!(world.getEntity(data.entityId()) instanceof ThrownProjectileEntity projectile)) {
            return;
        }

        if (!projectile.isCatching()) return;

        // Pull projectile toward hand
        Vec3 newVel = new Vec3(data.velX(), data.velY(), data.velZ());
        projectile.setDeltaMovement(newVel);

        // Update hand rotation
        projectile.setHandRoll(data.rollDeg());
    }

    // Client caught the projectile
    public static void handleCatchComplete(Player player, CatchCompleteData data) {
        if (player == null || !player.isAlive()) return;

        ServerLevel world = (ServerLevel) player.level();
        if (!(world.getEntity(data.entityId()) instanceof ThrownProjectileEntity projectile)) {
            return;
        }

        if (!projectile.isCatching()) return;

        // Check hand is empty
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) return;

        // Get projectile's item
        ItemStack projectileStack = projectile.getItem();
        int stackSize = projectile.getStackSize();

        // Play catch sound
        if (!player.level().isClientSide()) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.5f, 2.0f);
        }

        // Return item to player
        ItemStack giveStack = projectileStack.copy();
        giveStack.setCount(stackSize);
        player.setItemInHand(InteractionHand.MAIN_HAND, giveStack);

        // Remove projectile
        projectile.discard();
    }

    // Show blood particle effects
    public static void handleBloodParticle(Player player, BloodParticleData data) {
        // Spawn particles on client
        if (player != null && player.level().isClientSide()) {
            win.demistorm.client.particles.BloodParticle.spawnParticles(data);
        }
        log.debug("[Network] Received blood particle packet at ({}, {}, {})",
                data.posX(), data.posY(), data.posZ());
    }

    // Show bleeding particle effects
    public static void handleBleedingParticle(Player player, BleedingParticleData data) {
        // Spawn bleeding particles on client
        if (player != null && player.level().isClientSide()) {
            win.demistorm.client.particles.BleedingParticle.spawnBleedingParticles(data);
        }
        log.debug("[Network] Received bleeding particle packet at ({}, {}, {})",
                data.posX(), data.posY(), data.posZ());
    }

    // Got config settings from server
    public static void handleConfigSync(Player player, ConfigSyncData data) {
        // Update client config
        win.demistorm.ConfigHelper.clientReceivedRemote(data.json());
        log.debug("[Network] Received config sync packet for player: {}", player.getName().getString());
    }
}
