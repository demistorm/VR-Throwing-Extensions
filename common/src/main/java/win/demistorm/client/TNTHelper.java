package win.demistorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Quaternionfc;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;
import win.demistorm.VRThrowingExtensions;

// TNT Lighting with flint & steel swipe detection
public class TNTHelper {
    private boolean isLit = false;
    private boolean isTracking = false;
    private InteractionHand activeTNTHand = InteractionHand.MAIN_HAND;
    private LocalPlayer trackingPlayer = null;
    private int ticksSinceLit = 0; // How many ticks since TNT was lit
    private static final int TNT_FUSE_TICKS = 100; // Server-side fuse duration

    private static final float proximityDistance = 0.2f; // Distance in meters

    // Check if flint & steel hand swiped through TNT hand's proximity zone
    public boolean checkSwipeMotion(LocalPlayer player) {
        if (!isTracking || trackingPlayer == null) return false;

        // Find which hand has flint & steel
        InteractionHand flintHand = null;
        if (isHoldingFlintAndSteel(player, InteractionHand.MAIN_HAND)) {
            flintHand = InteractionHand.MAIN_HAND;
        } else if (isHoldingFlintAndSteel(player, InteractionHand.OFF_HAND)) {
            flintHand = InteractionHand.OFF_HAND;
        }

        if (flintHand == null) return false;

        if (flintHand == activeTNTHand) return false;

        VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(player);
        if (history == null) return false;

        // Check last 5 ticks
        // At each tick, check distance between TNT hand and flint hand at that tick
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

            VRBodyPartData tntHandData = historicalPose.getHand(activeTNTHand);
            VRBodyPartData flintHandData = historicalPose.getHand(flintHand);

            if (tntHandData == null || flintHandData == null) continue;

            Vector3f tntHandPos = new Vector3f(
                (float) tntHandData.getPos().x,
                (float) tntHandData.getPos().y,
                (float) tntHandData.getPos().z
            );

            Vector3f flintHandPos = new Vector3f(
                (float) flintHandData.getPos().x,
                (float) flintHandData.getPos().y,
                (float) flintHandData.getPos().z
            );

            Vector3f offset = new Vector3f(flintHandPos).sub(tntHandPos);
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
            ticksSinceLit = 0; // Reset tick counter

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

    // Check if player is holding flint & steel in either hand
    public boolean isHoldingFlintAndSteel(LocalPlayer player) {
        return isHoldingFlintAndSteel(player, InteractionHand.MAIN_HAND) ||
               isHoldingFlintAndSteel(player, InteractionHand.OFF_HAND);
    }

    // Start tracking TNT lighting
    public void startTracking(LocalPlayer player, InteractionHand hand) {
        isTracking = true;
        isLit = false;
        activeTNTHand = hand;
        trackingPlayer = player;
    }

    // Stop tracking and reset state
    public void stopTracking() {
        if (isLit) {
            ClientNetworkHelper.sendCancelTNTPacket();
        }

        isTracking = false;
        isLit = false;
        trackingPlayer = null;
        ticksSinceLit = 0; // Reset tick counter
    }

    // Check if TNT is currently lit
    public boolean isLit() {
        return isLit;
    }

    // Check if currently tracking
    public boolean isTracking() {
        return isTracking;
    }

    // Emit smoke particles from hand holding lit TNT
    public void emitSmokeParticles(LocalPlayer player) {
        if (!isLit || trackingPlayer == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Increment tick counter
        ticksSinceLit++;

        // Calculate remaining fuse ticks
        int remainingTicks = TNT_FUSE_TICKS - ticksSinceLit;

        // Stop emitting if fuse has expired
        if (remainingTicks <= 0) {
            isLit = false; // TNT exploded in hand
            ticksSinceLit = 0;
            return;
        }

        // Get hand holding TNT from VR
        VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
        if (pose == null) return;

        VRBodyPartData hand = pose.getHand(activeTNTHand);
        if (hand == null) return;

        Vec3 handPos = hand.getPos();
        Quaternionfc rotation = hand.getRotation();

        // Calculate offset position for hand particles
        // Forward 0.15m, Up 0.1m from hand center
        Vector3f forward = new Vector3f(0, 0, -1).rotate(rotation).normalize();
        Vector3f up = new Vector3f(0, 1, 0).rotate(rotation).normalize();

        Vec3 particlePos = handPos.add(
            new Vec3(forward.x * 0.15, forward.y * 0.15, forward.z * 0.15)
        ).add(
            new Vec3(up.x * 0.1, up.y * 0.1, up.z * 0.1)
        );

        // Calculate color based on remaining fuse
        float[] color = calculateSmokeColor(remainingTicks);

        // Spawn colored smoke particle at offset position with 50% scale
        try {
            win.demistorm.client.particles.TNTSmokeParticle.spawnColoredSmoke(
                    color[0], color[1], color[2],
                    particlePos.x,
                    particlePos.y,
                    particlePos.z,
                    0.5f // 50% scale for hand particles
            );
        } catch (Exception e) {
            VRThrowingExtensions.log.debug("Failed to spawn smoke particle: {}", e.getMessage());
        }
    }

    // Calculate smoke color based on remaining fuse ticks (same logic as ThrownTNTEntity)
    private float[] calculateSmokeColor(int remainingFuse) {
        float r, g, b;

        if (remainingFuse > 75) {
            // Black/dark gray phase
            float t = (remainingFuse - 75) / 25.0f;
            r = 0.05f + t * 0.05f;
            g = 0.05f + t * 0.05f;
            b = 0.05f + t * 0.05f;
        } else if (remainingFuse > 50) {
            // Dark red to bright red phase
            float t = (remainingFuse - 50) / 25.0f;
            r = 0.3f + (1.0f - t) * 0.7f;
            g = 0.0f;
            b = 0.0f;
        } else if (remainingFuse > 25) {
            // Red to orange phase
            float t = (remainingFuse - 25) / 25.0f;
            r = 1.0f;
            g = (1.0f - t) * 0.5f;
            b = 0.0f;
        } else {
            // Orange to white phase
            float t = remainingFuse / 25.0f;
            r = 1.0f;
            g = 0.5f + (1.0f - t) * 0.5f;
            b = (1.0f - t);
        }

        return new float[]{r, g, b};
    }
}
