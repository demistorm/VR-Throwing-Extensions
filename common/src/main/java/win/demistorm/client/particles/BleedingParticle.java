package win.demistorm.client.particles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import win.demistorm.client.config.ClientOnlyConfig;
import win.demistorm.network.NetworkHelper;

import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
public final class BleedingParticle {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHelper.BleedParticlePacket.ID,
                (payload, context) -> {
                    if (!ClientOnlyConfig.ACTIVE.bloodEffect) return;
                    context.client().execute(() -> spawnBleedTrickle(payload.pos()));
                });
    }

    // Tunables for the trickle effect
    private static final int baseCount = 10;
    private static final int countVariation = 4;

    private static final double lateralJitter = 0.02;     // Sideways drift
    private static final double velDownMin = -0.08;       // Downward velocity range
    private static final double velDownMax = -0.02;
    private static final double velSideMin = -0.015;      // Slight sideways velocity
    private static final double velSideMax =  0.015;

    private static final float dustScaleBase = 0.45f;     // Subtle dust
    private static final float dustScaleVar  = 0.20f;

    private static final double redDyeChance = 0.35;      // Occasionally spawn a heavier droplet

    private static void spawnBleedTrickle(Vec3d pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int count = baseCount + rng.nextInt(-countVariation, countVariation + 1);

        for (int i = 0; i < count; i++) {
            // Small position jitter to avoid a single point emitter
            double ox = rng.nextDouble(-lateralJitter, lateralJitter);
            double oy = rng.nextDouble(-lateralJitter, lateralJitter);
            double oz = rng.nextDouble(-lateralJitter, lateralJitter);

            // Downward-biased velocity with tiny sideways drift
            double vx = rng.nextDouble(velSideMin, velSideMax);
            double vy = rng.nextDouble(velDownMin, velDownMax);
            double vz = rng.nextDouble(velSideMin, velSideMax);

            // Occasionally spawn a "droplet" using red dye
            if (rng.nextDouble() < redDyeChance) {
                mc.world.addParticleClient(
                        new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.RED_DYE)),
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        vx, vy, vz
                );
            } else {
                // Dark red dust mote
                int packedColor = packColor(0.6f + rng.nextFloat() * 0.35f, rng.nextFloat() * 0.08f, rng.nextFloat() * 0.06f);
                float scale = dustScaleBase + (rng.nextFloat() - 0.5f) * dustScaleVar;
                DustParticleEffect bloodDust = new DustParticleEffect(packedColor, scale);

                mc.world.addParticleClient(
                        bloodDust,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        vx, vy, vz
                );
            }
        }
    }

    // Pack floats [0..1] into 0xRRGGBB
    private static int packColor(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, (int)(r * 255f)));
        int gi = Math.max(0, Math.min(255, (int)(g * 255f)));
        int bi = Math.max(0, Math.min(255, (int)(b * 255f)));
        return (ri << 16) | (gi << 8) | bi;
    }

    private BleedingParticle() {}
}
