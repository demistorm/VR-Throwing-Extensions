package win.demistorm;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import win.demistorm.effects.BoomerangEffect;
import win.demistorm.effects.EmbeddingEffect;
import win.demistorm.network.NetworkHelper;

import static win.demistorm.VRThrowingExtensions.log;

// Projectile that carries the player's held item, deals damage, and drops the item after collision
public class ThrownProjectileEntity extends net.minecraft.entity.projectile.thrown.ThrownItemEntity {
    private int stackSize = 1;
    public boolean catching = false;                // Whether this projectile is being caught
    private Vec3d storedVelocity = Vec3d.ZERO;      // Stores velocity before catching

    // Boomerang state tracking
    private int bounceReturnTicks = 0;              // Time spent in return flight
    private boolean reachedOriginOnce = false;      // Prevents oscillation at origin
    public Vec3d originalThrowPos = Vec3d.ZERO;  // Saved when the entity is created on the server
    public boolean hasBounced = false;           // Whether the first hit happened
    public boolean bounceActive = false;         // If the boomerang is active
    public Vec3d bounceCurveOffset = Vec3d.ZERO;
    public Vec3d bouncePlaneNormal = Vec3d.ZERO;
    public double bounceArcMag = 0.0;
    public boolean bounceInverse = true;           // Inverts the bounce direction

    // Embedding state tracking
    private LivingEntity embeddedTarget = null;     // Host entity we're embedded in
    private Vec3d embeddedOffset = Vec3d.ZERO;      // LOCAL offset (relative to host body yaw)
    private boolean alreadyDropped = false;         // Prevent duplicate drops via removal

    private float embeddedLocalYaw = 0f;            // Yaw relative to host yaw
    private float embeddedLocalPitch = 0f;          // Pitch relative to host pitch

    public ThrownProjectileEntity(EntityType<? extends ThrownProjectileEntity> type, World world) {
        super(type, world);
    }

    private static final TrackedData<Float> HAND_ROLL =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> IS_CATCHING =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);

    // Tracked data for client sync
    private static final TrackedData<Boolean> BOUNCE_ACTIVE =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);

    // Embedding tracked data (so clients can render properly)
    private static final TrackedData<Boolean> IS_EMBEDDED =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> EMBED_YAW =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> EMBED_PITCH =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> EMBED_ROLL =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> EMBED_TILT =
            DataTracker.registerData(ThrownProjectileEntity.class,
                    TrackedDataHandlerRegistry.FLOAT);

    // Handles the rotation of the arm and bounce state
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(HAND_ROLL, 0f);
        builder.add(IS_CATCHING, false);
        builder.add(BOUNCE_ACTIVE, false);
        builder.add(IS_EMBEDDED, false);
        builder.add(EMBED_YAW, 0f);
        builder.add(EMBED_PITCH, 0f);
        builder.add(EMBED_ROLL, 0f);
        builder.add(EMBED_TILT, 0f);
    }

    public void setHandRoll(float deg) {
        this.dataTracker.set(HAND_ROLL, deg);
    }
    public float getHandRoll() {
        return this.dataTracker.get(HAND_ROLL);
    }

    public void startCatch() {
        // Release embedding state when catching is called
        EmbeddingEffect.releaseEmbedding(this);

        this.catching = true;
        this.storedVelocity = getVelocity();
        this.dataTracker.set(IS_CATCHING, true);

        // Disable gravity while being caught
        this.setNoGravity(true);
        log.debug("[VR Catch] Started catch for projectile {}", this.getId());
    }

    public void cancelCatch() {
        this.catching = false;
        this.dataTracker.set(IS_CATCHING, false);

        // Restore gravity
        this.setNoGravity(false);

        // Restore some velocity to continue flying
        if (storedVelocity.length() > 0.1) {
            setVelocity(storedVelocity.multiply(0.5));
        }

        // DEBUG
        log.debug("[VR Catch] Canceled catch for projectile {}", this.getId());
    }

    // Data trackers
    public boolean isCatching() {
        return this.dataTracker.get(IS_CATCHING);
    }
    public boolean isBounceActive() {
        return this.dataTracker.get(BOUNCE_ACTIVE);
    }
    public int getStackSize() {
        return this.stackSize;
    }

    public ThrownProjectileEntity(World world, LivingEntity owner, ItemStack carried, boolean isWholeStack) {
        super(VRThrowingExtensions.THROWN_ITEM_TYPE, world);
        setOwner(owner);
        setItem(carried.copyWithCount(1)); // Visually only throws 1 model

        // Sets stackSize based on whether it is throwing the whole stack or not
        this.stackSize = isWholeStack ? carried.getCount() : 1;
    }

    @Override
    public void tick() {
        super.tick();

        // Checks if projectile is embedded
        if (isEmbedded() && !isCatching()) {
            EmbeddingEffect.tickEmbedded(this);
            return; //
        }

        // Boomerang return handling
        if (bounceActive && !isCatching()) {
            bounceReturnTicks++;

            // Return logic end
            if (BoomerangEffect.tickReturn(this)) {
                // Reached origin so it converts over to a regular projectile again
                bounceActive = false;
                reachedOriginOnce = true;
                this.dataTracker.set(BOUNCE_ACTIVE, false);

                // Restore gravity but preserve the current velocity
                setNoGravity(false);

                // Instead of dropping straight down, preserve the return velocity
                Vec3d returnVel = getVelocity();

                double currentSpeed = returnVel.length();
                if (currentSpeed > 1.0) {
                    // Caps the speed to prevent items flying too far
                    returnVel = returnVel.normalize().multiply(Math.min(currentSpeed, 1.0));
                }

                // Apply the preserved velocity so that it naturally falls
                setVelocity(returnVel);

                log.debug("[VR Throw] Projectile {} completed boomerang return after {} ticks, continuing with velocity {}",
                        this.getId(), bounceReturnTicks, returnVel);
            }

            // Stops the bounce if something goes wrong
            if (bounceReturnTicks > 200) { // ~10 seconds at 20 TPS
                log.debug("[VR Throw] Projectile {} return timed out, dropping", this.getId());
                stopBoomerang();
            }
        }

        // Don't apply normal physics if being caught
        if (isCatching()) {
            // Apply slight air resistance to smooth magnetism effect
            Vec3d vel = getVelocity();
            setVelocity(vel.multiply(0.95));
        }
    }

    // End boomerang effect and restore normal physics
    private void stopBoomerang() {
        bounceActive = false;
        this.dataTracker.set(BOUNCE_ACTIVE, false);
        setNoGravity(false);

        // Apply gentle downward velocity
        Vec3d currentVel = getVelocity();
        double horizontalSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);

        // Cap horizontal speed and add downward motion
        if (horizontalSpeed > 0.5) {
            double factor = 0.3 / horizontalSpeed;
            setVelocity(new Vec3d(currentVel.x * factor, -0.2, currentVel.z * factor));
        } else {
            setVelocity(new Vec3d(currentVel.x * 0.5, -0.2, currentVel.z * 0.5));
        }
    }

    // Collision mechanics with damage logic
    @Override
    protected void onCollision(HitResult hit) {
        // Ignore while being caught
        if (isCatching()) return;

        // If the thrown item is mid-boomerang, have it drop on hit
        if (bounceActive || hasBounced) {
            if (hit.getType() == HitResult.Type.ENTITY) {
                onEntityHit((EntityHitResult) hit);
                log.debug("[VR Throw] Projectile {} hit entity during return flight, dropping", this.getId());
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                log.debug("[VR Throw] Projectile {} hit block during return flight, dropping", this.getId());
            }

            // Always drop after collision during return flight
            dropAndDiscard();
            return;
        }

        // Thrown item first hit
        if (!getWorld().isClient) {
            boolean hitEntity = hit.getType() == HitResult.Type.ENTITY;

            if (hitEntity) {
                // Deal damage first
                onEntityHit((EntityHitResult) hit);

                // Only boomerang/embed if the item is a weapon/tool/does damage (should be mod compatible?)
                float attackDamage = stackBaseDamage(getStack());
                if (attackDamage <= 1.0F) {
                    dropAndDiscard();
                    return;
                }

                // Apply effect based on weeapon effect config
                if (ConfigHelper.ACTIVE.weaponEffect == WeaponEffectType.BOOMERANG) {
                    boolean shouldBounce = BoomerangEffect.canBounce(getStack().getItem())
                            && !hasBounced
                            && !reachedOriginOnce; // Don't bounce if already completed return

                    if (shouldBounce) {
                        log.debug("[VR Throw] Starting boomerang effect for projectile {}", this.getId());
                        BoomerangEffect.startBounce(this);

                        // Update tracked data for client sync
                        bounceActive = true;
                        this.dataTracker.set(BOUNCE_ACTIVE, true);
                        return; // Starts return flight
                    }
                } else if (ConfigHelper.ACTIVE.weaponEffect == WeaponEffectType.EMBED) {
                    // Starts embedding effect
                    EmbeddingEffect.startEmbedding(this, (EntityHitResult) hit);
                    return; // Embedded items are handled by tickEmbedded
                }
            }

            // Normal drop for non-bouncing hits or block hits
            dropAndDiscard();
        } else {
            // Nice particle effects
            getWorld().addParticleClient(
                    new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                    getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    // Damage mechanics
    @Override
    protected void onEntityHit(EntityHitResult res) {
        Entity target = res.getEntity();
        ServerWorld world = (ServerWorld) getWorld();
        DamageSources sources = world.getDamageSources();
        DamageSource src = sources.thrown(this, getOwner() == null ? this : getOwner());

        // Grabs the base damage from the itemStack and applies enchantment bonuses on top
        float base = stackBaseDamage(getStack());
        float totalDamage = EnchantmentHelper.getDamage(world, getStack(), target, src, base);
        float enchantmentBonus = totalDamage - base; // For DEBUG

        // DEBUG
        log.debug("[VR Throw] Damage dealt: Item={}, Base={}, EnchantBonus={}, Final={}, Target={}, BounceState={}",
                getStack().getItem().toString(), base, enchantmentBonus,
                totalDamage, target.getName().getString(),
                bounceActive ? "RETURNING" : "FORWARD");
        if (VRThrowingExtensions.debugMode) {
            if (getOwner() instanceof ServerPlayerEntity player) {
                Text msg = Text.literal(String.format(
                        "[VR Throw] Damage dealt: Item=%s, Base=%.2f, EnchantBonus=%.2f, Final=%.2f, Target=%s, BounceState=%s",
                        getStack().getItem(),
                        base,
                        enchantmentBonus,
                        totalDamage,
                        target.getName().getString(),
                        bounceActive ? "RETURNING" : "FORWARD"
                ));
                player.sendMessage(msg, false);
            }
        }

        // Actually damages the entity
        target.damage(world, src, totalDamage);

        // DRAFT Blood Effect
        // NEW: Send blood particle packet if damage was dealt
        if (totalDamage > 0 && !world.isClient()) {
            Vec3d hitPos = res.getPos();
            Vec3d hitVel = getVelocity();

            for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                if (player.getWorld() == world && player.squaredDistanceTo(hitPos) < 4096) { // 64 blocks
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            player, new NetworkHelper.BloodParticlePacket(hitPos, hitVel));
                }
            }
        }

        // Adds a little knockback
        Vec3d push = getVelocity().normalize().multiply(0.5);
        target.addVelocity(push.x, 0.1 + push.y, push.z);
    }

    // Creates the dropped stack, also does -1 durability to tools
    private ItemStack createDropStack() {
        ItemStack drop = getStack().copy();
        if (drop.isDamageable()) {
            int totalDamage = MathHelper.clamp(
                    drop.getDamage() + stackSize, // Durability penalty
                    0, drop.getMaxDamage());
            drop.setDamage(totalDamage);
        }
        return drop;
    }

    // Checks the attack damage of a given item
    public static float stackBaseDamage(ItemStack stack) {
        final float[] totalBonus = {0f};

        stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attrEntry, modifier) -> {
            if (attrEntry == EntityAttributes.ATTACK_DAMAGE) {
                totalBonus[0] += (float) modifier.value();
            }
        });

        return 1.0F + totalBonus[0]; // 1.0 base punch + item’s modifier
    }

    // Placeholder item for ThrownItemEntity's sake
    @Override
    protected Item getDefaultItem() {
        return net.minecraft.item.Items.STICK;
    }

    public void setOriginalThrowPos(Vec3d v) {
        this.originalThrowPos = v;
    }

    // Called by EmbeddingEffect to initialize embedded state
    public void beginEmbedding(LivingEntity host, Vec3d worldOffset,
                               float yawDeg, float pitchDeg, float tiltDeg, float initialXRollDeg) {

        // Freeze physics & move to the embed point precisely
        this.embeddedTarget = host;
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);
        this.setPosition(host.getPos().add(worldOffset));

        // Convert world-space offset to host-local space (rotate by -host BODY yaw)
        float hostBodyYaw = host.getBodyYaw();
        float hostPitch = host.getPitch();
        Vec3d localOffset = rotateY(worldOffset, -hostBodyYaw);

        // Save local offset/orientation (these are used each tick to follow the host)
        this.embeddedOffset = localOffset; // now LOCAL
        this.embeddedLocalYaw = MathHelper.wrapDegrees(yawDeg - hostBodyYaw);
        this.embeddedLocalPitch = MathHelper.wrapDegrees(pitchDeg - hostPitch);

        // Network embed state for clients (stores current world orientation)
        this.dataTracker.set(IS_EMBEDDED, true);
        this.dataTracker.set(EMBED_YAW, yawDeg);
        this.dataTracker.set(EMBED_PITCH, pitchDeg);
        this.dataTracker.set(EMBED_ROLL, initialXRollDeg);
        this.dataTracker.set(EMBED_TILT, tiltDeg);

        // DEBUG
        VRThrowingExtensions.log.debug("[Embed] beginEmbedding: proj={} host={} worldOffset={} localOffset={} yaw={} pitch={} tilt={} xRollStart={}",
                getId(), host.getName().getString(), worldOffset, localOffset,
                String.format("%.1f", yawDeg), String.format("%.1f", pitchDeg),
                String.format("%.1f", tiltDeg), String.format("%.1f", initialXRollDeg));
    }


    // Clears embedding state (used when host dies or catching starts)
    public void clearEmbedding() {
        // Unregister bleed on the server before clearing the target
        if (!this.getWorld().isClient() && this.embeddedTarget != null) {
            EmbeddingEffect.BleedManager.unregister(this.embeddedTarget, this); // CHANGED
        }

        this.dataTracker.set(IS_EMBEDDED, false);
        this.embeddedTarget = null;
        this.embeddedOffset = Vec3d.ZERO;
        this.embeddedLocalYaw = 0f;
        this.embeddedLocalPitch = 0f;
        this.setNoGravity(false);
    }

    // Embedding accessors for renderer/tick
    public boolean isEmbedded() { return this.dataTracker.get(IS_EMBEDDED); }
    public float getEmbedYaw()  { return this.dataTracker.get(EMBED_YAW); }
    public float getEmbedPitch(){ return this.dataTracker.get(EMBED_PITCH); }
    public float getEmbedRoll() { return this.dataTracker.get(EMBED_ROLL); }
    public void setEmbedRoll(float v) { this.dataTracker.set(EMBED_ROLL, v); }
    public float getEmbedTilt() { return this.dataTracker.get(EMBED_TILT); } // NEW
    public Entity getEmbeddedTarget() { return this.embeddedTarget; }

    public Vec3d getEmbeddedOffset()      { return this.embeddedOffset; }
    public float getEmbeddedLocalYaw()    { return this.embeddedLocalYaw; }
    public float getEmbeddedLocalPitch()  { return this.embeddedLocalPitch; }
    public void setEmbedYaw(float v)      { this.dataTracker.set(EMBED_YAW, v); }
    public void setEmbedPitch(float v)    { this.dataTracker.set(EMBED_PITCH, v); }

    // Rotate a vector around the Y-axis by degrees
    private static Vec3d rotateY(Vec3d v, float degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3d(x, v.y, z);
    }

    // Drops the item (if not already dropped) and discards this projectile
    public void dropAndDiscard() {
        // NEW: Ensure bleed is unregistered if we were embedded
        if (isEmbedded()) {
            clearEmbedding();
        }

        if (alreadyDropped) {
            // Prevent double-drop
            discard();
            return;
        }
        alreadyDropped = true;
        if (!getWorld().isClient()) {
            ItemStack dropStack = createDropStack();
            dropStack.setCount(stackSize);
            getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(
                    getWorld(), getX(), getY(), getZ(), dropStack));
        }
        discard();
    }
}
