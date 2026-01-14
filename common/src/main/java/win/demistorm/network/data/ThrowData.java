package win.demistorm.network.data;

// Data for throw packets (client to server)
public record ThrowData(
    double posX, double posY, double posZ,    // Starting position
    double velX, double velY, double velZ,    // Velocity vector
    boolean useBindHeld,                      // Place/use keybind was held
    boolean playerCrouched,                   // Player was crouching
    float rollDeg                              // Hand roll angle
) {}