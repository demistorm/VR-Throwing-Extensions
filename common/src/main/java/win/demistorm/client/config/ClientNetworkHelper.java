package win.demistorm.client.config;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionfc;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.network.NetworkHelper;

import static win.demistorm.VRThrowingExtensions.log;

// Forwards client network information to NetworkHelper
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void sendToServer(Vec3d pos, Vec3d velocity, boolean wholeStack, float rollDeg) {
        log.debug("ClientNetworkHelper: Sending throw. pos={} vel={} all={}", pos, velocity, wholeStack);
        ClientPlayNetworking.send(new NetworkHelper.ThrowPacket(pos, velocity, wholeStack, rollDeg));
    }

    public static void sendCatchToServer(ThrownProjectileEntity entity, boolean startCatch) {
        log.debug("ClientNetworkHelper: Sending catch start/cancel. entity={} start={}", entity.getId(), startCatch);
        ClientPlayNetworking.send(new NetworkHelper.CatchPacket(entity.getId(), startCatch));
    }

    public static void sendCatchUpdateToServer(ThrownProjectileEntity entity, Vec3d newVelocity, Quaternionfc handRotation) {
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
        ClientPlayNetworking.send(new NetworkHelper.CatchUpdatePacket(entity.getId(), newVelocity, rollDeg));
    }

    public static void sendCatchCompleteToServer(ThrownProjectileEntity entity) {
        log.debug("ClientNetworkHelper: Sending catch complete. entity={}", entity.getId());
        ClientPlayNetworking.send(new NetworkHelper.CatchCompletePacket(entity.getId()));
    }
}