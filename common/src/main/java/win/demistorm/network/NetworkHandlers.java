package win.demistorm.network;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import win.demistorm.ConfigHelper;
import win.demistorm.ModCompat;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.ThrownTNTEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.effects.ProjectileEffect;
import win.demistorm.effects.PlaceEffect;
import win.demistorm.network.data.*;

import static win.demistorm.VRThrowingExtensions.log;

// Processes incoming network packets
public final class NetworkHandlers {

    // Client threw something
    public static void handleThrow(Player player, ThrowData data) {
        if (player == null || !player.isAlive()) return;

        ItemStack heldStack = data.hand() == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
        if (heldStack.isEmpty() || ModCompat.throwingDisabled(heldStack, player, data.playerCrouched(), data.useBindHeld())) return;

        // PlaceEffect logic
        PlaceEffect.BlockThrowResult blockResult = PlaceEffect.determineBlockThrowLogic(heldStack, player, data.useBindHeld(), data.playerCrouched());

        if (blockResult.shouldHandle()) {
            // PlaceEffect will handle this throw
            Vec3 origin = new Vec3(data.posX(), data.posY(), data.posZ());
            Vec3 velocity = new Vec3(data.velX(), data.velY(), data.velZ());

            ThrownProjectileEntity proj = new ThrownProjectileEntity(
                player.level(), player, heldStack, blockResult.throwWholeStack(),
                data.useBindHeld(), data.playerCrouched(), blockResult.shouldPlaceBlock()
            );

            // Make sure item syncs properly on first spawn
            proj.setItem(heldStack.copyWithCount(1));

            proj.setPos(origin);
            proj.setOriginalThrowPos(origin);
            proj.setDeltaMovement(velocity);
            proj.setHandRoll(data.rollDeg());

            log.debug("[Server] Spawning block placement proj {} with item {} (placement: {})",
                proj.getId(), proj.getItem(), blockResult.shouldPlaceBlock());

            player.level().addFreshEntity(proj);

            // Play throw sound
            if (!player.level().isClientSide()) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.6f, 1.05f);
            }

            // Update player inventory
            if (blockResult.throwWholeStack()) {
                player.setItemInHand(data.hand(), ItemStack.EMPTY);
            } else {
                if (heldStack.getCount() > 1) heldStack.shrink(1);
                else player.setItemInHand(data.hand(), ItemStack.EMPTY);
            }
            return;
        }

        // If PlaceEffect didn't handle it, use existing projectile logic
        ProjectileEffect.ThrowBehavior behavior = ProjectileEffect.determineThrowBehavior(
            heldStack, player, data.useBindHeld(), data.playerCrouched());

        Vec3 origin = new Vec3(data.posX(), data.posY(), data.posZ());
        Vec3 velocity = new Vec3(data.velX(), data.velY(), data.velZ());

        switch (behavior) {
            case VANILLA_PROJECTILE -> handleVanillaProjectile(player, heldStack, data);
            case CUSTOM_PROJECTILE_SINGLE -> handleCustomProjectile(player, heldStack, origin, velocity, data.rollDeg(), false, data.useBindHeld(), data.playerCrouched(), data.hand());
            case CUSTOM_PROJECTILE_WHOLE_STACK -> handleCustomProjectile(player, heldStack, origin, velocity, data.rollDeg(), true, data.useBindHeld(), data.playerCrouched(), data.hand());
        }
    }

    // Handle vanilla projectile behavior with VR position/velocity
    private static void handleVanillaProjectile(Player player, ItemStack heldStack, ThrowData data) {
        log.debug("[Network] Handling vanilla projectile for item: {}", heldStack);

        // Start tracking before item use
        ProjectileEffect.ItemProjectileDetector.startTracking(
            (ServerPlayer) player, data.useBindHeld(), data.playerCrouched(),
            new Vec3(data.posX(), data.posY(), data.posZ()),
            new Vec3(data.velX(), data.velY(), data.velZ()),
            data.rollDeg()
        );

        // Call vanilla item use
        InteractionResult result = heldStack.use(player.level(), player, InteractionHand.MAIN_HAND);
        if (result != InteractionResult.PASS) {
            // Item was consumed or changed, update the hand
            ItemStack newStack = player.getMainHandItem();
            if (newStack.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }

        // Check for spawned projectiles and redirect after one tick
        if (player.getServer() != null) {
            player.getServer().getTickCount(); // Access server to check if available
            // Schedule the interception for next tick
            ProjectileEffect.ItemProjectileDetector.interceptAndRedirect((ServerPlayer) player);
        }
    }

    // Handle custom projectile (ThrownProjectileEntity)
    private static void handleCustomProjectile(Player player, ItemStack heldStack, Vec3 origin, Vec3 velocity, float rollDeg, boolean wholeStack, boolean useBindHeld, boolean playerCrouched, InteractionHand hand) {
        log.debug("[Network] Handling custom projectile for item: {}", heldStack);

        ThrownProjectileEntity proj = new ThrownProjectileEntity(player.level(), player, heldStack, wholeStack, useBindHeld, playerCrouched);

        // Make sure item syncs properly on first spawn
        proj.setItem(heldStack.copyWithCount(1));

        proj.setPos(origin);
        proj.setOriginalThrowPos(origin);
        proj.setDeltaMovement(velocity);
        proj.setHandRoll(rollDeg);

        log.debug("[Server] Spawning thrown proj {} with item {}", proj.getId(), proj.getItem());

        player.level().addFreshEntity(proj);

        float attackDamage = ThrownProjectileEntity.stackBaseDamage(heldStack);
        log.debug("[Network] Thrown item attack damage = {}", attackDamage);

        if (!player.level().isClientSide()) {
            if (attackDamage <= 1.0F) {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.6f, 1.05f);
            } else {
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.6f, 1.33f);
            }
        }

        // Update player inventory
        if (wholeStack) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            if (heldStack.getCount() > 1) heldStack.shrink(1);
            else player.setItemInHand(hand, ItemStack.EMPTY);
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
            projectile.startCatch(data.hand());
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

        // Get the hand that was catching (from projectile data)
        InteractionHand hand = projectile.getCatchingHand();

        // Check hand is empty
        ItemStack handItem = player.getItemInHand(hand);
        if (!handItem.isEmpty()) return;

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
        player.setItemInHand(hand, giveStack);

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

    // Got player config from client (non-authoritative mode)
    public static void handlePlayerConfig(Player player, PlayerConfigData data) {
        if (!(player instanceof ServerPlayer)) return;

        if (ConfigHelper.ACTIVE.serverAuthoritative) {
            log.debug("[Network] Ignored player config from {} (server is authoritative)", player.getName().getString());
            return;
        }

        // Store player config
        ConfigHelper.Data playerConfig = new ConfigHelper.Data();
        playerConfig.weaponEffect = data.weaponEffect();
        playerConfig.throwableProjectiles = data.throwableProjectiles();
        playerConfig.crouchBehaviorProjectiles = data.crouchBehaviorProjectiles();
        playerConfig.placeBlocksOnThrow = data.placeBlocksOnThrow();
        playerConfig.crouchBehaviorPlaceBlocks = data.crouchBehaviorPlaceBlocks();
        playerConfig.onlyPlaceLights = data.onlyPlaceLights();
        playerConfig.immersiveMCThrowables = data.immersiveMCThrowables();
        playerConfig.throwConflictingItems = data.throwConflictingItems();

        ConfigHelper.storePlayerConfig(player.getUUID(), playerConfig);
        log.debug("[Network] Stored config for player: {}", player.getName().getString());
    }

    // Client lit TNT with flint & steel swipe
    public static void handleTNTLit(Player player) {
        if (player == null || !player.isAlive()) return;

        log.debug("[Network] {} lit TNT with flint & steel", player.getName().getString());

        // Start the fuse timer
        TNTServer.instance().startTNTTimer((ServerPlayer) player, InteractionHand.MAIN_HAND);

        if (VRThrowingExtensions.debugMode) {
            player.displayClientMessage(Component.literal("TNT lit! Throw it or BOOM!"), true);
        }
    }

    // Client threw lit TNT with flint & steel
    public static void handleThrowTNT(Player player, ThrowTNTData data) {
        if (player == null || !player.isAlive()) return;

        log.debug("[Network] {} threw lit TNT", player.getName().getString());

        // Get remaining fuse ticks from server timer
        int remainingTicks = TNTServer.instance().getRemainingTicks((ServerPlayer) player);

        if (remainingTicks <= 0) {
            log.warn("[Network] Received throw TNT packet but no active timer for {}",
                    player.getName().getString());
            return;
        }

        // Check player is still holding TNT in the correct hand
        ItemStack heldStack = player.getItemInHand(data.hand());
        if (!heldStack.is(net.minecraft.world.item.Items.TNT)) {
            log.warn("[Network] Player {} tried to throw TNT but not holding TNT",
                    player.getName().getString());
            TNTServer.instance().cancelTNTTimer((ServerPlayer) player);
            return;
        }

        // Spawn ThrownTNTEntity with custom properties
        ServerLevel level = (ServerLevel) player.level();
        ThrownTNTEntity tnt = new ThrownTNTEntity(VRThrowingExtensions.THROWN_TNT_TYPE, level);

        // Set position, velocity, and data
        tnt.setPos(data.posX(), data.posY(), data.posZ());
        tnt.setDeltaMovement(data.velX(), data.velY(), data.velZ());
        tnt.setFuse(remainingTicks);
        tnt.setHandRoll(data.rollDeg());

        // Spawn the TNT
        level.addFreshEntity(tnt);

        // Play throw sound
        level.playSound(null, player.blockPosition(),
                SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.6f, 1.05f);

        // Consume 1 TNT from player's hand
        if (heldStack.getCount() > 1) {
            heldStack.shrink(1);
        } else {
            player.setItemInHand(data.hand(), ItemStack.EMPTY);
        }

        // Cancel the fuse timer (TNT is now thrown entity with its own fuse)
        TNTServer.instance().cancelTNTTimer((ServerPlayer) player);

        log.debug("[Network] Spawned lit PrimedTnt with {} tick fuse at ({}, {}, {})",
                remainingTicks, data.posX(), data.posY(), data.posZ());

        if (VRThrowingExtensions.debugMode) {
            player.displayClientMessage(
                    Component.literal("Thrown lit TNT with " + remainingTicks + " tick fuse!"), true);
        }
    }
}
