package win.demistorm.client.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.config.ClientOnlyConfig;
import win.demistorm.network.BloodParticleData;

import java.util.concurrent.ThreadLocalRandom;

public final class BloodParticle {

    // Public entry point for particle spawning from network packets
    public static void spawnParticles(BloodParticleData data) {
        if (!ClientOnlyConfig.ACTIVE.bloodEffect) {
            VRThrowingExtensions.log.debug("[Blood Effect] Blood particles disabled by config, skipping spawn at ({}, {}, {})",
                data.posX(), data.posY(), data.posZ());
            return;
        }

        VRThrowingExtensions.log.debug("[Blood Effect] Spawning blood particles at ({}, {}, {}) with velocity ({}, {}, {})",
            data.posX(), data.posY(), data.posZ(), data.velX(), data.velY(), data.velZ());

        Vec3 pos = new Vec3(data.posX(), data.posY(), data.posZ());
        Vec3 velocity = new Vec3(data.velX(), data.velY(), data.velZ());
        spawnBloodParticles(pos, velocity);
    }

    // Tunables
    private static final int particleCount = 32;        // Average droplets per burst
    private static final int particleVariation = 6;     // +/- random count

    // Cone spread (in degrees)
    private static final double coneAngleBase = 12.0;      // Base cone angle
    private static final double coneAngleVariation = 10.0; // Random extra

    // Mist
    private static final double mistSpread = 0.20;          // Lateral spread
    private static final double mistVelMultiplier = 0.30;   // Fraction of impact speed

    // Droplets
    private static final double dropletRatio = 0.6;         // Portion of particles that are droplets
    private static final double dropletSideJitter = 0.12;   // Small sideways jitter
    private static final double dropletSpeedScale = 1.0;    // Forward speed = impactSpeed * scale

    // Particle scale
    private static final float scaleBase = 1.0f;            // Base size
    private static final float scaleVariation = 0.4f;       // +/- random size

    private static void spawnBloodParticles(Vec3 pos, Vec3 velocity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Impact speed and forward direction
        double speed = velocity.length();
        Vec3 forward = normalizeSafely(velocity, new Vec3(0, 0, -1));

        // Local basis aligned to forward
        Basis basis = makePerpendicularBasis(forward);

        // Random cone angle (degrees)
        double coneDeg = coneAngleBase + rng.nextDouble() * coneAngleVariation;

        // Total particles this burst
        int count = particleCount + rng.nextInt(-particleVariation, particleVariation + 1);

        for (int i = 0; i < count; i++) {
            // Jitter spawn position
            double ox = rng.nextDouble(-0.15, 0.15);
            double oy = rng.nextDouble(-0.15, 0.15);
            double oz = rng.nextDouble(-0.15, 0.15);

            // Direction inside the cone
            Vec3 sprayForward = randomDirectionCone(basis, coneDeg, rng);

            // Dark red color variation and size
            float r = 0.6f + (float) rng.nextDouble(0.0, 0.4);
            float g = (float) rng.nextDouble(0.0, 0.1);
            float b = (float) rng.nextDouble(0.0, 0.05);
            float scale = scaleBase + (float) rng.nextDouble(-scaleVariation * 0.5, scaleVariation * 0.5);

            boolean spawnDroplet = rng.nextDouble() < dropletRatio;

            if (spawnDroplet) {
                // Droplet: forward-heavy with small lateral jitter
                double jitterU = rng.nextDouble(-dropletSideJitter, dropletSideJitter);
                double jitterV = rng.nextDouble(-dropletSideJitter, dropletSideJitter);
                Vec3 jitter = basis.u.scale(jitterU).add(basis.v.scale(jitterV));

                // Velocity = forward * impactSpeed * scale + jitter
                Vec3 vel = sprayForward.scale(speed * dropletSpeedScale).add(jitter);

                client.level.addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.RED_DYE)),
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        vel.x, vel.y, vel.z
                );
            } else {
                // Mist: fraction of speed + lateral spread
                double mistU = rng.nextDouble(-mistSpread, mistSpread);
                double mistV = rng.nextDouble(-mistSpread, mistSpread);
                Vec3 sideways = basis.u.scale(mistU).add(basis.v.scale(mistV));

                Vec3 vel = sprayForward.scale(mistVelMultiplier * speed).add(sideways);

                Vector3f color = new Vector3f(r, g, b);
                DustParticleOptions blood = new DustParticleOptions(color, scale);

                client.level.addParticle(
                        blood,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        vel.x, vel.y, vel.z
                );
            }
        }
    }

    // Normalize or return fallback when too small
    private static Vec3 normalizeSafely(Vec3 v, Vec3 fallback) {
        double len2 = v.lengthSqr();
        if (len2 < 1.0e-8) return fallback;
        return v.scale(1.0 / Math.sqrt(len2));
    }

    // Build orthonormal basis {f, u, v} with f = forward
    private static Basis makePerpendicularBasis(Vec3 forward) {
        Vec3 f = normalizeSafely(forward, new Vec3(0, 0, -1));

        // Up-like vector not parallel to f
        Vec3 up = Math.abs(f.y) < 0.999 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);

        Vec3 u = f.cross(up);
        u = normalizeSafely(u, new Vec3(1, 0, 0));
        Vec3 v = f.cross(u);
        return new Basis(f, u, v);
    }

    // Uniform direction inside a cone around forward
    private static Vec3 randomDirectionCone(Basis basis, double maxAngleDegrees, ThreadLocalRandom rng) {
        double maxRad = Math.toRadians(maxAngleDegrees);
        double cosAlpha = Mth.lerp(rng.nextDouble(), Math.cos(maxRad), 1.0);
        double sinAlpha = Math.sqrt(Math.max(0.0, 1.0 - cosAlpha * cosAlpha));

        double theta = rng.nextDouble(0.0, Math.PI * 2.0);
        double ct = Math.cos(theta);
        double st = Math.sin(theta);

        Vec3 lateral = basis.u.scale(ct).add(basis.v.scale(st));
        return basis.f.scale(cosAlpha).add(lateral.scale(sinAlpha)).normalize();
    }

    private record Basis(Vec3 f, Vec3 u, Vec3 v) {}

    private BloodParticle() {}
}
