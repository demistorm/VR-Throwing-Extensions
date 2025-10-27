package win.demistorm;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import com.google.common.collect.Multimap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import win.demistorm.effects.BoomerangEffect;
import win.demistorm.effects.EmbeddingEffect;

import static win.demistorm.VRThrowingExtensions.log;

// Main projectile entity for thrown items
public class ThrownProjectileEntity extends ThrowableItemProjectile {
    private int stackSize = 1;
    public boolean catching = false;
    private Vec3 storedVelocity = Vec3.ZERO;
    private int immunityTicks = 20;

    private int bounceReturnTicks = 0;
    private boolean reachedOriginOnce = false;
    public Vec3 originalThrowPos = Vec3.ZERO;
    public boolean hasBounced = false;
    public boolean bounceActive = false;
    public Vec3 bounceCurveOffset = Vec3.ZERO;
    public Vec3 bouncePlaneNormal = Vec3.ZERO;
    public double bounceArcMag = 0.0;
    public boolean bounceInverse = true;

    private LivingEntity embeddedTarget = null;
    private Vec3 embeddedOffset = Vec3.ZERO;
    private boolean alreadyDropped = false;
    private float embeddedLocalYaw = 0f;
    private float embeddedLocalPitch = 0f;

    private boolean damageDealt = false;

    public ThrownProjectileEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    private static final EntityDataAccessor<Float> HAND_ROLL =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_CATCHING =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BOUNCE_ACTIVE =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_EMBEDDED =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> EMBED_YAW =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EMBED_PITCH =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EMBED_ROLL =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EMBED_TILT =
            SynchedEntityData.defineId(ThrownProjectileEntity.class, EntityDataSerializers.FLOAT);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HAND_ROLL, 0f);
        this.entityData.define(IS_CATCHING, false);
        this.entityData.define(BOUNCE_ACTIVE, false);
        this.entityData.define(IS_EMBEDDED, false);
        this.entityData.define(EMBED_YAW, 0f);
        this.entityData.define(EMBED_PITCH, 0f);
        this.entityData.define(EMBED_ROLL, 0f);
        this.entityData.define(EMBED_TILT, 0f);
    }

    // Constructor used by server when creating the projectile
    public ThrownProjectileEntity(Level level, LivingEntity owner, ItemStack carried, boolean isWholeStack) {
        super(VRThrowingExtensions.THROWN_ITEM_TYPE, level);
        this.setOwner(owner);
        this.setItem(carried.copyWithCount(1)); // vanilla sync field in ThrowableItemProjectile
        this.stackSize = isWholeStack ? carried.getCount() : 1;
    }

    // Client-side: help verify when the stack actually arrives
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide) {
            // Log first few updates to confirm arrival timing
            if (this.tickCount < 40) {
                VRThrowingExtensions.log.debug("[Client] Data updated for {}: item now {}", getId(), this.getItem());
            }
        }
    }

    public void setHandRoll(float deg) {
        this.entityData.set(HAND_ROLL, deg);
    }
    public float getHandRoll() {
        return this.entityData.get(HAND_ROLL);
    }

    public void startCatch() {
        EmbeddingEffect.releaseEmbedding(this);
        this.catching = true;
        this.storedVelocity = getDeltaMovement();
        this.entityData.set(IS_CATCHING, true);
        this.setNoGravity(true);
        log.debug("[VR Catch] Started catch for projectile {}", this.getId());
    }

    public void cancelCatch() {
        this.catching = false;
        this.entityData.set(IS_CATCHING, false);
        this.setNoGravity(false);
        if (storedVelocity.length() > 0.1) {
            setDeltaMovement(storedVelocity.scale(0.5));
        }
        log.debug("[VR Catch] Canceled catch for projectile {}", this.getId());
    }

    public boolean isCatching() { return this.entityData.get(IS_CATCHING); }
    public boolean isBounceActive() { return this.entityData.get(BOUNCE_ACTIVE); }
    public int getStackSize() { return this.stackSize; }

    @Override
    public void tick() {
        super.tick();

        if (immunityTicks > 0) immunityTicks--;

        if (isEmbedded() && !isCatching()) {
            EmbeddingEffect.tickEmbedded(this);
            return;
        }

        if (bounceActive && !isCatching()) {
            bounceReturnTicks++;
            if (BoomerangEffect.tickReturn(this)) {
                bounceActive = false;
                reachedOriginOnce = true;
                this.entityData.set(BOUNCE_ACTIVE, false);
                setNoGravity(false);
                Vec3 returnVel = getDeltaMovement();
                double currentSpeed = returnVel.length();
                if (currentSpeed > 1.0) {
                    returnVel = returnVel.normalize().scale(Math.min(currentSpeed, 1.0));
                }
                setDeltaMovement(returnVel);
                log.debug("[VR Throw] Projectile {} completed return after {} ticks, vel {}",
                        this.getId(), bounceReturnTicks, returnVel);
            }
            if (bounceReturnTicks > 200) {
                log.debug("[VR Throw] Projectile {} return timed out, dropping", this.getId());
                stopBoomerang();
            }
        }

        if (isCatching()) {
            Vec3 vel = getDeltaMovement();
            setDeltaMovement(vel.scale(0.95));
        }
    }

    private void stopBoomerang() {
        bounceActive = false;
        this.entityData.set(BOUNCE_ACTIVE, false);
        setNoGravity(false);
        Vec3 currentVel = getDeltaMovement();
        double horizontalSpeed = Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);
        if (horizontalSpeed > 0.5) {
            double factor = 0.3 / horizontalSpeed;
            setDeltaMovement(new Vec3(currentVel.x * factor, -0.2, currentVel.z * factor));
        } else {
            setDeltaMovement(new Vec3(currentVel.x * 0.5, -0.2, currentVel.z * 0.5));
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        if (isCatching()) return;

        if (immunityTicks > 0 && hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult ehr = (EntityHitResult) hit;
            Entity target = ehr.getEntity();
            if (target == getOwner()) {
                log.debug("[VR Throw] Projectile {} ignoring owner collision (immunity: {} ticks)", this.getId(), immunityTicks);
                return;
            }
        }

        // Calculate hit position if hit an entity (needed for both bounce return and normal hit)
        Vec3 hitPos = null;
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity target = entityHit.getEntity();

            // Calculate clamped hit position for both blood particles and embedding
            AABB targetBox = target.getBoundingBox();
            Vec3 projPos = this.position();
            double hitX = Mth.clamp(projPos.x, targetBox.minX, targetBox.maxX);
            double hitY = Mth.clamp(projPos.y, targetBox.minY, targetBox.maxY);
            double hitZ = Mth.clamp(projPos.z, targetBox.minZ, targetBox.maxZ);
            hitPos = new Vec3(hitX, hitY, hitZ);
        }

        if (bounceActive || hasBounced) {
            if (hit.getType() == HitResult.Type.ENTITY) {
                onHitEntity((EntityHitResult) hit, hitPos);
                log.debug("[VR Throw] Projectile {} hit entity during return, dropping", this.getId());
            } else {
                log.debug("[VR Throw] Projectile {} hit block during return, dropping", this.getId());
            }
            dropAndDiscard();
            return;
        }

        if (!level().isClientSide) {
            boolean hitEntity = hit.getType() == HitResult.Type.ENTITY;
            if (hitEntity) {
                EntityHitResult entityHit = (EntityHitResult) hit;

                onHitEntity(entityHit, hitPos);
                float attackDamage = stackBaseDamage(getItem());
                if (attackDamage <= 1.0F) {
                    dropAndDiscard();
                    return;
                }

                if (ConfigHelper.ACTIVE.weaponEffect == WeaponEffectType.BOOMERANG) {
                    boolean shouldBounce = BoomerangEffect.canBounce(getItem().getItem())
                            && !hasBounced && !reachedOriginOnce;
                    if (shouldBounce) {
                        log.debug("[VR Throw] Starting boomerang effect for {}", this.getId());
                        BoomerangEffect.startBounce(this);
                        bounceActive = true;
                        this.entityData.set(BOUNCE_ACTIVE, true);
                        return;
                    }
                } else if (ConfigHelper.ACTIVE.weaponEffect == WeaponEffectType.EMBED) {
                    if (damageDealt) {
                        EmbeddingEffect.startEmbedding(this, entityHit, hitPos);
                        return;
                    } else {
                        log.debug("[VR Throw] Damage blocked, not embedding projectile {}", this.getId());
                    }
                }
            }
            dropAndDiscard();
        } else {
            level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, getItem()),
                    getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    protected void onHitEntity(EntityHitResult res, Vec3 hitPos) {
        Entity target = res.getEntity();
        ServerLevel world = (ServerLevel) level();
        DamageSource src = world.damageSources().thrown(this, getOwner() == null ? this : getOwner());

        float base = stackBaseDamage(getItem());
        float enchBonus = getEnchantmentDamageBonus(getItem(), target);
        float total = base + enchBonus;

        log.debug("[VR Throw] Damage dealt: Item={}, Base={}, Ench={}, Final={}, Target={}, State={}",
                getItem().getItem(), base, enchBonus, total, target.getName().getString(),
                bounceActive ? "RETURNING" : "FORWARD");

        if (VRThrowingExtensions.debugMode && getOwner() instanceof ServerPlayer sp) {
            Component msg = Component.literal(String.format(
                    "[VR Throw] Damage: Item=%s, Base=%.2f, Ench=%.2f, Final=%.2f, Target=%s, State=%s",
                    getItem().getItem(), base, enchBonus, total, target.getName().getString(),
                    bounceActive ? "RETURNING" : "FORWARD"));
            sp.sendSystemMessage(msg, false);
        }

        damageDealt = target.hurt(src, total);

        log.debug("[VR Throw] Damage result: dealt={}, target={}", damageDealt, target.getName().getString());

        // Send blood particles using the pre-calculated clamped hit position
        Vec3 velocity = getDeltaMovement();
        int playersSent = 0;
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            if (player.level() == world && player.distanceToSqr(hitPos) < 4096) { // 64 blocks
                win.demistorm.network.Network.INSTANCE.sendToPlayer(player,
                    new win.demistorm.network.BloodParticleData(hitPos.x, hitPos.y, hitPos.z, velocity.x, velocity.y, velocity.z));
                playersSent++;
            }
        }
        log.debug("[Server] Sent blood particle packet to {} players at ({}, {}, {})", playersSent, hitPos.x, hitPos.y, hitPos.z);

        Vec3 push = getDeltaMovement().normalize().scale(0.5);
        target.push(push.x, 0.1 + push.y, push.z);
    }

    private ItemStack createDropStack() {
        ItemStack drop = getItem().copy();
        if (drop.isDamageableItem()) {
            int totalDamage = Mth.clamp(drop.getDamageValue() + stackSize, 0, drop.getMaxDamage());
            drop.setDamageValue(totalDamage);
        }
        return drop;
    }

    // Returns the item's attack damage when held in hand (player base 1.0 + item modifiers)
    public static float stackBaseDamage(ItemStack stack) {
        float baseDamage = 1.0F; // Player bare hand damage

        // Get damage modifiers from main hand slot in 1.20.1
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);

        for (Attribute attribute : modifiers.keySet()) {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                for (AttributeModifier modifier : modifiers.get(attribute)) {
                    double amount = modifier.getAmount();
                    AttributeModifier.Operation operation = modifier.getOperation();

                    if (operation == AttributeModifier.Operation.ADDITION) {
                        baseDamage += (float) amount;
                    } else if (operation == AttributeModifier.Operation.MULTIPLY_BASE) {
                        baseDamage += (float) (baseDamage * amount);
                    } else if (operation == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                        baseDamage *= (float) (1.0 + amount);
                    }
                }
            }
        }

        return Math.max(1.0F, baseDamage);
    }

    // Calculate enchantment damage bonus
    private static float getEnchantmentDamageBonus(ItemStack stack, Entity target) {
        MobType mobType = target instanceof LivingEntity ? ((LivingEntity) target).getMobType() : MobType.UNDEFINED;
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(stack, mobType);
    }

    public void clearSpawnImmunity() {
        this.immunityTicks = 0;
        log.debug("[VR Throw] Projectile {} spawn immunity cleared", this.getId());
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return net.minecraft.world.item.Items.STICK;
    }

    public void setOriginalThrowPos(Vec3 v) { this.originalThrowPos = v; }

    public void beginEmbedding(LivingEntity host, Vec3 worldOffset,
                               float yawDeg, float pitchDeg, float tiltDeg, float initialXRollDeg) {
        this.embeddedTarget = host;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(host.position().add(worldOffset));

        float hostBodyYaw = host.getYHeadRot();
        float hostPitch = host.getXRot();
        Vec3 localOffset = rotateY(worldOffset, -hostBodyYaw);

        this.embeddedOffset = localOffset;
        this.embeddedLocalYaw = Mth.wrapDegrees(yawDeg - hostBodyYaw);
        this.embeddedLocalPitch = Mth.wrapDegrees(pitchDeg - hostPitch);

        this.entityData.set(IS_EMBEDDED, true);
        this.entityData.set(EMBED_YAW, yawDeg);
        this.entityData.set(EMBED_PITCH, pitchDeg);
        this.entityData.set(EMBED_ROLL, initialXRollDeg);
        this.entityData.set(EMBED_TILT, tiltDeg);

        VRThrowingExtensions.log.debug("[Embed] beginEmbedding: proj={} host={} worldOffset={} localOffset={} yaw={} pitch={} tilt={} xRollStart={}",
                getId(), host.getName().getString(), worldOffset, localOffset,
                String.format("%.1f", yawDeg), String.format("%.1f", pitchDeg),
                String.format("%.1f", tiltDeg), String.format("%.1f", initialXRollDeg));
    }

    public void clearEmbedding() {
        if (!this.level().isClientSide() && this.embeddedTarget != null) {
            win.demistorm.effects.EmbeddingEffect.BleedManager.unregister(this.embeddedTarget, this);
        }
        this.entityData.set(IS_EMBEDDED, false);
        this.embeddedTarget = null;
        this.embeddedOffset = Vec3.ZERO;
        this.embeddedLocalYaw = 0f;
        this.embeddedLocalPitch = 0f;
        this.setNoGravity(false);
    }

    public boolean isEmbedded() { return this.entityData.get(IS_EMBEDDED); }
    public float getEmbedYaw()  { return this.entityData.get(EMBED_YAW); }
    public float getEmbedPitch(){ return this.entityData.get(EMBED_PITCH); }
    public float getEmbedRoll() { return this.entityData.get(EMBED_ROLL); }
    public void setEmbedRoll(float v) { this.entityData.set(EMBED_ROLL, v); }
    public float getEmbedTilt() { return this.entityData.get(EMBED_TILT); }
    public Entity getEmbeddedTarget() { return this.embeddedTarget; }

    public Vec3 getEmbeddedOffset()      { return this.embeddedOffset; }
    public float getEmbeddedLocalYaw()   { return this.embeddedLocalYaw; }
    public float getEmbeddedLocalPitch() { return this.embeddedLocalPitch; }
    public void setEmbedYaw(float v)     { this.entityData.set(EMBED_YAW, v); }
    public void setEmbedPitch(float v)   { this.entityData.set(EMBED_PITCH, v); }

    private static Vec3 rotateY(Vec3 v, float degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    public void dropAndDiscard() {
        if (isEmbedded()) clearEmbedding();

        clearSpawnImmunity();

        if (alreadyDropped) {
            discard();
            return;
        }
        alreadyDropped = true;
        if (!level().isClientSide()) {
            ItemStack dropStack = createDropStack();
            dropStack.setCount(stackSize);
            level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    level(), getX(), getY(), getZ(), dropStack));
        }
        discard();
    }
}
