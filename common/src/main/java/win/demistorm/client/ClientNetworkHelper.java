package win.demistorm.client;

import net.minecraft.world.phys.Vec3;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
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

    public static void sendCatchUpdateToServer(ThrownProjectileEntity entity, Vec3 newVelocity, Quaternion handRotation) {
        // Get hand roll from quaternion (same logic as throwing)
        Vector3f fwdTemp = new Vector3f(0, 0, -1);
        fwdTemp.transform(handRotation);
        Vector3f fwd = new Vector3f(fwdTemp.x(), fwdTemp.y(), fwdTemp.z());

        Vector3f upTemp = new Vector3f(0, 1, 0);
        upTemp.transform(handRotation);
        Vector3f up = new Vector3f(upTemp.x(), upTemp.y(), upTemp.z());

        Vector3f projCtrlUpTemp = new Vector3f(up.x(), up.y(), up.z());
        Vector3f fwdCopy = new Vector3f(fwd.x(), fwd.y(), fwd.z());
        fwdCopy.mul(up.dot(fwd));
        projCtrlUpTemp.sub(fwdCopy);
        Vector3f projCtrlUp = new Vector3f(projCtrlUpTemp.x(), projCtrlUpTemp.y(), projCtrlUpTemp.z());

        Vector3f projWorldUpTemp = new Vector3f(0, 1, 0);
        Vector3f fwdCopy2 = new Vector3f(fwd.x(), fwd.y(), fwd.z());
        fwdCopy2.mul(fwd.y());
        projWorldUpTemp.sub(fwdCopy2);
        Vector3f projWorldUp = new Vector3f(projWorldUpTemp.x(), projWorldUpTemp.y(), projWorldUpTemp.z());

        Vector3f crossResult = new Vector3f(projCtrlUp.x(), projCtrlUp.y(), projCtrlUp.z());
        crossResult.cross(projWorldUp);
        Vector3f dotResult1 = new Vector3f(crossResult.x(), crossResult.y(), crossResult.z());
        dotResult1.dot(fwd);

        Vector3f dotResult2 = new Vector3f(projCtrlUp.x(), projCtrlUp.y(), projCtrlUp.z());
        dotResult2.dot(projWorldUp);

        float rollRad = (float) Math.atan2(dotResult1.x(), dotResult2.x());
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