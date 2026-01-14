package win.demistorm.network.data;

// Data for catch packets (client to server)
public record CatchData(
    int entityId,      // Projectile to catch
    boolean startCatch // Start catching (true) or stop (false)
) {}