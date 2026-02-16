package win.demistorm.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.network.Network;
import win.demistorm.network.data.ThrowData;
import win.demistorm.network.data.ThrowTNTData;
import win.demistorm.network.data.TNTLitData;
import win.demistorm.network.data.CatchData;
import win.demistorm.network.data.CatchUpdateData;
import win.demistorm.network.data.CatchCompleteData;

import static win.demistorm.VRThrowingExtensions.log;

// Forwards client network information to cross-platform Network system
public final class ClientNetworkHelper {
    private ClientNetworkHelper() {}

    public static void sendToServer(Vec3 pos, Vec3 velocity, boolean useBindHeld, boolean playerCrouched, float rollDeg, net.minecraft.world.InteractionHand hand) {
        log.debug("ClientNetworkHelper: Sending throw. pos={} vel={} useBindHeld={} playerCrouched={} hand={}", pos, velocity, useBindHeld, playerCrouched, hand);
        Network.INSTANCE.sendToServer(new ThrowData(pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z, useBindHeld, playerCrouched, rollDeg, hand));
    }

    public static void sendCatchToServer(ThrownProjectileEntity entity, boolean startCatch, net.minecraft.world.InteractionHand hand) {
        log.debug("ClientNetworkHelper: Sending catch start/cancel. entity={} start={} hand={}", entity.getId(), startCatch, hand);
        Network.INSTANCE.sendToServer(new CatchData(entity.getId(), startCatch, hand));
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

    public static void sendTNTLitPacket() {
        log.debug("ClientNetworkHelper: Sending TNT lit event");
        Network.INSTANCE.sendToServer(new TNTLitData());
    }

    public static void sendThrowTNTPacket(Vec3 pos, Vec3 velocity, float rollDeg, net.minecraft.world.InteractionHand hand) {
        log.debug("ClientNetworkHelper: Sending lit TNT throw. pos={} vel={} roll={} hand={}", pos, velocity, rollDeg, hand);
        Network.INSTANCE.sendToServer(new ThrowTNTData(pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z, rollDeg, hand));
    }
}