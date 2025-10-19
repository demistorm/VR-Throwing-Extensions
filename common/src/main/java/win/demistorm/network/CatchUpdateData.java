package win.demistorm.network;

// Data for catch update packets (client to server)
public record CatchUpdateData(
    int entityId,                          // Projectile being caught
    double velX, double velY, double velZ, // Updated velocity for pull effect
    float rollDeg                           // Current hand rotation
) {}