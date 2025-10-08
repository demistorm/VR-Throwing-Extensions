package win.demistorm.effects;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;

import java.util.HashSet;
import java.util.Set;

public final class BoomerangEffect {
    // Base return speed
    public static final double baseReturnSpeed = 1.00;

    // Distance-based speed scaling
    public static final double closeDistance = 3.0;
    public static final double farDistance = 25.0;
    public static final double closeSpeedMultiplier = 0.3;
    public static final double farSpeedMultiplier = 2.0;
    public static final double scalingCurve = 1.2;

    // Return arc tunables
    public static final double arcGain = 0.6;      // Roughly how big the arc should be
    public static final double arcMin = 0.8;       // Min offset in blocks
    public static final double arcMax = 6.0;       // Max offset in blocks

    // How quickly the offset collapses back to the origin each server step (5 ticks atm)
    public static final double arcDecayPerStep = 0.80; // Higher is gentler, lower is more dramatic (C vs V)

    // How sharply projectile can turn toward the current target per server step
    public static final double maxTurnRateNear = Math.toRadians(30); // Within about 8 blocks
    public static final double maxTurnRateFar  = Math.toRadians(55); // When far away

    // Lateral boost at bounce so the arc is immediately visible
    public static final double lateralStartBoost = 0.35; // Portion of bounce speed to inject immediately

    // Damping and speed clamping
    public static final double dampFactor = 0.95;
    public static final double maxOverTarget = 1.6;
    public static final double minUnderTarget = 0.45;

    // This will be used in 1.2 where weapons/tools will be explicitly listed
    public static final Set<Item> bounceTools = new HashSet<>();
    static {
        Registries.ITEM.stream().filter(i -> !Registries.ITEM.getId(i)
                        .equals(Identifier.of("minecraft","air")))
                .forEach(bounceTools::add);
    }
    public static boolean canBounce(Item i) {
        return bounceTools.contains(i);
    }

    // Starts the boomerang-style return path
    public static void startBounce(ThrownProjectileEntity proj) {
        proj.hasBounced = true;
        proj.bounceActive = true;

        Vec3d currentPos = proj.getPos();
        Vec3d toOrigin = proj.originalThrowPos.subtract(currentPos);
        double distanceToOrigin = toOrigin.length();

        if (distanceToOrigin < 0.1) {
            VRThrowingExtensions.log.debug("[Boomerang] Too close to origin, dropping normally");
            proj.bounceActive = false;
            return;
        }

        Vec3d dir = toOrigin.normalize();

        // Roll, controls curve plane
        double rollRad = Math.toRadians(proj.getHandRoll());

        // Rotate the projectile based on roll to arc back the way it was thrown sorta
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d upRolled = rotateAroundAxis(worldUp, dir, rollRad);
        // Make sure upRolled is perpendicular to dir (project out any component along dir)
        Vec3d upProj = projectPerp(upRolled, dir);      // Vertical-ish curve axis (when roll ~ 0)
        Vec3d right  = dir.crossProduct(upRolled).normalize(); // Horizontal-ish curve axis (when roll ~ 90°)

        // Blend vertical and horzontal direciton based on roll
        double cosR = Math.cos(rollRad);
        double sinR = Math.sin(rollRad);
        Vec3d curveDir = upProj.multiply(cosR).add(right.multiply(sinR)).normalize();

        // Flips return arc direction (swap < and >), currently have it so that it returns the way it was thrown *shrug*
        proj.bounceInverse = proj.getHandRoll() < 0.0f;

        // Plane normal is dir × curveDir (fixed for the whole flight)
        Vec3d planeNormal = dir.crossProduct(curveDir).normalize();
        if (proj.bounceInverse)            // optional mirror
            planeNormal = planeNormal.multiply(-1);

        double arcMag = MathHelper.clamp(distanceToOrigin * arcGain, arcMin, arcMax);

        proj.bouncePlaneNormal = planeNormal;
        proj.bounceArcMag      = arcMag;

        // Start speed
        double speedMultiplier = calculateSpeedMultiplier(distanceToOrigin);
        double bounceSpeed = baseReturnSpeed * speedMultiplier;

        // Add lateral velocity to immediately show the curve
        Vec3d baseVel = dir.multiply(bounceSpeed);
        Vec3d lateralVel = curveDir.multiply(bounceSpeed * lateralStartBoost);
        Vec3d finalVel = baseVel.add(lateralVel).add(0, 0.02, 0); // small up bias

        proj.setVelocity(finalVel);
        proj.setNoGravity(true);

        if (!proj.getWorld().isClient()) {
            proj.getWorld().playSound(null, proj.getBlockPos(),
                    SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS,
                    0.6f, 1.5f);
        }

        // DEBUG
        VRThrowingExtensions.log.debug(
                "[Boomerang] Projectile {} started return. Dist={}. Roll={}°, Speed={} (mult={}), ArcMag={}",
                proj.getId(), String.format("%.2f", distanceToOrigin),
                String.format("%.1f", proj.getHandRoll()),
                String.format("%.3f", bounceSpeed),
                String.format("%.2f", speedMultiplier),
                String.format("%.2f", arcMag)
        );
    }

    // Returns true when return is finished
    public static boolean tickReturn(ThrownProjectileEntity proj) {
        Vec3d currentPos = proj.getPos();
        Vec3d toOrigin = proj.originalThrowPos.subtract(currentPos);
        double distSq = toOrigin.lengthSquared();

        // Finish if we reached or overshot the origin
        boolean reachedOrigin = false;
        if (distSq < 0.36) {
            reachedOrigin = true;
            VRThrowingExtensions.log.debug("[Boomerang] Projectile {} reached origin (dist={})",
                    proj.getId(), String.format("%.3f", Math.sqrt(distSq)));
        } else {
            Vec3d currentVel = proj.getVelocity();
            if (currentVel.length() > 0.01) {
                double dot = currentVel.normalize().dotProduct(toOrigin.normalize());
                if (dot < -0.8 && distSq < 4.0) {
                    reachedOrigin = true;
                    VRThrowingExtensions.log.debug("[Boomerang] Projectile {} overshot origin (dist={}, dot={})",
                            proj.getId(), String.format("%.3f", Math.sqrt(distSq)),
                            String.format("%.3f", dot));
                }
            }
        }
        if (reachedOrigin) {
            return true;
        }

        // Decaying arc target (steer toward origin + offset)
        Vec3d target = proj.originalThrowPos.add(proj.bounceCurveOffset);

        // Calc steering toward the decaying target
        Vec3d toTarget = target.subtract(currentPos);
        double distance = toTarget.length();
        if (distance < 0.0001) {
            return false;
        }
        Vec3d wantDir = toTarget.normalize();

        Vec3d currentVel = proj.getVelocity();

        // Distance-based speed target
        double speedMultiplier = calculateSpeedMultiplier(Math.sqrt(distSq));
        double targetSpeed = baseReturnSpeed * speedMultiplier;

        // Turn towards the target with a capped turn rate
        double turnRate = MathHelper.lerp(
                MathHelper.clamp((float)(distance / farDistance), 0.0f, 1.0f),
                maxTurnRateNear,
                maxTurnRateFar
        );
        Vec3d turned = turnTowards(currentVel, wantDir, turnRate);

        // Clamp and damp speeds
        Vec3d newVel = turned.multiply(dampFactor);
        double newSpeed = newVel.length();
        double maxSpeed = targetSpeed * maxOverTarget;
        double minSpeed = targetSpeed * minUnderTarget;

        if (newSpeed > maxSpeed) {
            newVel = newVel.normalize().multiply(maxSpeed);
        } else if (newSpeed < minSpeed && newSpeed > 0.0001) {
            newVel = newVel.normalize().multiply(minSpeed);
        }

        proj.setVelocity(newVel);

        // Collapse faster when near (so it swings back into the player's hand sorta)
        double nearFactor = MathHelper.clamp(1.0 - (distance / farDistance), 0.0, 0.8);
        double decay = MathHelper.lerp(nearFactor, arcDecayPerStep, arcDecayPerStep * 0.75);
        proj.bounceCurveOffset = proj.bounceCurveOffset.multiply(decay);

        if (proj.age % 20 == 0) {
            VRThrowingExtensions.log.debug("[Boomerang] Return: dist={}, speed={}, target={}, arcLen={}",
                    String.format("%.2f", Math.sqrt(distSq)),
                    String.format("%.3f", newVel.length()),
                    String.format("%.3f", targetSpeed),
                    String.format("%.2f", proj.bounceCurveOffset.length()));
        }
        return false;
    }

    // Speed multiplier curve
    private static double calculateSpeedMultiplier(double distance) {
        if (distance <= closeDistance) return closeSpeedMultiplier;
        if (distance >= farDistance)   return farSpeedMultiplier;

        double t = (distance - closeDistance) / (farDistance - closeDistance);
        t = Math.pow(t, scalingCurve);
        return MathHelper.lerp((float)t, (float)closeSpeedMultiplier, (float)farSpeedMultiplier);
    }

    // Rotate vector v around axis by angle radians
    private static Vec3d rotateAroundAxis(Vec3d v, Vec3d axis, double angle) {
        Vec3d k = axis.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = v.dotProduct(k);

        Vec3d term1 = v.multiply(cos);
        Vec3d term2 = k.crossProduct(v).multiply(sin);
        Vec3d term3 = k.multiply(dot * (1.0 - cos));

        return term1.add(term2).add(term3);
    }

    // Project vector v onto plane perpendicular to normal
    private static Vec3d projectPerp(Vec3d v, Vec3d normal) {
        double d = v.dotProduct(normal);
        return v.subtract(normal.multiply(d)).normalize();
    }

    // Turn the velocity vector toward a direction by at most maxAngle (keeping magnitude)
    private static Vec3d turnTowards(Vec3d currentVel, Vec3d wantDir, double maxAngle) {
        double speed = currentVel.length();
        if (speed < 1e-6) {
            return wantDir.multiply(speed);
        }
        Vec3d curDir = currentVel.normalize();
        double dot = MathHelper.clamp(curDir.dotProduct(wantDir), -1.0, 1.0);
        double angle = Math.acos(dot);

        if (angle <= maxAngle) {
            return wantDir.multiply(speed);
        }
        // Rotate curDir toward wantDir by maxAngle around the rotation axis
        Vec3d axis = curDir.crossProduct(wantDir);
        if (axis.lengthSquared() < 1e-9) {
            // Parallel or anti-parallel, just lerp directions
            Vec3d turnDir = curDir.multiply(1.0 - 1e-3).add(wantDir.multiply(1e-3)).normalize();
            return turnDir.multiply(speed);
        }
        Vec3d rotated = rotateAroundAxis(curDir, axis, maxAngle);
        return rotated.normalize().multiply(speed);
    }

    private BoomerangEffect() { }
}