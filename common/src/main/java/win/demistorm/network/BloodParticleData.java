package win.demistorm.network;

// Data for blood particle packets (server to client)
public record BloodParticleData(
    double posX, double posY, double posZ,    // Where projectile hit
    double velX, double velY, double velZ     // Direction of impact
) {}