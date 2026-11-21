package win.demistorm.network.data;

import net.minecraft.world.InteractionHand;

// Data for catch packets (client to server)
public record CatchData(
    int entityId,            // Projectile to catch
    boolean startCatch,      // Start catching (true) or stop (false)
    InteractionHand hand     // Which hand is catching
) {}