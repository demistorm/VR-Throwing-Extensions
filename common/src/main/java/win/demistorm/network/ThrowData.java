package win.demistorm.network;

// Data for throw packets (client to server)
public record ThrowData(
    double posX, double posY, double posZ,    // Starting position
    double velX, double velY, double velZ,    // Velocity vector
    boolean wholeStack,                        // Throw entire stack or just one
    float rollDeg                              // Hand roll angle
) {}