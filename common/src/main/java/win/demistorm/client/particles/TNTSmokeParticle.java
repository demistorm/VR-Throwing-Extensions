package win.demistorm.client.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

// Colored smoke particle for thrown TNT
public final class TNTSmokeParticle {

    // Spawn a single colored smoke particle (identical behavior to vanilla SMOKE)
    public static void spawnColoredSmoke(float r, float g, float b, double x, double y, double z) {
        spawnColoredSmoke(r, g, b, x, y, z, 1.0f);
    }

    // Spawn a single colored smoke particle with custom scale
    public static void spawnColoredSmoke(float r, float g, float b, double x, double y, double z, float scale) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        // Create Vector3f color from RGB floats
        Vector3f color = new Vector3f(r, g, b);

        // Create dust particle with color and custom scale
        DustParticleOptions coloredSmoke = new DustParticleOptions(color, scale);

        // Spawn particle with zero velocity (same as vanilla SMOKE)
        client.level.addParticle(
                coloredSmoke,
                x, y, z,
                0.0, 0.0, 0.0
        );
    }

    private TNTSmokeParticle() {}
}
