package win.demistorm.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import win.demistorm.ConfigHelper;

import java.util.List;
import java.util.Optional;
import static win.demistorm.VRThrowingExtensions.log;

// Aim assist joys
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
    public static Vec3d applyAimAssist(ClientPlayerEntity player, Vec3d origin, Vec3d originalVelocity) {
        if (!ConfigHelper.CLIENT.aimAssist) {
            return originalVelocity;
        }

        Optional<TargetInfo> bestTarget = findBestTarget(player, origin, originalVelocity);

        if (bestTarget.isEmpty()) {
            log.debug("[Aim Assist] No suitable target found");
            return originalVelocity;
        }

        TargetInfo target = bestTarget.get();
        Vec3d assistedVelocity = calculateBallisticAssist(origin, originalVelocity, target);

        double adjustment = assistedVelocity.subtract(originalVelocity).length();
        log.debug("[Aim Assist] Target: {}, distance: {}, time: {}s, adjustment: {}",
                target.entity.getName().getString(), target.distance, target.interceptTime, adjustment);

        return assistedVelocity;
    }

    // Finds the best entity to aim at
    private static Optional<TargetInfo> findBestTarget(ClientPlayerEntity player, Vec3d origin, Vec3d velocity) {
        Vec3d throwDirection = velocity.normalize();
        double throwSpeed = velocity.length();

        Box searchBox = Box.of(origin, maxAssistDistance * 2, maxAssistDistance * 2, maxAssistDistance * 2);

        List<LivingEntity> candidates = player.getWorld()
                .getEntitiesByClass(LivingEntity.class, searchBox, entity ->
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
    private static TargetInfo evaluateTarget(LivingEntity entity, Vec3d origin, Vec3d throwDirection, double throwSpeed) {
        Vec3d targetPos = entity.getPos().add(0, entity.getHeight() / 1.5, 0); // Where to aim on entity
        // Currently set to upper-mid body ^
        Vec3d toTarget = targetPos.subtract(origin);
        double distance = toTarget.length();

        // Quick range and view checks
        if (distance > maxAssistDistance) return null;

        double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                throwDirection.dotProduct(toTarget.normalize()), -1.0, 1.0)));
        if (angle > assistViewAngle) return null;

        // Predict intercept time
        Vec3d entityVelocity = entity.getVelocity();
        double interceptTime = calculateOptimalInterceptTime(origin, targetPos, entityVelocity, throwSpeed);

        if (interceptTime < 0 || interceptTime > maxPredictionTime) return null;

        // Predict position and confidence
        double tTicks = interceptTime * ticksPerSecond;
        Vec3d predictedPos = targetPos.add(entityVelocity.multiply(tTicks));

        double trajectoryConfidence = calculateTrajectoryPossibility(origin, predictedPos, throwSpeed, interceptTime);

        if (trajectoryConfidence < 0.3) return null; // Skip impossible throws

        return new TargetInfo(entity, targetPos, predictedPos, distance, angle, trajectoryConfidence, interceptTime);
    }

    // Finds when to intercept based on target speed and throw speed
    private static double calculateOptimalInterceptTime(Vec3d origin, Vec3d targetPos, Vec3d targetVel, double throwSpeed) {
        final double minTicks = 1.0;
        final double maxTicks = maxPredictionTime * ticksPerSecond;

        if (throwSpeed <= 1.0e-6) return -1.0;

        // Compare required speed vs player throw speed
        java.util.function.DoubleUnaryOperator speedErrorAtTicks = (double tTicks) -> {
            if (tTicks <= 1e-6) return Double.POSITIVE_INFINITY;
            Vec3d predicted = targetPos.add(targetVel.multiply(tTicks));
            double tSec = tTicks / ticksPerSecond;
            Vec3d requiredVel = calculateRequiredBallisticVelocity(origin, predicted, tSec);
            return requiredVel.length() - throwSpeed;
        };

        // Initial guess using linear distance (no gravity yet)
        Vec3d r0 = targetPos.subtract(origin);
        double a = targetVel.lengthSquared() - throwSpeed * throwSpeed;
        double b = 2.0 * r0.dotProduct(targetVel);
        double c = r0.lengthSquared();

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
            tGuess = MathHelper.clamp(tGuess, minTicks, maxTicks);
        }

        // Look for a good time range and refine it
        final int steps = 12;
        double prevT = minTicks;
        double prevErr = speedErrorAtTicks.applyAsDouble(prevT);
        double brLo = Double.NaN, brHi = Double.NaN, errLo = 0;

        for (int i = 1; i <= steps; i++) {
            double alpha = (double) i / steps;
            double t = MathHelper.lerp(alpha, minTicks, maxTicks);
            t = MathHelper.lerp(0.25f, t, MathHelper.clamp((float) tGuess, (float) minTicks, (float) maxTicks));
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
                MathHelper.clamp(tGuess * 0.5, minTicks, maxTicks),
                MathHelper.clamp(tGuess * 0.75, minTicks, maxTicks),
                MathHelper.clamp(tGuess, minTicks, maxTicks),
                MathHelper.clamp(tGuess * 1.25, minTicks, maxTicks),
                MathHelper.clamp(tGuess * 1.5, minTicks, maxTicks),
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
    private static Vec3d calculateRequiredBallisticVelocity(Vec3d origin, Vec3d target, double flightTimeSeconds) {
        Vec3d displacement = target.subtract(origin);
        double flightTimeTicks = flightTimeSeconds * ticksPerSecond;

        double vx = displacement.x / flightTimeTicks;
        double vz = displacement.z / flightTimeTicks;
        double vy = (displacement.y + 0.5 * gravityCalc * flightTimeTicks * flightTimeTicks) / flightTimeTicks;

        return new Vec3d(vx, vy, vz);
    }

    // Checks if the throw path is doable
    private static double calculateTrajectoryPossibility(Vec3d origin, Vec3d target, double throwSpeed, double timeSeconds) {
        Vec3d requiredVel = calculateRequiredBallisticVelocity(origin, target, timeSeconds);
        double requiredSpeed = requiredVel.length();
        return Math.min(throwSpeed, requiredSpeed) / Math.max(throwSpeed, requiredSpeed);
    }

    // Calculates adjusted velocity toward target
    private static Vec3d calculateBallisticAssist(Vec3d origin, Vec3d originalVelocity, TargetInfo target) {
        Vec3d idealVelocity = calculateRequiredBallisticVelocity(origin, target.predictedPos, target.interceptTime);

        // Merge the ideal throw with the player's intended throw
        double originalSpeed = originalVelocity.length();
        double idealSpeed = idealVelocity.length();
        if (idealSpeed > 1e-6) {
            idealVelocity = idealVelocity.multiply(originalSpeed / idealSpeed);
        }

        if (idealVelocity.length() > originalSpeed * 1.6) {
            idealVelocity = idealVelocity.normalize().multiply(originalSpeed * 1.6);
        }

        double effectiveStrength = assistStrength * target.confidence;
        return blendVelocities(originalVelocity, idealVelocity, effectiveStrength);
    }

    // Blends two velocities smoothly
    private static Vec3d blendVelocities(Vec3d current, Vec3d target, double strength) {
        return current.multiply(1.0 - strength).add(target.multiply(strength));
    }

    // Rates how good a target is
    private static double calculateTargetScore(TargetInfo target) {
        double baseScore = target.confidence;
        double distanceScore = 1.0 - (target.distance / maxAssistDistance);
        double angleScore = 1.0 - (target.angle / assistViewAngle);

        // Type weights
        double typeMultiplier = 0.7; // Default (animals)
        if (target.entity instanceof Monster) typeMultiplier = 1.0; // Prefer hostile mobs
        if (target.entity instanceof PlayerEntity) typeMultiplier = 0.6; // Only aim at players when nothing else is present

        return (baseScore + distanceScore + angleScore) / 3.0 * typeMultiplier;
    }

    // Target info
    private record TargetInfo(LivingEntity entity, Vec3d currentPos, Vec3d predictedPos, double distance, double angle,
                              double confidence, double interceptTime) {
    }

    private AimHelper() {} // Utility class
}
