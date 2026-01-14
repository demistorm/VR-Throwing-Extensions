package win.demistorm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

// Custom PrimedTnt entity that supports hand roll for proper spinning
public class ThrownTNTEntity extends PrimedTnt {

    private static final EntityDataAccessor<Float> HAND_ROLL =
            SynchedEntityData.defineId(ThrownTNTEntity.class, EntityDataSerializers.FLOAT);

    public ThrownTNTEntity(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HAND_ROLL, 0f);
    }

    public void setHandRoll(float deg) {
        this.entityData.set(HAND_ROLL, deg);
    }

    public float getHandRoll() {
        return this.entityData.get(HAND_ROLL);
    }

    private static final ExplosionDamageCalculator USED_PORTAL_DAMAGE_CALCULATOR;
    private boolean usedPortal;
    private float explosionPower = 4.0F;

    private void setUsedPortal(boolean bl) {
        this.usedPortal = bl;
    }

    @Nullable
    public Entity teleport(TeleportTransition teleportTransition) {
        Entity entity = super.teleport(teleportTransition);
        if (entity instanceof PrimedTnt primedTnt) {
            this.setUsedPortal(true);
        }

        return entity;
    }

    @Override
    public void tick() {
        this.handlePortal();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.applyEffectsFromBlocks();
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, (double)-0.5F, 0.7));
        }

        int i = this.getFuse() - 1;
        this.setFuse(i);
        if (i <= 0) {
            this.discard();
            if (!this.level().isClientSide()) {
                this.explode();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide()) {
                spawnColoredSmokeParticle();
            }
        }

    }

    // Spawn colored smoke particle based on fuse time
    private void spawnColoredSmokeParticle() {
        int remainingFuse = this.getFuse();

        // Calculate color based on remaining fuse (100 ticks max)
        // Phase 1 (100-75 ticks): Black/dark gray (0.05-0.15)
        // Phase 2 (75-50 ticks): Dark red to bright red (0.0, 0.0-0.5, 0.0)
        // Phase 3 (50-25 ticks): Red to orange (1.0, 0.0-0.5, 0.0)
        // Phase 4 (25-0 ticks): Orange to white (1.0, 0.5-1.0, 0.0-1.0)

        float r, g, b;

        if (remainingFuse > 75) {
            // Black/dark gray phase
            float t = (remainingFuse - 75) / 25.0f; // 1.0 to 0.0
            r = 0.05f + t * 0.05f;  // 0.10 to 0.05
            g = 0.05f + t * 0.05f;  // 0.10 to 0.05
            b = 0.05f + t * 0.05f;  // 0.10 to 0.05
        } else if (remainingFuse > 50) {
            // Dark red to bright red phase
            float t = (remainingFuse - 50) / 25.0f; // 1.0 to 0.0
            r = 0.3f + (1.0f - t) * 0.7f;  // 1.0 to 0.3
            g = 0.0f;
            b = 0.0f;
        } else if (remainingFuse > 25) {
            // Red to orange phase
            float t = (remainingFuse - 25) / 25.0f; // 1.0 to 0.0
            r = 1.0f;
            g = (1.0f - t) * 0.5f;  // 0.5 to 0.0
            b = 0.0f;
        } else {
            // Orange to white phase
            float t = remainingFuse / 25.0f; // 1.0 to 0.0
            r = 1.0f;
            g = 0.5f + (1.0f - t) * 0.5f;  // 0.5 to 1.0
            b = (1.0f - t);  // 0.0 to 1.0
        }

        // Spawn the colored smoke particle using custom particle class
        win.demistorm.client.particles.TNTSmokeParticle.spawnColoredSmoke(
                r, g, b,
                this.getX(),
                this.getY() + 0.5,
                this.getZ()
        );
    }

    private void explode() {
        Level var2 = this.level();
        if (var2 instanceof ServerLevel serverLevel) {
            if (serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
                this.level().explode(this, Explosion.getDefaultDamageSource(this.level(), this), this.usedPortal ? USED_PORTAL_DAMAGE_CALCULATOR : null, this.getX(), this.getY((double)0.0625F), this.getZ(), this.explosionPower, false, Level.ExplosionInteraction.TNT);
            }
        }

    }
    
    static {
        USED_PORTAL_DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, float f) {
                return blockState.is(Blocks.NETHER_PORTAL) ? false : super.shouldBlockExplode(explosion, blockGetter, blockPos, blockState, f);
            }

            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
                return blockState.is(Blocks.NETHER_PORTAL) ? Optional.empty() : super.getBlockExplosionResistance(explosion, blockGetter, blockPos, blockState, fluidState);
            }
        };
    }
}
