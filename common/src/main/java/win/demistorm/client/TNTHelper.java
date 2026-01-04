package win.demistorm.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;

// TNT Lighting with flint & steel swipe detection
public class TNTHelper {
    private boolean isLit = false;
    private boolean isTracking = false;
    private LocalPlayer trackingPlayer = null;

    private static final float proximityDistance = 0.2f; // Distance in meters

    // Check if offhand swiped through main hand's proximity zone
    public boolean checkSwipeMotion(LocalPlayer player) {
        if (!isTracking || trackingPlayer == null) return false;

        // Must be holding flint & steel in offhand
        if (!isHoldingFlintAndSteel(player)) {
            return false;
        }

        VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(player);
        if (history == null) return false;

        // Check last 5 ticks
        // At each tick, check distance between main hand and offhand at that tick
        boolean wasOutside = false;
        boolean wasInside = false;
        boolean isOutsideAgain = false;

        for (int i = 4; i >= 0; i--) {
            VRPose historicalPose;
            try {
                historicalPose = history.getHistoricalData(i);
            } catch (Exception e) {
                continue;
            }

            if (historicalPose == null) continue;

            VRBodyPartData mainHandData = historicalPose.getHand(InteractionHand.MAIN_HAND);
            VRBodyPartData offHandData = historicalPose.getHand(InteractionHand.OFF_HAND);

            if (mainHandData == null || offHandData == null) continue;

            Vector3f mainHandPos = new Vector3f(
                (float) mainHandData.getPos().x,
                (float) mainHandData.getPos().y,
                (float) mainHandData.getPos().z
            );

            Vector3f offHandPos = new Vector3f(
                (float) offHandData.getPos().x,
                (float) offHandData.getPos().y,
                (float) offHandData.getPos().z
            );

            Vector3f offset = new Vector3f(offHandPos).sub(mainHandPos);
            float distance = offset.length();

            if (i == 0) {
                isOutsideAgain = distance >= proximityDistance;
            } else if (i <= 3) {
                if (distance < proximityDistance) wasInside = true;
            } else { // i == 4
                wasOutside = distance >= proximityDistance;
            }
        }

        // Swipe pattern: outside → inside → outside
        boolean swipeDetected = wasOutside && wasInside && isOutsideAgain;

        if (swipeDetected && !isLit) {
            isLit = true;

            // Send packet to server to start fuse timer
            ClientNetworkHelper.sendTNTLitPacket();

            return true;
        }

        return false;
    }

    // Check if player is holding flint & steel in the specified hand
    public boolean isHoldingFlintAndSteel(LocalPlayer player, InteractionHand hand) {
        if (player == null) return false;
        return player.getItemInHand(hand).is(Items.FLINT_AND_STEEL);
    }

    // Check if player is holding flint & steel in offhand
    public boolean isHoldingFlintAndSteel(LocalPlayer player) {
        return isHoldingFlintAndSteel(player, InteractionHand.OFF_HAND);
    }

    // Start tracking TNT lighting
    public void startTracking(LocalPlayer player) {
        isTracking = true;
        isLit = false;
        trackingPlayer = player;
    }

    // Stop tracking and reset state
    public void stopTracking() {
        isTracking = false;
        isLit = false;
        trackingPlayer = null;
    }

    // Check if TNT is currently lit
    public boolean isLit() {
        return isLit;
    }

    // Check if currently tracking
    public boolean isTracking() {
        return isTracking;
    }
}
