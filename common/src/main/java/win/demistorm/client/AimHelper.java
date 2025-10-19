package win.demistorm.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import win.demistorm.ConfigHelper;

import java.util.List;
import java.util.Optional;
import static win.demistorm.VRThrowingExtensions.log;

// Helps players aim at targets
public final class AimHelper {

    // Tunables
    private static final double maxAssistDistance = 25.0;
    private static final double assistViewAngle = 35.0;
    private static final double assistStrength = 0.5;
    private static final double maxPredictionTime = 2.5;

    // Physics constants
    private static final double gravityCalc = 0.06; // Gravity per tick squared
    private static final double ticksPerSecond = 20.0;

    // Applies aim assist
    public static Vec3 applyAimAssist(LocalPlayer player, Vec3 origin, Vec3 originalVelocity) {
        // Debug logging to see what config its checking
        log.debug("[Aim Assist] Checking ACTIVE.aimAssist = {}, CLIENT.aimAssist = {}",
            ConfigHelper.ACTIVE.aimAssist, ConfigHelper.CLIENT.aimAssist);

        if (!ConfigHelper.ACTIVE.aimAssist) {
            log.debug("[Aim Assist] Aim assist is DISABLED by server config - returning original velocity");
            return originalVelocity;
        }

        Optional<TargetInfo> bestTarget = findBestTarget(player, origin, originalVelocity);

        if (bestTarget.isEmpty()) {
            log.debug("[Aim Assist] No suitable target found");
            return originalVelocity;
        }

        TargetInfo target = bestTarget.get();
        Vec3 assistedVelocity = calculateBallisticAssist(origin, originalVelocity, target);

        double adjustment = assistedVelocity.subtract(originalVelocity).length();
        log.debug("[Aim Assist] Target: {}, distance: {}, time: {}s, adjustment: {}",
                target.entity.getName().getString(), target.distance, target.interceptTime, adjustment);

        return assistedVelocity;
    }

    // Finds the best entity to aim at
    private static Optional<TargetInfo> findBestTarget(LocalPlayer player, Vec3 origin, Vec3 velocity) {
        Vec3 throwDirection = velocity.normalize();
        double throwSpeed = velocity.length();

        Vec3 min = origin.subtract(maxAssistDistance, maxAssistDistance, maxAssistDistance);
        Vec3 max = origin.add(maxAssistDistance, maxAssistDistance, maxAssistDistance);
        AABB searchBox = new AABB(min, max);

        List<LivingEntity> candidates = player.level()
                .getEntitiesOfClass(LivingEntity.class, searchBox, entity ->
                        entity != player && entity.isAlive() && !entity.isSpectator());

        TargetInfo bestTarget = null;
        double bestScore = 0.0;

        for (LivingEntity entity : candidates) {
            TargetInfo targetInfo = evaluateTarget(entity, origin, throwDirection, throwSpeed);

            if (targetInfo != null) {
                double score = calculateTargetScore(targetInfo);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = targetInfo;
                }
            }
        }

        return Optional.ofNullable(bestTarget);
    }

    // Checks if a target can be hit and predicts where it will be
    private static TargetInfo evaluateTarget(LivingEntity entity, Vec3 origin, Vec3 throwDirection, double throwSpeed) {
        Vec3 targetPos = entity.position().add(0, entity.getEyeHeight() / 1.5, 0); // Where to aim on entity
        // Currently set to upper-mid body
        Vec3 toTarget = targetPos.subtract(origin);
        double distance = toTarget.length();

        // Quick range and view checks
        if (distance > maxAssistDistance) return null;

        double angle = Math.toDegrees(Math.acos(Mth.clamp(
                throwDirection.dot(toTarget.normalize()), -1.0, 1.0)));
        if (angle > assistViewAngle) return null;

        // Predict intercept time
        Vec3 entityVelocity = entity.getDeltaMovement();
        double interceptTime = calculateOptimalInterceptTime(origin, targetPos, entityVelocity, throwSpeed);

        if (interceptTime < 0 || interceptTime > maxPredictionTime) return null;

        // Predict position and confidence
        double tTicks = interceptTime * ticksPerSecond;
        Vec3 predictedPos = targetPos.add(entityVelocity.scale(tTicks));

        double trajectoryConfidence = calculateTrajectoryPossibility(origin, predictedPos, throwSpeed, interceptTime);

        if (trajectoryConfidence < 0.3) return null; // Skip impossible throws

        return new TargetInfo(entity, targetPos, predictedPos, distance, angle, trajectoryConfidence, interceptTime);
    }

    // Finds when to intercept based on target speed and throw speed
    private static double calculateOptimalInterceptTime(Vec3 origin, Vec3 targetPos, Vec3 targetVel, double throwSpeed) {
        final double minTicks = 1.0;
        final double maxTicks = maxPredictionTime * ticksPerSecond;

        if (throwSpeed <= 1.0e-6) return -1.0;

        // Compare required speed vs player throw speed
        java.util.function.DoubleUnaryOperator speedErrorAtTicks = (double tTicks) -> {
            if (tTicks <= 1e-6) return Double.POSITIVE_INFINITY;
            Vec3 predicted = targetPos.add(targetVel.scale(tTicks));
            double tSec = tTicks / ticksPerSecond;
            Vec3 requiredVel = calculateRequiredBallisticVelocity(origin, predicted, tSec);
            return requiredVel.length() - throwSpeed;
        };

        // Initial guess using linear distance (no gravity yet)
        Vec3 r0 = targetPos.subtract(origin);
        double a = targetVel.lengthSqr() - throwSpeed * throwSpeed;
        double b = 2.0 * r0.dot(targetVel);
        double c = r0.lengthSqr();

        double tGuess = -1.0;
        double disc = b * b - 4.0 * a * c;
        if (Math.abs(a) < 1e-8) {
            if (Math.abs(b) > 1e-8) {
                double t = -c / b;
                if (t > 0) tGuess = t;
            }
        } else if (disc >= 0) {
            double sqrt = Math.sqrt(disc);
            double t1 = (-b - sqrt) / (2.0 * a);
            double t2 = (-b + sqrt) / (2.0 * a);
            double best = Double.POSITIVE_INFINITY;
            if (t1 > 0) best = Math.min(best, t1);
            if (t2 > 0) best = Math.min(best, t2);
            if (Double.isFinite(best)) tGuess = best;
        }
        // Fallback guess by linear distance
        if (!(tGuess > 0)) {
            double linearDistance = r0.length();
            tGuess = Math.max(minTicks, Math.min(maxTicks, linearDistance / Math.max(1e-6, throwSpeed)));
        } else {
            tGuess = Mth.clamp(tGuess, minTicks, maxTicks);
        }

        // Look for a good time range and refine it
        final int steps = 12;
        double prevT = minTicks;
        double prevErr = speedErrorAtTicks.applyAsDouble(prevT);
        double brLo = Double.NaN, brHi = Double.NaN, errLo = 0;

        for (int i = 1; i <= steps; i++) {
            double alpha = (double) i / steps;
            double t = Mth.lerp(alpha, minTicks, maxTicks);
            t = Mth.lerp(0.25f, t, Mth.clamp((float) tGuess, (float) minTicks, (float) maxTicks));
            double err = speedErrorAtTicks.applyAsDouble(t);

            if (prevErr == 0.0 || err == 0.0 || (prevErr < 0 && err > 0) || (prevErr > 0 && err < 0)) {
                brLo = Math.min(prevT, t);
                brHi = Math.max(prevT, t);
                errLo = (brLo == prevT) ? prevErr : err;
                break;
            }
            prevT = t;
            prevErr = err;
        }

        double solvedTicks;
        final double absTol = Math.max(0.01, 0.03 * throwSpeed);

        if (Double.isFinite(brLo) && Double.isFinite(brHi)) {
            double lo = brLo, hi = brHi;
            double fLo = errLo;

            for (int iter = 0; iter < 18; iter++) {
                double mid = 0.5 * (lo + hi);
                double fMid = speedErrorAtTicks.applyAsDouble(mid);

                if (Math.abs(fMid) <= absTol) {
                    solvedTicks = mid;
                    double errPct = Math.abs(fMid) / Math.max(1e-6, throwSpeed);
                    log.debug("[Aim Assist] Intercept refined: T={} ticks (~{}s), err={} ({}%)",
                            String.format("%.2f", solvedTicks),
                            String.format("%.2f", solvedTicks / ticksPerSecond),
                            String.format("%.4f", fMid),
                            errPct * 100.0);
                    return solvedTicks / ticksPerSecond;
                }

                if ((fLo < 0 && fMid > 0) || (fLo > 0 && fMid < 0)) {
                    hi = mid;
                } else {
                    lo = mid;
                    fLo = fMid;
                }
            }
            solvedTicks = 0.5 * (lo + hi);
            return solvedTicks / ticksPerSecond;
        }

        // If no perfect fit, test nearby times
        double[] testTimes = new double[] {
                Mth.clamp(tGuess * 0.5, minTicks, maxTicks),
                Mth.clamp(tGuess * 0.75, minTicks, maxTicks),
                Mth.clamp(tGuess, minTicks, maxTicks),
                Mth.clamp(tGuess * 1.25, minTicks, maxTicks),
                Mth.clamp(tGuess * 1.5, minTicks, maxTicks),
        };

        double bestT = -1;
        double bestAbsErr = Double.MAX_VALUE;
        for (double t : testTimes) {
            double e = speedErrorAtTicks.applyAsDouble(t);
            double ae = Math.abs(e);
            if (ae < bestAbsErr) {
                bestAbsErr = ae;
                bestT = t;
            }
        }

        // Requires assist to be close enough to player's throw speed
        double relErr = bestAbsErr / Math.max(1e-6, throwSpeed);
        if (relErr <= 0.15) {
            log.debug("[Aim Assist] Using close fallback time: T={} ticks (~{}s), relErr={}%",
                    String.format("%.2f", bestT),
                    String.format("%.2f", bestT / ticksPerSecond),
                    relErr * 100.0);
            return bestT / ticksPerSecond;
        }

        log.debug("[Aim Assist] No valid intercept (bestRelErr={}%)", String.format("%.1f", relErr * 100.0));
        return -1.0;
    }

    // Finds required initial vel to hit a target considering gravity
    private static Vec3 calculateRequiredBallisticVelocity(Vec3 origin, Vec3 target, double flightTimeSeconds) {
        Vec3 displacement = target.subtract(origin);
        double flightTimeTicks = flightTimeSeconds * ticksPerSecond;

        double vx = displacement.x / flightTimeTicks;
        double vz = displacement.z / flightTimeTicks;
        double vy = (displacement.y + 0.5 * gravityCalc * flightTimeTicks * flightTimeTicks) / flightTimeTicks;

        return new Vec3(vx, vy, vz);
    }

    // Checks if the throw path is doable
    private static double calculateTrajectoryPossibility(Vec3 origin, Vec3 target, double throwSpeed, double timeSeconds) {
        Vec3 requiredVel = calculateRequiredBallisticVelocity(origin, target, timeSeconds);
        double requiredSpeed = requiredVel.length();
        return Math.min(throwSpeed, requiredSpeed) / Math.max(throwSpeed, requiredSpeed);
    }

    // Calculates adjusted velocity toward target
    private static Vec3 calculateBallisticAssist(Vec3 origin, Vec3 originalVelocity, TargetInfo target) {
        Vec3 idealVelocity = calculateRequiredBallisticVelocity(origin, target.predictedPos, target.interceptTime);

        // Merge the ideal throw with the player's intended throw
        double originalSpeed = originalVelocity.length();
        double idealSpeed = idealVelocity.length();
        if (idealSpeed > 1e-6) {
            idealVelocity = idealVelocity.scale(originalSpeed / idealSpeed);
        }

        if (idealVelocity.length() > originalSpeed * 1.6) {
            idealVelocity = idealVelocity.normalize().scale(originalSpeed * 1.6);
        }

        double effectiveStrength = assistStrength * target.confidence;
        return blendVelocities(originalVelocity, idealVelocity, effectiveStrength);
    }

    // Blends two velocities smoothly
    private static Vec3 blendVelocities(Vec3 current, Vec3 target, double strength) {
        return current.scale(1.0 - strength).add(target.scale(strength));
    }

    // Rates how good a target is
    private static double calculateTargetScore(TargetInfo target) {
        double baseScore = target.confidence;
        double distanceScore = 1.0 - (target.distance / maxAssistDistance);
        double angleScore = 1.0 - (target.angle / assistViewAngle);

        // Type weights
        double typeMultiplier = 0.7; // Default (animals)
        if (target.entity instanceof Monster) typeMultiplier = 1.0; // Prefer hostile mobs
        if (target.entity instanceof Player) typeMultiplier = 0.6; // Only aim at players when nothing else is present

        return (baseScore + distanceScore + angleScore) / 3.0 * typeMultiplier;
    }

    // Target info
    private record TargetInfo(LivingEntity entity, Vec3 currentPos, Vec3 predictedPos, double distance, double angle,
                              double confidence, double interceptTime) {
    }

    private AimHelper() {} // Utility class
}
