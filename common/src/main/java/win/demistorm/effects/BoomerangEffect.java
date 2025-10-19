package win.demistorm.effects;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import win.demistorm.ThrownProjectileEntity;
import win.demistorm.VRThrowingExtensions;

import java.util.HashSet;
import java.util.Set;

// Makes weapons return after hitting (boomerang effect)
public final class BoomerangEffect {
    // Base speed for returning weapons
    public static final double baseReturnSpeed = 1.00;

    // Speed changes based on distance
    public static final double closeDistance = 3.0;
    public static final double farDistance = 25.0;
    public static final double closeSpeedMultiplier = 0.3;
    public static final double farSpeedMultiplier = 2.0;
    public static final double scalingCurve = 1.2;

    // Arc shape settings
    public static final double arcGain = 0.6;      // How big the curve is
    public static final double arcMin = 0.8;       // Minimum arc height
    public static final double arcMax = 6.0;       // Maximum arc height

    // How fast arc shrinks back toward target
    public static final double arcDecayPerStep = 0.80; // Higher = gentler, lower = dramatic

    // Max turn angle per update
    public static final double maxTurnRateNear = Math.toRadians(30); // When close
    public static final double maxTurnRateFar  = Math.toRadians(55); // When far

    // Boost at start to show arc immediately
    public static final double lateralStartBoost = 0.35; // Extra sideways speed

    // Speed limits and smoothing
    public static final double dampFactor = 0.95;
    public static final double maxOverTarget = 1.6;
    public static final double minUnderTarget = 0.45;

    // Items that can boomerang (for future weapon lists)
    public static final Set<Item> bounceTools = new HashSet<>();
    static {
        BuiltInRegistries.ITEM.stream().filter(i -> !BuiltInRegistries.ITEM.getKey(i)
                        .equals(ResourceLocation.withDefaultNamespace("air")))
                .forEach(bounceTools::add);
    }
    public static boolean canBounce(Item i) {
        return bounceTools.contains(i);
    }

    // Start weapon returning to thrower
    public static void startBounce(ThrownProjectileEntity proj) {
        proj.hasBounced = true;
        proj.bounceActive = true;

        // Player can be hit by their own weapon now
        proj.clearSpawnImmunity();

        Vec3 currentPos = proj.position();
        Vec3 toOrigin = proj.originalThrowPos.subtract(currentPos);
        double distanceToOrigin = toOrigin.length();

        if (distanceToOrigin < 0.1) {
            VRThrowingExtensions.log.debug("[Boomerang] Too close to origin, dropping normally");
            proj.bounceActive = false;
            return;
        }

        Vec3 dir = toOrigin.normalize();

        // Hand roll affects curve direction
        double rollRad = Math.toRadians(proj.getHandRoll());

        // Create curve based on hand rotation
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 upRolled = rotateAroundAxis(worldUp, dir, rollRad);
        // Make upRolled perpendicular to direction
        Vec3 upProj = projectPerp(upRolled, dir);      // Vertical curve (roll = 0)
        Vec3 right  = dir.cross(upRolled).normalize(); // Horizontal curve (roll = 90°)

        // Mix vertical and horizontal based on roll
        double cosR = Math.cos(rollRad);
        double sinR = Math.sin(rollRad);
        Vec3 curveDir = upProj.scale(cosR).add(right.scale(sinR)).normalize();

        // Flip arc direction based on roll
        proj.bounceInverse = proj.getHandRoll() < 0.0f;

        // Calculate curve plane
        Vec3 planeNormal = dir.cross(curveDir).normalize();
        if (proj.bounceInverse)            // mirror the curve
            planeNormal = planeNormal.scale(-1);

        double arcMag = Mth.clamp(distanceToOrigin * arcGain, arcMin, arcMax);

        proj.bouncePlaneNormal = planeNormal;
        proj.bounceArcMag      = arcMag;

        // Calculate return speed
        double speedMultiplier = calculateSpeedMultiplier(distanceToOrigin);
        double bounceSpeed = baseReturnSpeed * speedMultiplier;

        // Add curve to make return path visible
        Vec3 baseVel = dir.scale(bounceSpeed);
        Vec3 lateralVel = curveDir.scale(bounceSpeed * lateralStartBoost);
        Vec3 finalVel = baseVel.add(lateralVel).add(0, 0.02, 0); // slight upward bias

        proj.setDeltaMovement(finalVel);
        proj.setNoGravity(true);

        if (!proj.level().isClientSide()) {
            proj.level().playSound(null, proj.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
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

    // Update weapon return (true when done)
    public static boolean tickReturn(ThrownProjectileEntity proj) {
        Vec3 currentPos = proj.position();
        Vec3 toOrigin = proj.originalThrowPos.subtract(currentPos);
        double distSq = toOrigin.lengthSqr();

        // Check if weapon reached player
        boolean reachedOrigin = false;
        if (distSq < 0.36) {
            reachedOrigin = true;
            VRThrowingExtensions.log.debug("[Boomerang] Projectile {} reached origin (dist={})",
                    proj.getId(), String.format("%.3f", Math.sqrt(distSq)));
        } else {
            Vec3 currentVel = proj.getDeltaMovement();
            if (currentVel.length() > 0.01) {
                double dot = currentVel.normalize().dot(toOrigin.normalize());
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

        // Steer toward offset target
        Vec3 target = proj.originalThrowPos.add(proj.bounceCurveOffset);

        // Calculate direction to target
        Vec3 toTarget = target.subtract(currentPos);
        double distance = toTarget.length();
        if (distance < 0.0001) {
            return false;
        }
        Vec3 wantDir = toTarget.normalize();

        Vec3 currentVel = proj.getDeltaMovement();

        // Set target speed based on distance
        double speedMultiplier = calculateSpeedMultiplier(Math.sqrt(distSq));
        double targetSpeed = baseReturnSpeed * speedMultiplier;

        // Turn toward target with limited turn rate
        double turnRate = Mth.lerp(
                Mth.clamp((float)(distance / farDistance), 0.0f, 1.0f),
                maxTurnRateNear,
                maxTurnRateFar
        );
        Vec3 turned = turnTowards(currentVel, wantDir, turnRate);

        // Smooth and limit speed
        Vec3 newVel = turned.scale(dampFactor);
        double newSpeed = newVel.length();
        double maxSpeed = targetSpeed * maxOverTarget;
        double minSpeed = targetSpeed * minUnderTarget;

        if (newSpeed > maxSpeed) {
            newVel = newVel.normalize().scale(maxSpeed);
        } else if (newSpeed < minSpeed && newSpeed > 0.0001) {
            newVel = newVel.normalize().scale(minSpeed);
        }

        proj.setDeltaMovement(newVel);

        // Shrink arc faster when close to player
        double nearFactor = Mth.clamp(1.0 - (distance / farDistance), 0.0, 0.8);
        double decay = Mth.lerp(nearFactor, arcDecayPerStep, arcDecayPerStep * 0.75);
        proj.bounceCurveOffset = proj.bounceCurveOffset.scale(decay);

        if (proj.tickCount % 20 == 0) {
            VRThrowingExtensions.log.debug("[Boomerang] Return: dist={}, speed={}, target={}, arcLen={}",
                    String.format("%.2f", Math.sqrt(distSq)),
                    String.format("%.3f", newVel.length()),
                    String.format("%.3f", targetSpeed),
                    String.format("%.2f", proj.bounceCurveOffset.lengthSqr() > 0 ? Math.sqrt(proj.bounceCurveOffset.lengthSqr()) : 0));
        }
        return false;
    }

    // Calculate speed based on distance
    private static double calculateSpeedMultiplier(double distance) {
        if (distance <= closeDistance) return closeSpeedMultiplier;
        if (distance >= farDistance)   return farSpeedMultiplier;

        double t = (distance - closeDistance) / (farDistance - closeDistance);
        t = Math.pow(t, scalingCurve);
        return Mth.lerp((float)t, (float)closeSpeedMultiplier, (float)farSpeedMultiplier);
    }

    // Rotate vector around an axis
    private static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double angle) {
        Vec3 k = axis.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = v.dot(k);

        Vec3 term1 = v.scale(cos);
        Vec3 term2 = k.cross(v).scale(sin);
        Vec3 term3 = k.scale(dot * (1.0 - cos));

        return term1.add(term2).add(term3);
    }

    // Project vector onto perpendicular plane
    private static Vec3 projectPerp(Vec3 v, Vec3 normal) {
        double d = v.dot(normal);
        return v.subtract(normal.scale(d)).normalize();
    }

    // Turn velocity toward direction (max angle limit)
    private static Vec3 turnTowards(Vec3 currentVel, Vec3 wantDir, double maxAngle) {
        double speed = currentVel.length();
        if (speed < 1e-6) {
            return wantDir.scale(speed);
        }
        Vec3 curDir = currentVel.normalize();
        double dot = Mth.clamp(curDir.dot(wantDir), -1.0, 1.0);
        double angle = Math.acos(dot);

        if (angle <= maxAngle) {
            return wantDir.scale(speed);
        }
        // Rotate toward target direction
        Vec3 axis = curDir.cross(wantDir);
        if (axis.lengthSqr() < 1e-9) {
            // Directions are parallel, just blend them
            Vec3 turnDir = curDir.scale(1.0 - 1e-3).add(wantDir.scale(1e-3)).normalize();
            return turnDir.scale(speed);
        }
        Vec3 rotated = rotateAroundAxis(curDir, axis, maxAngle);
        return rotated.normalize().scale(speed);
    }

    private BoomerangEffect() { }
}