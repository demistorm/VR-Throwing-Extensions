package win.demistorm.client.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
//? if <=1.21.1
//import org.joml.Vector3f;

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

        // Pack RGB into 0xRRGGBB format
        //? if >=1.21.11 {
        int packedColor = packColor(r, g, b);

        // Create dust particle with color and custom scale
        DustParticleOptions coloredSmoke = new DustParticleOptions(packedColor, scale);
        //?} else {
        /*Vector3f color = new Vector3f(r, g, b);

        // Create dust particle with color and custom scale
        DustParticleOptions coloredSmoke = new DustParticleOptions(color, scale);*/
        //?}

        // Spawn particle with zero velocity (same as vanilla SMOKE)
        client.level.addParticle(
                coloredSmoke,
                x, y, z,
                0.0, 0.0, 0.0
        );
    }

    //? if >=1.21.11 {
    // Pack floats [0..1] into 0xRRGGBB
    private static int packColor(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, (int)(r * 255f)));
        int gi = Math.max(0, Math.min(255, (int)(g * 255f)));
        int bi = Math.max(0, Math.min(255, (int)(b * 255f)));
        return (ri << 16) | (gi << 8) | bi;
    }
    //?}

    private TNTSmokeParticle() {}
}
