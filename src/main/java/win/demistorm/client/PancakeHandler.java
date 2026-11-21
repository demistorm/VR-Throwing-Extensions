package win.demistorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import win.demistorm.ModCompat;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;

import java.util.Comparator;

import static win.demistorm.VRThrowingExtensions.log;

// Flatscreen throwing and catching via the drop key
public class PancakeHandler {

    // Hold states
    private enum Mode { NONE, CHARGE, CATCH_HOLD }

    private static Mode mode = Mode.NONE;                      // Current hold state
    private static int ticksHeld = 0;                          // Drop key hold length
    private static boolean catchActive = false;                // Catching a projectile right now
    private static ThrownProjectileEntity targetProjectile = null; // Projectile being caught
    private static int catchTicksHeld = 0;                     // Catch hold length

    // Tunables
    private static final double cameraVelMultiplier    = 0.08; // Degrees per tick into blocks per tick
    private static final int    holdThresholdTicks     = 4;    // Hold length separating taps from throws
    private static final int    maxCameraLookbackTicks = 6;    // Camera window for throw power
    private static final double cameraTurnThreshold    = 3.0;  // Min degrees per tick or the throw cancels
    private static final double maxThrowSpeed          = 3.0;  // Speed cap (arms have one and mice do not)
    private static final double originForwardOffset    = 0.3;  // Spawn distance in front of the eyes

    // Camera rotation history only (strafing never adds power)
    private static final int RING_SIZE = 8;
    private static final float[] yawRing = new float[RING_SIZE];
    private static final float[] pitchRing = new float[RING_SIZE];
    private static int ringHead = 0;
    private static int ringFilled = 0;

    // Called by the mixin at the head of vanilla key handling
    public static void onHandleKeybinds() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator()) return;
        if (VRAbstraction.isVRActive()) return;

        boolean engage;
        if (mode != Mode.NONE) {
            engage = true; // Keep swallowing repeats
        } else if (player.getMainHandItem().isEmpty()) {
            engage = true; // Empty hand means catch mode
        } else {
            ItemStack held = player.getMainHandItem();
            engage = !ModCompat.throwingDisabled(held, player, player.isCrouching(), mc.hasControlDown());
        }
        if (!engage) return;

        // First click starts the hold (the rest are swallowed spam drops)
        while (mc.options.keyDrop.consumeClick()) {
            if (mode == Mode.NONE) startHold(player);
        }
    }

    // Client tick entry (registered in VRThrowingExtensionsClient)
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player != null) sampleCamera(player);

        if (mode == Mode.NONE) {
            ThrowCancels.setPancake(false, false);
            return;
        }

        // Hold cancels when the gates break
        if (player == null || VRAbstraction.isVRActive()) {
            resetState();
            return;
        }
        if (mc.screen != null) {
            if (catchActive) cancelCatch();
            // Swallow clicks queued behind the screen
            while (mc.options.keyDrop.consumeClick()) { }
            resetState();
            return;
        }

        boolean dropDown = mc.options.keyDrop.isDown();

        if (mode == Mode.CHARGE) {
            ThrowCancels.setPancake(true, true);
            if (!dropDown) {
                resolveCharge(player, mc.hasControlDown());
            } else {
                ticksHeld++;
            }
        } else {
            ThrowCancels.setPancake(true, false);
            runCatch(player, dropDown);
        }
    }

    // Press edge of the drop key
    private static void startHold(LocalPlayer player) {
        if (player.getMainHandItem().isEmpty()) {
            mode = Mode.CATCH_HOLD;
            log.debug("[Pancake Catch] Hold started");
        } else {
            mode = Mode.CHARGE;
            ticksHeld = 0;
            player.swing(InteractionHand.MAIN_HAND);
            log.debug("[Pancake Throw] Hold started with {}", player.getMainHandItem());
        }
    }

    // Release edge of a throw hold
    private static void resolveCharge(LocalPlayer player, boolean ctrlHeld) {
        if (ticksHeld < holdThresholdTicks) {
            // Quick tap so do the vanilla drop
            if (player.drop(ctrlHeld)) {
                player.swing(InteractionHand.MAIN_HAND);
            }
            log.debug("[Pancake Throw] Tap drop after {} ticks", ticksHeld);
        } else if (!player.getMainHandItem().isEmpty()) {
            tryThrow(player, ctrlHeld);
        }
        resetState();
    }

    // Flick powered throw
    private static void tryThrow(LocalPlayer player, boolean ctrlHeld) {
        double angSpeed = cameraAngularSpeed(maxCameraLookbackTicks);
        if (angSpeed < cameraTurnThreshold) {
            log.debug("[Pancake Throw] Canceled, camera too slow ({}/tick)", angSpeed);
            return;
        }

        // Look direction at release
        Vec3 dir = lookVector(0);
        double speed = Math.min(angSpeed * cameraVelMultiplier, maxThrowSpeed);
        Vec3 velocity = dir.scale(speed);
        Vec3 origin = player.getEyePosition().add(dir.scale(originForwardOffset));
        Vec3 assisted = AimHelper.applyAimAssist(player, origin, velocity);

        ClientNetworkHelper.sendToServer(origin, assisted, ctrlHeld, player.isCrouching(), 0.0f, InteractionHand.MAIN_HAND);

        if (VRThrowingExtensions.debugMode) {
            player.displayClientMessage(Component.literal(
                    "[Pancake Throw] angSpeed=" + String.format("%.1f", angSpeed) +
                    " speed=" + String.format("%.2f", speed) +
                    " ctrl=" + ctrlHeld), true);
        }
        log.debug("[Pancake Throw] Threw {} at {} blocks per tick", player.getMainHandItem(), speed);
    }

    // Catch scanning and magnetism while held
    private static void runCatch(LocalPlayer player, boolean dropDown) {
        ItemStack held = player.getMainHandItem();

        // Filled hand turns the hold into a throw charge (VR does the same)
        if (!held.isEmpty()) {
            if (catchActive) cancelCatch();
            if (!ModCompat.throwingDisabled(held, player, player.isCrouching(), Minecraft.getInstance().hasControlDown())) {
                mode = Mode.CHARGE;
                ticksHeld = 0;
            } else {
                resetState();
            }
            return;
        }

        if (!dropDown) {
            if (catchActive) cancelCatch();
            resetState();
            return;
        }

        Vec3 eyePos = player.getEyePosition();

        if (!catchActive) {
            ThrownProjectileEntity nearest = findNearestProjectile(player, eyePos);
            if (nearest != null) {
                catchActive = true;
                targetProjectile = nearest;
                catchTicksHeld = 0;
                ClientNetworkHelper.sendCatchToServer(nearest, true, InteractionHand.MAIN_HAND);
                log.debug("[Pancake Catch] Started catching projectile {}", nearest.getId());
            }
            return;
        }

        if (targetProjectile == null || targetProjectile.isRemoved()) {
            // Keep scanning for a new target while the key stays down
            cancelCatch();
            return;
        }

        catchTicksHeld++;

        // Pull the projectile toward the eyes
        Vec3 toEye = eyePos.subtract(targetProjectile.position());
        double distance = toEye.length();
        if (distance > 0.001) {
            Vec3 magnet = toEye.normalize().scale(ThrowHelper.catchMagnetStrength);
            Vec3 newVel = targetProjectile.getDeltaMovement().scale(0.6).add(magnet);
            ClientNetworkHelper.sendCatchUpdateToServer(targetProjectile, newVel, new Quaternionf());
        }

        if (distance <= ThrowHelper.catchCompletionDistance && catchTicksHeld >= ThrowHelper.minCatchTicks) {
            ClientNetworkHelper.sendCatchCompleteToServer(targetProjectile);
            log.debug("[Pancake Catch] Completed catch");
            resetState();
        }
    }

    // Stops an active catch and frees the projectile
    private static void cancelCatch() {
        if (targetProjectile != null) {
            ClientNetworkHelper.sendCatchToServer(targetProjectile, false, InteractionHand.MAIN_HAND);
        }
        catchActive = false;
        targetProjectile = null;
        catchTicksHeld = 0;
    }

    // Clears all hold state
    private static void resetState() {
        mode = Mode.NONE;
        ticksHeld = 0;
        catchActive = false;
        targetProjectile = null;
        catchTicksHeld = 0;
        ThrowCancels.setPancake(false, false);
    }

    // Nearest thrown item inside the catch radius of the eyes
    private static ThrownProjectileEntity findNearestProjectile(LocalPlayer player, Vec3 eyePos) {
        Vec3 min = eyePos.subtract(ThrowHelper.catchMaxDistance, ThrowHelper.catchMaxDistance, ThrowHelper.catchMaxDistance);
        Vec3 max = eyePos.add(ThrowHelper.catchMaxDistance, ThrowHelper.catchMaxDistance, ThrowHelper.catchMaxDistance);
        AABB searchBox = new AABB(min, max);

        return player.level().getEntitiesOfClass(ThrownProjectileEntity.class, searchBox, entity -> {
                    if (entity.isRemoved()) return false;
                    return entity.position().distanceTo(eyePos) <= ThrowHelper.catchMaxDistance;
                }).stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceTo(eyePos)))
                .orElse(null);
    }

    // Records the camera angles for this tick
    private static void sampleCamera(LocalPlayer player) {
        yawRing[ringHead] = player.getYRot();
        pitchRing[ringHead] = player.getXRot();
        ringHead = (ringHead + 1) % RING_SIZE;
        if (ringFilled < RING_SIZE) ringFilled++;
    }

    // Yaw from N ticks back (0 is the latest sample)
    private static float yawAt(int ticksBack) {
        return yawRing[(ringHead - 1 - ticksBack + RING_SIZE * 2) % RING_SIZE];
    }

    // Pitch from N ticks back
    private static float pitchAt(int ticksBack) {
        return pitchRing[(ringHead - 1 - ticksBack + RING_SIZE * 2) % RING_SIZE];
    }

    // Mean rotation speed in degrees per tick
    private static double cameraAngularSpeed(int window) {
        window = Math.min(window, ringFilled - 1);
        if (window < 1) return 0.0;

        double total = 0.0;
        for (int i = 0; i < window; i++) {
            float yawDelta = Mth.wrapDegrees(yawAt(i) - yawAt(i + 1));
            float pitchDelta = pitchAt(i) - pitchAt(i + 1);
            total += Math.sqrt((double) yawDelta * yawDelta + (double) pitchDelta * pitchDelta);
        }
        return total / window;
    }

    // Look vector from N ticks back
    private static Vec3 lookVector(int ticksBack) {
        return Vec3.directionFromRotation(pitchAt(ticksBack), yawAt(ticksBack));
    }
}
