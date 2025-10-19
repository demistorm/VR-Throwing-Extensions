package win.demistorm.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import win.demistorm.ThrownProjectileEntity;

import static win.demistorm.VRThrowingExtensions.log;

// Makes weapons stick in entities and cause bleeding damage
public final class EmbeddingEffect {

    // Final roll angle after weapon sticks in target
    public static final float targetRollDegX = 15.0f;
    // How fast roll angle settles per tick
    public static final float rollApproachPerTick = 20.0f;
    private static final float forwardSpinSpeedDegPerTick = 15.0f;
    // Move weapon toward center of hitbox (0.0 = surface, 1.0 = center)
    private static final double embedAdjust = 0.40;

    // Bleeding Tunables
    private static final int bleedIntervalTicks = 30; // Bleed every 30 ticks
    private static final float bleedDamage = 1.0f;    // Damage per bleed tick

    private EmbeddingEffect() {}

    // Stick weapon into target entity
    public static void startEmbedding(ThrownProjectileEntity proj, EntityHitResult hit, Vec3 clampedHitPos) {
        if (proj.level().isClientSide()) return;
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living)) {
            proj.dropAndDiscard();
            return;
        }

        // Get weapon direction at impact
        Vec3 dir = proj.getDeltaMovement();
        if (dir.lengthSqr() < 1.0e-6) dir = hit.getLocation().subtract(proj.position());
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(1, 0, 0);
        dir = dir.normalize();

        // Find where weapon should stick (move toward center of hitbox)
        // Use the pre-calculated clamped hit position from ThrownProjectileEntity
        Vec3 embedPos = calculateEmbedPosition(target, clampedHitPos);

        // Set weapon rotation based on impact direction
        float yaw = (float)(Mth.atan2(dir.z, dir.x) * 180.0 / Math.PI);
        float pitch = (float)(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180.0 / Math.PI);

        // Hand tilt affects weapon angle
        float tiltDeg = -proj.getHandRoll();

        // Continue spinning from flight animation
        float initialXRollDeg = (proj.tickCount * forwardSpinSpeedDegPerTick) % 360.0f;

        // Position relative to target entity
        Vec3 worldOffset = embedPos.subtract(target.position());

        // Start embedding weapon
        proj.beginEmbedding(living, worldOffset, yaw, pitch, tiltDeg, initialXRollDeg);

        // Set up bleeding damage
        BleedManager.register(living, proj.level().getGameTime(), proj);

        // Sound effect
        proj.level().playSound(null, proj.blockPosition(),
                SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.45f, 0.8f);

        // DEBUG
        double finalEmbedDepth = clampedHitPos.distanceTo(embedPos);
        log.debug("[Embed] Projectile {} embedded into {} at {} (+{}), yaw={}, pitch={}, tilt={}, xRollStart={}",
                proj.getId(), target.getName().getString(), clampedHitPos,
                String.format("%.2f", finalEmbedDepth),
                String.format("%.1f", yaw), String.format("%.1f", pitch),
                String.format("%.1f", tiltDeg), String.format("%.1f", initialXRollDeg));
    }

    // Move embed point toward center of hitbox
    private static Vec3 calculateEmbedPosition(Entity target, Vec3 hitPos) {
        AABB boundingBox = target.getBoundingBox();

        // Find center point at hit height
        Vec3 centerAtHitY = new Vec3(boundingBox.getCenter().x, hitPos.y, boundingBox.getCenter().z);

        // Direction from hit to center
        Vec3 toCenter = centerAtHitY.subtract(hitPos);

        // Move part way toward center
        Vec3 adjustment = toCenter.scale(embedAdjust);

        // Return adjusted embed position
        return hitPos.add(adjustment);
    }

    // Update stuck weapon each tick
    public static void tickEmbedded(ThrownProjectileEntity proj) {
        if (!proj.isEmbedded()) return;

        if (proj.level().isClientSide()) {
            return;
        }

        Entity target = proj.getEmbeddedTarget();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved()) {
            // Target died, drop the weapon
            log.debug("[Embed] Host entity lost; dropping projectile {}", proj.getId());
            proj.clearEmbedding(); // Clear state to prevent double-drop
            proj.dropAndDiscard();
            return;
        }

        // Get target's current rotation
        float hostBodyYaw = living.getYHeadRot();
        float hostPitch = living.getXRot();

        // Keep weapon stuck to target (follow rotation)
        Vec3 base = target.position();
        Vec3 offsetWorld = rotateY(proj.getEmbeddedOffset(), hostBodyYaw);
        Vec3 newPos = base.add(offsetWorld);
        proj.setPos(newPos);

        // Stop weapon movement
        proj.setDeltaMovement(Vec3.ZERO);
        proj.setNoGravity(true);

        // Update weapon rotation to match target
        float worldYaw = Mth.wrapDegrees(hostBodyYaw + proj.getEmbeddedLocalYaw());
        float worldPitch = Mth.wrapDegrees(hostPitch + proj.getEmbeddedLocalPitch());
        proj.setEmbedYaw(worldYaw);
        proj.setEmbedPitch(worldPitch);

        // Settle roll angle to final position
        float current = proj.getEmbedRoll();
        float baseAngle = targetRollDegX;
        float nearestTarget = baseAngle + 360.0f * Math.round((current - baseAngle) / 360.0f);
        float diff = nearestTarget - current;

        if (Math.abs(diff) > 0.01f) {
            float step = Math.copySign(rollApproachPerTick, diff);
            float next = Math.abs(diff) <= rollApproachPerTick ? nearestTarget : current + step;
            proj.setEmbedRoll(next);
        }

        // Apply bleeding damage
        BleedManager.tryApplyBleed(living, proj.level().getGameTime());

        // DEBUG
        if (proj.tickCount % 20 == 0) {
            log.debug("[Embed] Following host. proj={} bodyYaw={} worldYaw={} pos={}",
                    proj.getId(),
                    String.format("%.1f", hostBodyYaw),
                    String.format("%.1f", worldYaw),
                    proj.position());
        }
    }

    // Remove weapon from target for catching
    public static void releaseEmbedding(ThrownProjectileEntity proj) {
        if (!proj.isEmbedded()) return;
        proj.clearEmbedding();
        proj.setNoGravity(true);
        proj.setDeltaMovement(Vec3.ZERO);
        log.debug("[Embed] Released projectile {} from embed state for catching", proj.getId());
    }

    // Rotate vector around Y axis
    private static Vec3 rotateY(Vec3 v, float degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    // Handles bleeding damage from embedded weapons
    public static final class BleedManager {
        // Track bleeding entities (weak references prevent memory leaks)
        private static final java.util.Map<LivingEntity, BleedState> STATES = new java.util.WeakHashMap<>();

        static void register(LivingEntity host, long worldTime, ThrownProjectileEntity proj) {
            BleedState st = STATES.get(host);
            if (st == null) {
                st = new BleedState(worldTime);
                STATES.put(host, st);
                log.debug("[Bleed] Anchor set for {} at worldTick={}", host.getName().getString(), worldTime);
            }
            st.projs.add(proj);
            log.debug("[Bleed] Added embed for {}. count={}", host.getName().getString(), st.projs.size());
        }

        public static void unregister(LivingEntity host, ThrownProjectileEntity proj) {
            BleedState st = STATES.get(host);
            if (st == null) return;
            st.projs.remove(proj);
            if (st.projs.isEmpty()) {
                STATES.remove(host);
                log.debug("[Bleed] Cleared bleed state for {} (no more embeds)", host.getName().getString());
            } else {
                log.debug("[Bleed] Removed embed for {}. Remaining count={}", host.getName().getString(), st.projs.size());
            }
        }

        static void tryApplyBleed(LivingEntity host, long worldTime) {
            BleedState st = STATES.get(host);
            if (st == null) return;

            // Clean up dead targets
            if (!host.isAlive()) {
                STATES.remove(host);
                log.debug("[Bleed] Host {} died. Removing bleed state.", host.getName().getString());
                return;
            }

            long delta = worldTime - st.anchorTick;
            if (delta < bleedIntervalTicks) return;                // Wait for first bleed
            if (delta % bleedIntervalTicks != 0) return;           // Only bleed on schedule

            // Prevent double damage in same tick
            if (st.lastAppliedTick == worldTime) return;
            st.lastAppliedTick = worldTime;

            // Count active embedded weapons
            int activeCount = 0;
            for (ThrownProjectileEntity p : st.projs) {
                if (p != null && !p.isRemoved() && p.isEmbedded()) {
                    activeCount++;
                }
            }
            if (activeCount <= 0) {
                STATES.remove(host);
                return;
            }

            float total = bleedDamage * activeCount;

            // Apply bleeding damage
            ServerLevel sw = (ServerLevel) host.level();
            host.hurtServer(sw, sw.damageSources().generic(), total);

            // Show bleeding particles
            for (ThrownProjectileEntity p : st.projs) {
                if (p == null || p.isRemoved() || !p.isEmbedded()) continue;
                net.minecraft.world.phys.Vec3 pos = p.position();

                // Send bleeding particle packet to nearby players
                for (ServerPlayer player : sw.getServer().getPlayerList().getPlayers()) {
                    if (player.level() == sw && player.distanceToSqr(pos) < 4096) { // 64 blocks
                        win.demistorm.network.Network.INSTANCE.sendToPlayer(player,
                            new win.demistorm.network.BleedingParticleData(pos.x, pos.y, pos.z));
                    }
                }
            }

            // DEBUG
            log.debug("[Bleed] Applied {} bleed to {} at tick {} (embeds={}, anchor={})",
                    total, host.getName().getString(), worldTime, activeCount, st.anchorTick);
        }

        private static final class BleedState {
            final long anchorTick;
            long lastAppliedTick = Long.MIN_VALUE;
            final java.util.Set<ThrownProjectileEntity> projs = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

            BleedState(long anchorTick) {
                this.anchorTick = anchorTick;
            }
        }
    }

}
