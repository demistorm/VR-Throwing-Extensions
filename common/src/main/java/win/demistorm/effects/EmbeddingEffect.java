package win.demistorm.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import win.demistorm.network.NetworkHelper;
import win.demistorm.ThrownProjectileEntity;

import static win.demistorm.VRThrowingExtensions.log;

// Embeds projectiles into entities on hit and maintains the embedded state.
public final class EmbeddingEffect {

    // How the rolled X rotation should settle after impact (in degrees, positive X)
    public static final float targetRollDegX = 15.0f;
    // How quickly the roll converges to target per tick (deg/tick)
    public static final float rollApproachPerTick = 20.0f;
    private static final float forwardSpinSpeedDegPerTick = 15.0f;
    // How much to adjust the embed position toward the center of the hitbox (0.0 = no adjustment, 1.0 = center)
    private static final double embedAdjust = 0.45;

    // Bleeding tunables
    private static final int bleedIntervalTicks = 30; // Every 30 ticks from embed
    private static final float bleedDamage = 1.0f;    // Damage per embedded projectile

    private EmbeddingEffect() {}

    // Called on first entity hit to start embedding if conditions pass
    public static void startEmbedding(ThrownProjectileEntity proj, EntityHitResult hit) {
        if (proj.getWorld().isClient()) return;
        Entity target = hit.getEntity();
        if (!(target instanceof LivingEntity living)) {
            proj.dropAndDiscard();
            return;
        }

        // Direction at impact
        Vec3d dir = proj.getVelocity();
        if (dir.lengthSquared() < 1.0e-6) dir = hit.getPos().subtract(proj.getPos());
        if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(1, 0, 0);
        dir = dir.normalize();

        // Calculate dynamic embed position by moving 30% closer to center of hitbox while maintaining Y level
        Vec3d hitPos = hit.getPos();
        Vec3d embedPos = calculateEmbedPosition(target, hitPos);

        // Base yaw/pitch as in flight rendering
        float yaw = (float)(MathHelper.atan2(dir.z, dir.x) * 180.0 / Math.PI);
        float pitch = (float)(MathHelper.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180.0 / Math.PI);

        // Visual hand tilt (same as used in flight: rotation around Z by -handRoll)
        float tiltDeg = -proj.getHandRoll();

        // Initial X roll continues the inflight spin for seamless transition
        float initialXRollDeg = (proj.age * forwardSpinSpeedDegPerTick) % 360.0f;

        // Offset from the target's position to the exact embed point
        Vec3d worldOffset = embedPos.subtract(target.getPos());

        // Initialize embedding
        proj.beginEmbedding(living, worldOffset, yaw, pitch, tiltDeg, initialXRollDeg);

        // NEW: Register this embed for bleeding (anchor to the time of the first embed)
        BleedManager.register(living, proj.getWorld().getTime(), proj);

        // Sound effect
        proj.getWorld().playSound(null, proj.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.45f, 0.8f);

        // DEBUG
        double finalEmbedDepth = hitPos.distanceTo(embedPos);
        log.debug("[Embed] Projectile {} embedded into {} at {} (+{}), yaw={}, pitch={}, tilt={}, xRollStart={}",
                proj.getId(), target.getName().getString(), hitPos,
                String.format("%.2f", finalEmbedDepth),
                String.format("%.1f", yaw), String.format("%.1f", pitch),
                String.format("%.1f", tiltDeg), String.format("%.1f", initialXRollDeg));
    }

    // Calculate embed position by moving closer to center while maintaining Y level
    private static Vec3d calculateEmbedPosition(Entity target, Vec3d hitPos) {
        Box boundingBox = target.getBoundingBox();

        // Get the center of the bounding box at the same Y level as the hit
        Vec3d centerAtHitY = new Vec3d(boundingBox.getCenter().x, hitPos.y, boundingBox.getCenter().z);

        // Calculate the vector from hit position to center
        Vec3d toCenter = centerAtHitY.subtract(hitPos);

        // Move embedAdjust percent of the way toward the center
        Vec3d adjustment = toCenter.multiply(embedAdjust);

        // Apply the adjustment to move the embed position closer to center
        return hitPos.add(adjustment);
    }

    // Per tick embed tracker
    // Keeps it stuck at the original hit point on the target entity and animates roll toward a stable angle.
    public static void tickEmbedded(ThrownProjectileEntity proj) {
        if (!proj.isEmbedded()) return;

        if (proj.getWorld().isClient()) {
            return;
        }

        Entity target = proj.getEmbeddedTarget();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target.isRemoved()) {
            // If the entity died or disappeared, drop the item
            log.debug("[Embed] Host entity lost; dropping projectile {}", proj.getId());
            proj.clearEmbedding(); // Clears state first to prevent double-drop
            proj.dropAndDiscard();
            return;
        }

        // Use body yaw so in-place rotation is reflected even when not technically moving
        float hostBodyYaw = living.getBodyYaw();
        float hostPitch = living.getPitch();

        // Maintain position at the locked local offset relative to the host (rotate by host BODY yaw)
        Vec3d base = target.getPos();
        Vec3d offsetWorld = rotateY(proj.getEmbeddedOffset(), hostBodyYaw);
        Vec3d newPos = base.add(offsetWorld);
        proj.setPosition(newPos);

        // Freeze physics
        proj.setVelocity(Vec3d.ZERO);
        proj.setNoGravity(true);

        // Visual yaw/pitch follow the host body/head while preserving local embed orientation
        float worldYaw = MathHelper.wrapDegrees(hostBodyYaw + proj.getEmbeddedLocalYaw());
        float worldPitch = MathHelper.wrapDegrees(hostPitch + proj.getEmbeddedLocalPitch());
        proj.setEmbedYaw(worldYaw);
        proj.setEmbedPitch(worldPitch);

        // Approach the nearest 85 degrees (mod 360) and stop there
        float current = proj.getEmbedRoll();
        float baseAngle = targetRollDegX;
        float nearestTarget = baseAngle + 360.0f * Math.round((current - baseAngle) / 360.0f);
        float diff = nearestTarget - current;

        if (Math.abs(diff) > 0.01f) {
            float step = Math.copySign(rollApproachPerTick, diff);
            float next = Math.abs(diff) <= rollApproachPerTick ? nearestTarget : current + step;
            proj.setEmbedRoll(next);
        }

        // NEW: Apply synced bleed if it's this world's bleed tick for the host
        BleedManager.tryApplyBleed(living, proj.getWorld().getTime());

        // DEBUG
        if (proj.age % 20 == 0) {
            log.debug("[Embed] Following host. proj={} bodyYaw={} worldYaw={} pos={}",
                    proj.getId(),
                    String.format("%.1f", hostBodyYaw),
                    String.format("%.1f", worldYaw),
                    proj.getPos());
        }
    }

    // When catching starts, release from embedding
    public static void releaseEmbedding(ThrownProjectileEntity proj) {
        if (!proj.isEmbedded()) return;
        proj.clearEmbedding();
        proj.setNoGravity(true);
        proj.setVelocity(Vec3d.ZERO);
        log.debug("[Embed] Released projectile {} from embed state for catching", proj.getId());
    }

    // Rotate a vector around the Y-axis by degrees
    private static Vec3d rotateY(Vec3d v, float degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3d(x, v.y, z);
    }

    // Per-entity bleed manager with synchronized 30-tick cycles
    public static final class BleedManager {
        // Weakly key by entity to avoid leaks across deaths/unloads
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

            // Cleanup if host is dead
            if (!host.isAlive()) {
                STATES.remove(host);
                log.debug("[Bleed] Host {} died. Removing bleed state.", host.getName().getString());
                return;
            }

            long delta = worldTime - st.anchorTick;
            if (delta < bleedIntervalTicks) return;                // First bleed exactly at anchor + interval
            if (delta % bleedIntervalTicks != 0) return;           // Must align to the 30 tick cycle

            // Ensure only one application in this world tick
            if (st.lastAppliedTick == worldTime) return;
            st.lastAppliedTick = worldTime;

            // Compute number of active embedded projectiles
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

            // Apply generic damage (respects armor/enchantments)
            net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) host.getWorld();
            host.damage(sw, sw.getDamageSources().generic(), total);

            // Send trickle particles for every currently embedded projectile
            for (ThrownProjectileEntity p : st.projs) {
                if (p == null || p.isRemoved() || !p.isEmbedded()) continue;
                Vec3d pos = p.getPos();

                for (net.minecraft.server.network.ServerPlayerEntity player : sw.getServer().getPlayerManager().getPlayerList()) {
                    if (player.getWorld() == sw && player.squaredDistanceTo(pos) < 4096) { // 64 blocks
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                                player, new NetworkHelper.BleedParticlePacket(pos));
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
