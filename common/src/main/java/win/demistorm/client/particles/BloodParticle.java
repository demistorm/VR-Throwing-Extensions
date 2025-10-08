package win.demistorm.client.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import win.demistorm.client.config.ClientOnlyConfig;
import win.demistorm.network.NetworkHelper;

import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
public final class BloodParticle {

    // Register client packet handler
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHelper.BloodParticlePacket.ID,
                (payload, context) -> {
                    if (!ClientOnlyConfig.ACTIVE.bloodEffect) return;
                    context.client().execute(() -> spawnBloodParticles(payload.pos(), payload.velocity()));
                });
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

    private static void spawnBloodParticles(Vec3d pos, Vec3d velocity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Impact speed and forward direction
        double speed = velocity.length();
        Vec3d forward = normalizeSafely(velocity, new Vec3d(0, 0, -1));

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
            Vec3d sprayForward = randomDirectionCone(basis, coneDeg, rng);

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
                Vec3d jitter = basis.u.multiply(jitterU).add(basis.v.multiply(jitterV));

                // Velocity = forward * impactSpeed * scale + jitter
                Vec3d vel = sprayForward.multiply(speed * dropletSpeedScale).add(jitter);

                client.world.addParticleClient(
                        new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.RED_DYE)),
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        vel.x, vel.y, vel.z
                );
            } else {
                // Mist: fraction of speed + lateral spread
                double mistU = rng.nextDouble(-mistSpread, mistSpread);
                double mistV = rng.nextDouble(-mistSpread, mistSpread);
                Vec3d sideways = basis.u.multiply(mistU).add(basis.v.multiply(mistV));

                Vec3d vel = sprayForward.multiply(mistVelMultiplier * speed).add(sideways);

                int packedColor = packColor(r, g, b); // 0xRRGGBB
                DustParticleEffect blood = new DustParticleEffect(packedColor, scale);

                client.world.addParticleClient(
                        blood,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        vel.x, vel.y, vel.z
                );
            }
        }
    }

    // Normalize or return fallback when too small
    private static Vec3d normalizeSafely(Vec3d v, Vec3d fallback) {
        double len2 = v.lengthSquared();
        if (len2 < 1.0e-8) return fallback;
        return v.multiply(1.0 / Math.sqrt(len2));
    }

    // Build orthonormal basis {f, u, v} with f = forward
    private static Basis makePerpendicularBasis(Vec3d forward) {
        Vec3d f = normalizeSafely(forward, new Vec3d(0, 0, -1));

        // Up-like vector not parallel to f
        Vec3d up = Math.abs(f.y) < 0.999 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);

        Vec3d u = f.crossProduct(up);
        u = normalizeSafely(u, new Vec3d(1, 0, 0));
        Vec3d v = f.crossProduct(u);
        return new Basis(f, u, v);
    }

    // Uniform direction inside a cone around forward
    private static Vec3d randomDirectionCone(Basis basis, double maxAngleDegrees, ThreadLocalRandom rng) {
        double maxRad = Math.toRadians(maxAngleDegrees);
        double cosAlpha = MathHelper.lerp(rng.nextDouble(), Math.cos(maxRad), 1.0);
        double sinAlpha = Math.sqrt(Math.max(0.0, 1.0 - cosAlpha * cosAlpha));

        double theta = rng.nextDouble(0.0, Math.PI * 2.0);
        double ct = Math.cos(theta);
        double st = Math.sin(theta);

        Vec3d lateral = basis.u.multiply(ct).add(basis.v.multiply(st));
        return basis.f.multiply(cosAlpha).add(lateral.multiply(sinAlpha)).normalize();
    }

    // Pack floats [0..1] into 0xRRGGBB
    private static int packColor(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, (int)(r * 255f)));
        int gi = Math.max(0, Math.min(255, (int)(g * 255f)));
        int bi = Math.max(0, Math.min(255, (int)(b * 255f)));
        return (ri << 16) | (gi << 8) | bi;
    }

    private record Basis(Vec3d f, Vec3d u, Vec3d v) {}

    private BloodParticle() {}
}
