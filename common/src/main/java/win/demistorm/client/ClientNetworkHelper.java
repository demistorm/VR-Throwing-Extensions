package win.demistorm.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.network.Network;
import win.demistorm.network.ThrowData;
import win.demistorm.network.CatchData;
import win.demistorm.network.CatchUpdateData;
import win.demistorm.network.CatchCompleteData;

import static win.demistorm.VRThrowingExtensions.log;

// Forwards client network information to cross-platform Network system
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void sendToServer(Vec3 pos, Vec3 velocity, boolean wholeStack, float rollDeg) {
        log.debug("ClientNetworkHelper: Sending throw. pos={} vel={} all={}", pos, velocity, wholeStack);
        Network.INSTANCE.sendToServer(new ThrowData(pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z, wholeStack, rollDeg));
    }

    public static void sendCatchToServer(ThrownProjectileEntity entity, boolean startCatch) {
        log.debug("ClientNetworkHelper: Sending catch start/cancel. entity={} start={}", entity.getId(), startCatch);
        Network.INSTANCE.sendToServer(new CatchData(entity.getId(), startCatch));
    }

    public static void sendCatchUpdateToServer(ThrownProjectileEntity entity, Vec3 newVelocity, Quaternionfc handRotation) {
        // Calculate hand roll from quaternionfc (same logic as throwing)
        org.joml.Vector3f fwd = new org.joml.Vector3f(0, 0, -1).rotate(handRotation).normalize();
        org.joml.Vector3f up  = new org.joml.Vector3f(0, 1,  0).rotate(handRotation).normalize();

        org.joml.Vector3f projCtrlUp  = up .sub(new org.joml.Vector3f(fwd).mul(up .dot(fwd))).normalize();
        org.joml.Vector3f projWorldUp = new org.joml.Vector3f(0, 1, 0)
                .sub(new org.joml.Vector3f(fwd).mul(fwd.y)).normalize();

        float rollRad = projCtrlUp.angleSigned(projWorldUp, fwd);
        float rollDeg = (float) Math.toDegrees(rollRad);

        log.debug("ClientNetworkHelper: Sending catch update. entity={} vel={} roll={}",
                entity.getId(), newVelocity, rollDeg);
        Network.INSTANCE.sendToServer(new CatchUpdateData(entity.getId(), newVelocity.x, newVelocity.y, newVelocity.z, rollDeg));
    }

    public static void sendCatchCompleteToServer(ThrownProjectileEntity entity) {
        log.debug("ClientNetworkHelper: Sending catch complete. entity={}", entity.getId());
        Network.INSTANCE.sendToServer(new CatchCompleteData(entity.getId()));
    }
}