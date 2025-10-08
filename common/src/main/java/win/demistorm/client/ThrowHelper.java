package win.demistorm.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.client.Tracker;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;
import win.demistorm.ModCompat;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;
import win.demistorm.client.config.ClientNetworkHelper;

import java.util.Comparator;
import static win.demistorm.VRThrowingExtensions.log;

// Client throw logic
public class ThrowHelper {

    // Various literals
    private static boolean active          = false;          // Throwing logic active
    private static boolean catchActive     = false;          // Catching logic active
    private static boolean throwWholeStack = false;          // Whether the whole stack should be thrown
    private static boolean cancelBreaking  = false;          // Cancels breaking after a certain speed
    private static Vec3d relativeStartPoint = Vec3d.ZERO;    // Hand position relative to player when starting
    private static ItemStack heldItem   = ItemStack.EMPTY;   // Checks what item is in hand
    private static ThrownProjectileEntity targetProjectile = null; // The projectile being caught
    private static int ticksHeld  = 0;                       // How long trigger is pressed
    private static int catchTicksHeld = 0;                   // How long trigger is pressed for catching

    // Tunables
    private static final double minThrowDistance        = 0.08; // Min arm movement to activate throw
    private static final int    maxPoseHistoryTicks     = 6;    // How many ticks to look back for velocity
    private static final double speedThreshold          = 0.10; // How fast you can move your arm before canceling block breaking
    private static final double throwVelocityThreshold  = 0.06; // Min velocity to activate throw

    // Velocity multiplier curve tunables
    private static final double weakVelThreshold = 0.06;        // Vel around this will be considered "weak"
    private static final double strongVelThreshold = 0.30;      // Vel around this will be considered "strong"
    private static final double weakMultiplier = 3.5;           // Multiplier for weak throws
    private static final double strongMultiplier = 8.0;         // Multiplier for strong throws

    // Catching tunables
    private static final double catchMaxDistance        = 3.0;  // Max distance to start catching (in blocks)
    private static final double catchMagnetStrength     = 0.10; // Magnetizing effect strength
    private static final double catchCompletionDistance = 0.2;  // Distance to complete catch
    private static final int    minCatchTicks           = 3;    // Minimum ticks to hold before catch completes

    // Initialization is done by the tracker in VRThrowingExtensionsClient now

    // Interaction callbacks
    public static boolean cancellingBreaks() { return (active && cancelBreaking) || catchActive; }
    public static boolean cancellingUse   () { return active; } // Always cancel place/use while throwing is active

    // Throwing logic utilizing Vivecraft's Tracker system
    public static class ThrowTracker implements Tracker {
        @Override
        public ProcessType processType() {
            return ProcessType.PER_TICK;
        }

        @Override
        public boolean isActive(ClientPlayerEntity player) {
            return player != null && VRAPI.instance().isVRPlayer(player);
        }

        // If Tracker becomes active, main throwing mechanic
        @Override
        public void activeProcess(ClientPlayerEntity player) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player == null || !VRAPI.instance().isVRPlayer(player)) return;

            boolean attackPressed = mc.options.attackKey.isPressed(); // Attack/Destroy keybind
            boolean placePressed = mc.options.useKey.isPressed();     // Place/Use keybind

            // Handle catching logic first
            if (throwCatching(player, attackPressed)) {
                return; // Skip throwing logic if catching is active
            }

            // When Attack/Destroy is pressed, start Tracking
            if (!active && attackPressed) {
                ItemStack held = player.getMainHandStack();
                if (ModCompat.throwingDisabled(held)) return;

                VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
                if (pose == null) return;

                VRBodyPartData hand = pose.getHand(Hand.MAIN_HAND);
                if (hand == null) return;

                // Store both absolute and relative positions
                Vec3d handWorldPos = hand.getPos();
                Vec3d playerPos = player.getPos();

                // Activates throw states
                relativeStartPoint = handWorldPos.subtract(playerPos); // Relative start point for distance tracking
                heldItem = held.copy();
                ticksHeld = 0;
                active = true;
                throwWholeStack = placePressed;    // Throws the whole stack if pressed
                cancelBreaking = false;            // Doesn't cancel breaking until speed is too fast
                log.debug("[VR Throw] Hold trace started with item: {} at relative pos: {}", heldItem, relativeStartPoint);
            }

            // Holding Attack/Destroy
            else if (active && attackPressed) {
                ticksHeld        = Math.min(ticksHeld + 1, maxPoseHistoryTicks);
                throwWholeStack |= placePressed;         // Throws whole stack

                // Checks arm speed to determine if it should cancel block breaking
                if (!cancelBreaking) {
                    VRPoseHistory hist = VRAPI.instance().getHistoricalVRPoses(player);
                    if (hist != null) {
                        double speed = hist.averageSpeed(VRBodyPart.MAIN_HAND, 2);
                        if (speed > speedThreshold) {
                            cancelBreaking = true;
                            log.debug("[VR Throw] speed threshold crossed, mining blocked");
                        }
                    }
                }
            }

            // Released Attack/Destroy, sends throw packet
            else if (active) {
                if (ticksHeld >= 5) {
                    VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(player);
                    if (history != null) {
                        int usedTicks = Math.min(ticksHeld, maxPoseHistoryTicks);

                        // Calculate current relative position
                        Vec3d currentHandPos = history.averagePosition(VRBodyPart.MAIN_HAND, usedTicks);
                        Vec3d currentPlayerPos = player.getPos();
                        assert currentHandPos != null;
                        Vec3d currentRelativePos = currentHandPos.subtract(currentPlayerPos);

                        // Check relative movement
                        double relativeMovedDist = relativeStartPoint.distanceTo(currentRelativePos);

                        if (relativeMovedDist > minThrowDistance) {
                            // Subtract horizontal movement from velocity
                            Vec3d rawHandVel = history.averageVelocity(VRBodyPart.MAIN_HAND, usedTicks);
                            Vec3d playerHorizontalVel = new Vec3d(player.getVelocity().x, 0, player.getVelocity().z);
                            assert rawHandVel != null;
                            Vec3d relativeVel = rawHandVel.subtract(playerHorizontalVel);

                            double velLength = relativeVel.length();

                            if (velLength >= throwVelocityThreshold) {
                                Vec3d origin = historicalHandPosition(history);
                                double dynamicMultiplier = calculateVelocityMultiplier(velLength);
                                Vec3d launchVel = relativeVel.multiply(dynamicMultiplier);
                                Vec3d assistedVel = AimHelper.applyAimAssist(player, origin, launchVel);

                                // Hand rotation
                                VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
                                assert pose != null;
                                VRBodyPartData hand = pose.getHand(Hand.MAIN_HAND);
                                Quaternionfc q = hand.getRotation();
                                Vector3f fwd = new Vector3f(0, 0, -1).rotate(q).normalize();
                                Vector3f up  = new Vector3f(0, 1,  0).rotate(q).normalize();
                                Vector3f projCtrlUp  = up .sub(new Vector3f(fwd).mul(up .dot(fwd))).normalize();
                                Vector3f projWorldUp = new Vector3f(0, 1, 0)
                                        .sub(new Vector3f(fwd).mul(fwd.y)).normalize();
                                float rollRad = projCtrlUp.angleSigned(projWorldUp, fwd);
                                float rollDeg = (float) Math.toDegrees(rollRad);

                                // Send throw to server
                                ClientNetworkHelper.sendToServer(origin, assistedVel, throwWholeStack, rollDeg);

                                // DEBUG
                                if (VRThrowingExtensions.debugMode) {
                                    boolean aimAssistApplied = !assistedVel.equals(launchVel);
                                    player.sendMessage(Text.literal(
                                            "[VR Throw] origin=" + origin +
                                                    " rawHandVel=" + rawHandVel +
                                                    " playerHorizontalVel=" + playerHorizontalVel +
                                                    " relativeVel=" + relativeVel +
                                                    " velLength=" + String.format("%.4f", velLength) +
                                                    " multiplier=" + String.format("%.2f", dynamicMultiplier) +
                                                    " relativeMovement=" + String.format("%.3f", relativeMovedDist) +
                                                    " aimAssist=" + aimAssistApplied +
                                                    " stack=" + throwWholeStack), false);
                                }

                                VRClientAPI.instance().triggerHapticPulse(
                                        VRBodyPart.fromInteractionHand(Hand.MAIN_HAND), 0.2f);
                            } else {
                                log.debug("[VR Throw] Relative velocity too slow: {}", velLength);
                            }
                        } else {
                            log.debug("[VR Throw] Insufficient relative movement: {}", relativeMovedDist);
                        }
                    }
                } else {
                    log.debug("[VR Throw] Released too early. Held {} ticks.", ticksHeld);
                }
                reset();
            }
        }

        @Override
        public void inactiveProcess(ClientPlayerEntity player) {
            // Just here because Tracker calls for it I guess (doesn't seem to error if I remove though?)
        }
    }

    // Dynamic velocity multiplier with smooth curve
    private static double calculateVelocityMultiplier(double velocity) {
        // Below weak threshold → always weak multiplier
        if (velocity <= weakVelThreshold) {
            return weakMultiplier;
        }
        // Above strong threshold counts as a strong throw, not any stronger
        if (velocity >= strongVelThreshold) {
            return strongMultiplier;
        }

        // Interpolate between weak and strong
        double t = (velocity - weakVelThreshold) /
                (strongVelThreshold - weakVelThreshold);

        // Quadratic curve for a natural ramp
        t = t * t;

        return weakMultiplier + t * (strongMultiplier - weakMultiplier);
    }

    // Handles catching logic, returns true if catching is active and blocks throwing logic
    private static boolean throwCatching(ClientPlayerEntity player, boolean attackPressed) {
        // Check if player's active slot is empty
        ItemStack activeStack = player.getMainHandStack();
        if (!activeStack.isEmpty()) {
            // Player switched to occupied slot, cancel any active catch
            if (catchActive) {
                cancelCatch();
                log.debug("[VR Catch] Canceled: Player switched to occupied slot");
            }
            return false;
        }

        VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
        if (pose == null) return catchActive;

        VRBodyPartData hand = pose.getHand(Hand.MAIN_HAND);
        if (hand == null) return catchActive;

        Vec3d handPos = hand.getPos();

        // When attack is pressed and not already catching, look for projectiles
        if (!catchActive && attackPressed) {
            ThrownProjectileEntity nearestProjectile = findNearestProjectile(player, handPos);
            if (nearestProjectile != null) {
                startCatch(nearestProjectile);
                log.debug("[VR Catch] Started catching projectile...");
                return true;
            }
        }

        // Continue catch if projectile is found
        else if (catchActive && attackPressed) {
            if (targetProjectile == null || targetProjectile.isRemoved()) {
                cancelCatch();
                log.debug("[VR Catch] Canceled: Target projectile no longer exists");
                return false;
            }

            catchTicksHeld++;
            updateCatchMagnetism(handPos, hand.getRotation());

            // Check if projectile is close enough to complete catch
            double distanceToHand = targetProjectile.getPos().distanceTo(handPos);
            if (distanceToHand <= catchCompletionDistance && catchTicksHeld >= minCatchTicks) {
                completeCatch();
                log.debug("[VR Catch] Completed catch!");
                return false;
            }
            return true;
        }

        // Released attack button, cancel catch if active
        else if (catchActive) {
            cancelCatch();
            log.debug("[VR Catch] Canceled: Attack button released");
            return false;
        }

        return catchActive;
    }

    // Finds the nearest thrown item within catch range
    private static ThrownProjectileEntity findNearestProjectile(ClientPlayerEntity player, Vec3d handPos) {
        Box searchBox = Box.of(handPos, catchMaxDistance * 2, catchMaxDistance * 2, catchMaxDistance * 2);

        return player.getWorld().getEntitiesByClass(ThrownProjectileEntity.class, searchBox, entity -> {
                    if (entity.isRemoved()) return false;
                    double distance = entity.getPos().distanceTo(handPos);
                    return distance <= catchMaxDistance;
                }).stream()
                .min(Comparator.comparingDouble(e -> e.getPos().distanceTo(handPos)))
                .orElse(null);
    }

    // Starts catching the target projectile
    private static void startCatch(ThrownProjectileEntity projectile) {
        catchActive = true;
        targetProjectile = projectile;
        catchTicksHeld = 0;

        // Send catch packet to server to start magnetism
        ClientNetworkHelper.sendCatchToServer(projectile, true);
    }

    // Updates magnetism effect during catch
    private static void updateCatchMagnetism(Vec3d handPos, Quaternionfc handRotation) {
        if (targetProjectile == null) return;

        Vec3d projectilePos = targetProjectile.getPos();
        Vec3d toHand = handPos.subtract(projectilePos);
        double distance = toHand.length();

        if (distance > 0.001) {
            // Apply magnetizing effect
            Vec3d magnetEffect = toHand.normalize().multiply(catchMagnetStrength);
            Vec3d currentVel = targetProjectile.getVelocity();
            Vec3d newVel = currentVel.multiply(0.6).add(magnetEffect); // Blend with current velocity

            // Send updated velocity to server
            ClientNetworkHelper.sendCatchUpdateToServer(targetProjectile, newVel, handRotation);
        }
    }

    // Completes the catch, adding item to player inventory
    private static void completeCatch() {
        if (targetProjectile == null) return;

        // Send completion packet to server
        ClientNetworkHelper.sendCatchCompleteToServer(targetProjectile);

        // Reset catch state
        resetCatch();

        // Haptic feedback for successful catch
        VRClientAPI.instance().triggerHapticPulse(
                VRBodyPart.fromInteractionHand(Hand.MAIN_HAND), 0.5f);
    }

    // Cancels active catch and releases projectile
    private static void cancelCatch() {
        if (targetProjectile != null) {
            ClientNetworkHelper.sendCatchToServer(targetProjectile, false);
        }
        resetCatch();
    }

    // Checks historical hand positions
    private static Vec3d historicalHandPosition(VRPoseHistory hist) {
        try {
            VRPose pose2 = hist.getHistoricalData(2); // Gets pose from 2 ticks back
            VRBodyPartData hand2 = pose2.getHand(Hand.MAIN_HAND);
            if (hand2 != null) return hand2.getPos();
        } catch (IllegalArgumentException ignored) { }

        // Fallback to current pose if historical data isn't present (that guy joined and threw really fast!)
        VRPose now = VRClientAPI.instance().getPreTickWorldPose();
        assert now != null;
        return now.getHand(Hand.MAIN_HAND).getPos();
    }

    // Reset catch state
    private static void resetCatch() {
        catchActive = false;
        targetProjectile = null;
        catchTicksHeld = 0;
    }

    // Resets throw variables
    private static void reset() {
        active = false;
        throwWholeStack = false;
        cancelBreaking = false;
        relativeStartPoint = Vec3d.ZERO;  // NEW: Reset relative tracking
        heldItem = ItemStack.EMPTY;
        ticksHeld = 0;
    }
}