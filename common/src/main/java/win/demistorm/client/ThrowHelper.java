package win.demistorm.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.client.Tracker;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;
import win.demistorm.ConfigHelper;
import win.demistorm.ModCompat;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;

import java.util.Comparator;
import static win.demistorm.VRThrowingExtensions.log;

// Client throw logic
public class ThrowHelper {


    // Various literals (Main hand)
    private static boolean activeMain      = false;             // Throwing logic active
    private static boolean catchActiveMain = false;             // Catching logic active
    private static boolean useBindHeldMain  = false;            // Place/use keybind was held
    private static boolean playerCrouchedMain = false;          // Player was crouching when thrown
    private static boolean cancelBreakingMain = false;          // Cancels breaking after a certain speed
    private static ItemStack heldItemMain = ItemStack.EMPTY;    // Checks what item is in hand
    private static ThrownProjectileEntity targetProjectileMain = null; // The projectile being caught
    private static int ticksHeldMain  = 0;                      // How long trigger is pressed
    private static int catchTicksHeldMain = 0;                  // How long trigger is pressed for catching

    // Various literals (Offhand)
    private static boolean activeOff      = false;              // Throwing logic active
    private static boolean catchActiveOff = false;              // Catching logic active
    private static boolean useBindHeldOff  = false;             // Place/use keybind was held
    private static boolean playerCrouchedOff = false;           // Player was crouching when thrown
    private static boolean cancelBreakingOff = false;           // Cancels breaking after a certain speed
    private static ItemStack heldItemOff = ItemStack.EMPTY;     // Checks what item is in hand
    private static ThrownProjectileEntity targetProjectileOff = null; // The projectile being caught
    private static int ticksHeldOff  = 0;                       // How long trigger is pressed
    private static int catchTicksHeldOff = 0;                   // How long trigger is pressed for catching

    private static final TNTHelper tntHelper = new TNTHelper(); // TNT lighting tracker

    // Tracks which hand has priority for catching (null = no priority)
    private static InteractionHand allowedCatchHand = null;

    // Holds projectile and distance data for catch candidate selection
    private record ProjectileCatchCandidate(ThrownProjectileEntity projectile, double distance) {}

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
    public static boolean cancellingBreaks() { return (activeMain && cancelBreakingMain) || catchActiveMain; }
    public static boolean cancellingUse   () { return activeMain; } // Always cancel place/use while main hand throwing is active

    // Throwing logic utilizing Vivecraft's Tracker system
    public static class ThrowTracker implements Tracker {
        @Override
        public ProcessType processType() {
            return ProcessType.PER_TICK;
        }

        @Override
        public boolean isActive(LocalPlayer player) {
            return player != null && VRAPI.instance().isVRPlayer(player);
        }

        // If Tracker becomes active, main throwing mechanic
        @Override
        public void activeProcess(LocalPlayer player) {
            if (player == null || !VRAPI.instance().isVRPlayer(player)) return;

            boolean mainThrowPressed = RemapBindings.THROW.isDown();
            boolean offThrowPressed = RemapBindings.THROW_OFFHAND.isDown();
            boolean throwStackPressed = RemapBindings.THROW_STACK.isDown(); // Throw stack/null modifier keybind

            // Reset allowed catch hand at start of each tick
            allowedCatchHand = null;

            // Check if both hands are trying to catch and handle collision
            if (mainThrowPressed && offThrowPressed) {
                VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
                if (pose != null) {
                    VRBodyPartData mainHandData = pose.getHand(InteractionHand.MAIN_HAND);
                    VRBodyPartData offHandData = pose.getHand(InteractionHand.OFF_HAND);

                    if (mainHandData != null && offHandData != null) {
                        ProjectileCatchCandidate mainCandidate = findNearestProjectileWithDistance(player, mainHandData.getPos());
                        ProjectileCatchCandidate offCandidate = findNearestProjectileWithDistance(player, offHandData.getPos());

                        // If both hands target the same projectile, closer hand wins
                        if (mainCandidate != null && offCandidate != null &&
                            mainCandidate.projectile() == offCandidate.projectile()) {
                            if (mainCandidate.distance() < offCandidate.distance()) {
                                allowedCatchHand = InteractionHand.MAIN_HAND;
                            } else {
                                allowedCatchHand = InteractionHand.OFF_HAND;
                            }
                            log.debug("[VR Catch] Both hands targeting projectile {}, closer hand: {}",
                                mainCandidate.projectile().getId(), allowedCatchHand);
                        }
                    }
                }
            }

            // Handle catching for both hands
            boolean mainHandCatching = throwCatching(player, mainThrowPressed, InteractionHand.MAIN_HAND);
            boolean offHandCatching = throwCatching(player, offThrowPressed, InteractionHand.OFF_HAND);
            if (mainHandCatching || offHandCatching) {
                return;
            }

            // Emit smoke particles from hand if TNT is lit
            if (tntHelper.isLit()) {
                tntHelper.emitSmokeParticles(player);
            }

            // Process main hand throwing
            processThrowHand(player, mainThrowPressed, throwStackPressed, InteractionHand.MAIN_HAND);

            // Process offhand throwing
            processThrowHand(player, offThrowPressed, throwStackPressed, InteractionHand.OFF_HAND);
        }

        @Override
        public void inactiveProcess(LocalPlayer player) {
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

    // Process throwing logic for a specific hand
    private static void processThrowHand(LocalPlayer player, boolean throwPressed, boolean throwStackPressed, InteractionHand hand) {
        boolean active = (hand == InteractionHand.MAIN_HAND) ? activeMain : activeOff;
        VRBodyPart bodyPart = (hand == InteractionHand.MAIN_HAND) ? VRBodyPart.MAIN_HAND : VRBodyPart.OFF_HAND;

        // When throw key is pressed, start tracking
        if (!active && throwPressed) {
            ItemStack held = player.getItemInHand(hand);
            if (ModCompat.throwingDisabled(held, player, player.isCrouching(), throwStackPressed)) return;

            // Check if holding TNT for special handling (only if feature enabled)
            boolean holdingTNT = ConfigHelper.ACTIVE.throwableTNT && held.is(Items.TNT);

            // Start TNT tracking if holding TNT
            if (holdingTNT) {
                tntHelper.startTracking(player, hand);
                log.debug("[VR Throw] Started tracking TNT with flint & steel in {}", hand);
            }

            // Activates throw states
            if (hand == InteractionHand.MAIN_HAND) {
                heldItemMain = held.copy();
                ticksHeldMain = 0;
                activeMain = true;
                useBindHeldMain = throwStackPressed;
                playerCrouchedMain = player.isCrouching();
                cancelBreakingMain = false;
                log.debug("[VR Throw] Hold trace started with item: {} in {}", heldItemMain, hand);
            } else {
                heldItemOff = held.copy();
                ticksHeldOff = 0;
                activeOff = true;
                useBindHeldOff = throwStackPressed;
                playerCrouchedOff = player.isCrouching();
                cancelBreakingOff = false;
                log.debug("[VR Throw] Hold trace started with item: {} in {}", heldItemOff, hand);
            }
        }

        // Holding throw key
        else if (active && throwPressed) {
            int ticksHeld = (hand == InteractionHand.MAIN_HAND) ? ticksHeldMain : ticksHeldOff;
            ticksHeld = Math.min(ticksHeld + 1, maxPoseHistoryTicks);
            if (hand == InteractionHand.MAIN_HAND) {
                ticksHeldMain = ticksHeld;
                useBindHeldMain |= throwStackPressed;
                playerCrouchedMain = player.isCrouching();
            } else {
                ticksHeldOff = ticksHeld;
                useBindHeldOff |= throwStackPressed;
                playerCrouchedOff = player.isCrouching();
            }

            // Check for swipe motion if tracking TNT
            if (tntHelper.isTracking()) {
                if (tntHelper.checkSwipeMotion(player)) {
                    if (VRThrowingExtensions.debugMode) {
                        player.displayClientMessage(Component.literal("TNT lit!"), true);
                    }
                    log.debug("[VR Throw] TNT lit via flint & steel swipe!");
                }
            }

            // Checks arm speed to determine if it should cancel block breaking
            boolean cancelBreaking = (hand == InteractionHand.MAIN_HAND) ? cancelBreakingMain : cancelBreakingOff;
            if (!cancelBreaking) {
                VRPoseHistory hist = VRAPI.instance().getHistoricalVRPoses(player);
                if (hist != null) {
                    double speed = hist.averageSpeed(bodyPart, 2, true);
                    if (speed > speedThreshold) {
                        if (hand == InteractionHand.MAIN_HAND) {
                            cancelBreakingMain = true;
                        } else {
                            cancelBreakingOff = true;
                        }
                        log.debug("[VR Throw] speed threshold crossed, mining blocked in {}", hand);
                    }
                }
            }
        }

        // Released throw key, sends throw packet
        else if (active) {
            int ticksHeld = (hand == InteractionHand.MAIN_HAND) ? ticksHeldMain : ticksHeldOff;
            if (ticksHeld >= 5) {
                VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(player);
                if (history != null) {
                    int usedTicks = Math.min(ticksHeld, maxPoseHistoryTicks);

                    // Check how far the hand moved relative to the player over the hold duration
                    Vec3 handMovement = history.netMovement(bodyPart, usedTicks, true);

                    if (handMovement != null) {
                        double relativeMovedDist = handMovement.length();

                        if (relativeMovedDist > minThrowDistance) {
                            // Get velocity relative to player (independent of player movement)
                            Vec3 relativeVel = history.averageVelocity(bodyPart, usedTicks, true);

                            if (relativeVel != null) {
                                double velLength = relativeVel.length();

                                if (velLength >= throwVelocityThreshold) {
                                    // Get world space origin for the throw (where projectile spawns)
                                    Vec3 origin = historicalHandPosition(history, hand);
                                    double dynamicMultiplier = calculateVelocityMultiplier(velLength);
                                    Vec3 launchVel = relativeVel.scale(dynamicMultiplier);
                                    Vec3 assistedVel = AimHelper.applyAimAssist(player, origin, launchVel);

                                    // InteractionHand rotation (world space)
                                    VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
                                    assert pose != null;
                                    VRBodyPartData handData = pose.getHand(hand);
                                    Quaternionfc q = handData.getRotation();
                                    Vector3f fwd = new Vector3f(0, 0, -1).rotate(q).normalize();
                                    Vector3f up  = new Vector3f(0, 1,  0).rotate(q).normalize();
                                    Vector3f projCtrlUp  = up .sub(new Vector3f(fwd).mul(up .dot(fwd))).normalize();
                                    Vector3f projWorldUp = new Vector3f(0, 1, 0)
                                            .sub(new Vector3f(fwd).mul(fwd.y)).normalize();
                                    float rollRad = projCtrlUp.angleSigned(projWorldUp, fwd);
                                    float rollDeg = (float) Math.toDegrees(rollRad);

                                    boolean useBindHeld = (hand == InteractionHand.MAIN_HAND) ? useBindHeldMain : useBindHeldOff;
                                    boolean playerCrouched = (hand == InteractionHand.MAIN_HAND) ? playerCrouchedMain : playerCrouchedOff;

                                    // Send throw to server
                                    try {
                                        // Check if throwing lit TNT (only if feature enabled)
                                        if (ConfigHelper.ACTIVE.throwableTNT && tntHelper.isLit()) {
                                            // Send lit TNT throw packet
                                            ClientNetworkHelper.sendThrowTNTPacket(origin, assistedVel, rollDeg, hand);
                                            if (VRThrowingExtensions.debugMode) {
                                                player.displayClientMessage(Component.literal("Thrown lit TNT!"), true);
                                            }
                                            log.debug("[VR Throw] Thrown lit TNT from {}", hand);
                                        } else {
                                            // Send normal throw packet
                                            ClientNetworkHelper.sendToServer(origin, assistedVel, useBindHeld, playerCrouched, rollDeg, hand);
                                        }
                                    } catch (Exception e) {
                                        log.error("Error sending throw packet to server: {}", e.getMessage());
                                        if (hand == InteractionHand.MAIN_HAND) {
                                            resetMain();
                                        } else {
                                            resetOff();
                                        }
                                        return;
                                    }

                                    // DEBUG
                                    if (VRThrowingExtensions.debugMode) {
                                        boolean aimAssistApplied = !assistedVel.equals(launchVel);
                                        player.displayClientMessage(Component.literal(
                                                "[VR Throw] origin=" + origin +
                                                        " relativeVel=" + relativeVel +
                                                        " velLength=" + String.format("%.4f", velLength) +
                                                        " multiplier=" + String.format("%.2f", dynamicMultiplier) +
                                                        " relativeMovement=" + String.format("%.3f", relativeMovedDist) +
                                                        " aimAssist=" + aimAssistApplied +
                                                        " useBindHeld=" + useBindHeld +
                                                        " playerCrouched=" + playerCrouched +
                                                        " hand=" + hand), false);
                                    }

                                    VRClientAPI.instance().triggerHapticPulse(
                                            VRBodyPart.fromInteractionHand(hand), 0.2f);
                                } else {
                                    log.debug("[VR Throw] Relative velocity too slow: {}", velLength);
                                }
                            }
                        } else {
                            log.debug("[VR Throw] Insufficient relative movement: {}", relativeMovedDist);
                        }
                    }
                }
            } else {
                log.debug("[VR Throw] Released too early. Held {} ticks.", ticksHeld);
            }
            if (hand == InteractionHand.MAIN_HAND) {
                resetMain();
            } else {
                resetOff();
            }
        }
    }

    // Handles catching logic, returns true if catching is active and blocks throwing logic
    private static boolean throwCatching(LocalPlayer player, boolean attackPressed, InteractionHand hand) {
        boolean catchActive = (hand == InteractionHand.MAIN_HAND) ? catchActiveMain : catchActiveOff;
        ThrownProjectileEntity targetProjectile = (hand == InteractionHand.MAIN_HAND) ? targetProjectileMain : targetProjectileOff;
        int catchTicksHeld = (hand == InteractionHand.MAIN_HAND) ? catchTicksHeldMain : catchTicksHeldOff;

        // Check if player's hand slot is empty
        ItemStack activeStack = player.getItemInHand(hand);
        if (!activeStack.isEmpty()) {
            // Player switched to occupied slot, cancel any active catch
            if (catchActive) {
                if (hand == InteractionHand.MAIN_HAND) {
                    cancelCatchMain();
                } else {
                    cancelCatchOff();
                }
                log.debug("[VR Catch] Canceled: Player switched to occupied slot in {}", hand);
            }
            return false;
        }

        VRPose pose = VRClientAPI.instance().getPreTickWorldPose();
        if (pose == null) return catchActive;

        VRBodyPartData handData = pose.getHand(hand);
        if (handData == null) return catchActive;

        Vec3 handPos = handData.getPos();

        // When attack is pressed and not already catching, look for projectiles
        if (!catchActive && attackPressed) {
            // Check if hand is allowed to catch (when both hands target same projectile)
            if (allowedCatchHand != null && allowedCatchHand != hand) {
                log.debug("[VR Catch] Hand {} blocked from catching, {} has priority", hand, allowedCatchHand);
                return false;
            }

            ThrownProjectileEntity nearestProjectile = findNearestProjectile(player, handPos);
            if (nearestProjectile != null) {
                if (hand == InteractionHand.MAIN_HAND) {
                    startCatchMain(nearestProjectile);
                } else {
                    startCatchOff(nearestProjectile);
                }
                log.debug("[VR Catch] Started catching projectile with {}...", hand);
                return true;
            }
        }

        // Continue catch if projectile is found
        else if (catchActive && attackPressed) {
            if (targetProjectile == null || targetProjectile.isRemoved()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    cancelCatchMain();
                } else {
                    cancelCatchOff();
                }
                log.debug("[VR Catch] Canceled: Target projectile no longer exists");
                return false;
            }

            catchTicksHeld++;
            if (hand == InteractionHand.MAIN_HAND) {
                catchTicksHeldMain = catchTicksHeld;
            } else {
                catchTicksHeldOff = catchTicksHeld;
            }
            updateCatchMagnetism(handPos, handData.getRotation(), hand);

            // Check if projectile is close enough to complete catch
            double distanceToHand = targetProjectile.position().distanceTo(handPos);
            if (distanceToHand <= catchCompletionDistance && catchTicksHeld >= minCatchTicks) {
                if (hand == InteractionHand.MAIN_HAND) {
                    completeCatchMain();
                } else {
                    completeCatchOff();
                }
                log.debug("[VR Catch] Completed catch with {}!", hand);
                return false;
            }
            return true;
        }

        // Released attack button, cancel catch if active
        else if (catchActive) {
            if (hand == InteractionHand.MAIN_HAND) {
                cancelCatchMain();
            } else {
                cancelCatchOff();
            }
            log.debug("[VR Catch] Canceled: Attack button released for {}", hand);
            return false;
        }

        return false;
    }

    // Finds the nearest thrown item within catch range
    private static ThrownProjectileEntity findNearestProjectile(LocalPlayer player, Vec3 handPos) {
        Vec3 min = handPos.subtract(catchMaxDistance, catchMaxDistance, catchMaxDistance);
        Vec3 max = handPos.add(catchMaxDistance, catchMaxDistance, catchMaxDistance);
        AABB searchBox = new AABB(min, max);

        return player.level().getEntitiesOfClass(ThrownProjectileEntity.class, searchBox, entity -> {
                    if (entity.isRemoved()) return false;
                    double distance = entity.position().distanceTo(handPos);
                    return distance <= catchMaxDistance;
                }).stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceTo(handPos)))
                .orElse(null);
    }

    // Finds the nearest thrown item within catch range and return with distance
    private static ProjectileCatchCandidate findNearestProjectileWithDistance(LocalPlayer player, Vec3 handPos) {
        Vec3 min = handPos.subtract(catchMaxDistance, catchMaxDistance, catchMaxDistance);
        Vec3 max = handPos.add(catchMaxDistance, catchMaxDistance, catchMaxDistance);
        AABB searchBox = new AABB(min, max);

        return player.level().getEntitiesOfClass(ThrownProjectileEntity.class, searchBox, entity -> {
                    if (entity.isRemoved()) return false;
                    double distance = entity.position().distanceTo(handPos);
                    return distance <= catchMaxDistance;
                }).stream()
                .map(e -> new ProjectileCatchCandidate(e, e.position().distanceTo(handPos)))
                .min(Comparator.comparingDouble(ProjectileCatchCandidate::distance))
                .orElse(null);
    }

    // Starts catching the target projectile (main hand)
    private static void startCatchMain(ThrownProjectileEntity projectile) {
        catchActiveMain = true;
        targetProjectileMain = projectile;
        catchTicksHeldMain = 0;

        // Send catch packet to server to start magnetism
        ClientNetworkHelper.sendCatchToServer(projectile, true, InteractionHand.MAIN_HAND);
    }

    // Starts catching the target projectile (offhand)
    private static void startCatchOff(ThrownProjectileEntity projectile) {
        catchActiveOff = true;
        targetProjectileOff = projectile;
        catchTicksHeldOff = 0;

        // Send catch packet to server to start magnetism
        ClientNetworkHelper.sendCatchToServer(projectile, true, InteractionHand.OFF_HAND);
    }

    // Updates magnetism effect during catch
    private static void updateCatchMagnetism(Vec3 handPos, Quaternionfc handRotation, InteractionHand hand) {
        ThrownProjectileEntity targetProjectile = (hand == InteractionHand.MAIN_HAND) ? targetProjectileMain : targetProjectileOff;
        if (targetProjectile == null) return;

        Vec3 projectilePos = targetProjectile.position();
        Vec3 toHand = handPos.subtract(projectilePos);
        double distance = toHand.length();

        if (distance > 0.001) {
            // Apply magnetizing effect
            Vec3 magnetEffect = toHand.normalize().scale(catchMagnetStrength);
            Vec3 currentVel = targetProjectile.getDeltaMovement();
            Vec3 newVel = currentVel.scale(0.6).add(magnetEffect); // Blend with current velocity

            // Send updated velocity to server
            ClientNetworkHelper.sendCatchUpdateToServer(targetProjectile, newVel, handRotation);
        }
    }

    // Completes the catch, adding item to player inventory (main hand)
    private static void completeCatchMain() {
        if (targetProjectileMain == null) return;

        // Send completion packet to server
        ClientNetworkHelper.sendCatchCompleteToServer(targetProjectileMain);

        // Reset catch state
        resetCatchMain();

        // Haptic feedback for successful catch
        VRClientAPI.instance().triggerHapticPulse(
                VRBodyPart.fromInteractionHand(InteractionHand.MAIN_HAND), 0.5f);
    }

    // Completes the catch, adding item to player inventory (offhand)
    private static void completeCatchOff() {
        if (targetProjectileOff == null) return;

        // Send completion packet to server
        ClientNetworkHelper.sendCatchCompleteToServer(targetProjectileOff);

        // Reset catch state
        resetCatchOff();

        // Haptic feedback for successful catch
        VRClientAPI.instance().triggerHapticPulse(
                VRBodyPart.fromInteractionHand(InteractionHand.OFF_HAND), 0.5f);
    }

    // Cancels active catch and releases projectile (main hand)
    private static void cancelCatchMain() {
        if (targetProjectileMain != null) {
            ClientNetworkHelper.sendCatchToServer(targetProjectileMain, false, InteractionHand.MAIN_HAND);
        }
        resetCatchMain();
    }

    // Cancels active catch and releases projectile (offhand)
    private static void cancelCatchOff() {
        if (targetProjectileOff != null) {
            ClientNetworkHelper.sendCatchToServer(targetProjectileOff, false, InteractionHand.OFF_HAND);
        }
        resetCatchOff();
    }

    // Checks historical hand positions (world space for spawn origin)
    private static Vec3 historicalHandPosition(VRPoseHistory hist, InteractionHand hand) {
        try {
            VRPose pose2 = hist.getHistoricalData(2); // Gets pose from 2 ticks back
            VRBodyPartData hand2 = pose2.getHand(hand);
            if (hand2 != null) return hand2.getPos();
        } catch (IllegalArgumentException ignored) { }

        // Fallback to current pose if historical data isn't present (that guy joined and threw really fast!)
        VRPose now = VRClientAPI.instance().getPreTickWorldPose();
        assert now != null;
        return now.getHand(hand).getPos();
    }

    // Reset catch state (main hand)
    private static void resetCatchMain() {
        catchActiveMain = false;
        targetProjectileMain = null;
        catchTicksHeldMain = 0;
    }

    // Reset catch state (offhand)
    private static void resetCatchOff() {
        catchActiveOff = false;
        targetProjectileOff = null;
        catchTicksHeldOff = 0;
    }

    // Resets throw variables (main hand)
    private static void resetMain() {
        activeMain = false;
        useBindHeldMain = false;
        playerCrouchedMain = false;
        cancelBreakingMain = false;
        heldItemMain = ItemStack.EMPTY;
        ticksHeldMain = 0;
        tntHelper.stopTracking();
    }

    // Resets throw variables (offhand)
    private static void resetOff() {
        activeOff = false;
        useBindHeldOff = false;
        playerCrouchedOff = false;
        cancelBreakingOff = false;
        heldItemOff = ItemStack.EMPTY;
        ticksHeldOff = 0;
        tntHelper.stopTracking();
    }
}