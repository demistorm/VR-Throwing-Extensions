package win.demistorm.network.data;

import net.minecraft.world.InteractionHand;

// Data for lit TNT throw packets (client to server)
public record ThrowTNTData(
    double posX, double posY, double posZ,    // Starting position
    double velX, double velY, double velZ,    // Velocity vector
    float rollDeg,                             // Hand roll angle
    InteractionHand hand                       // Which hand threw the TNT
) {}
