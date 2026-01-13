package win.demistorm;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.MoverType;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;

// Custom PrimedTnt entity that supports hand roll for proper spinning
public class ThrownTNTEntity extends PrimedTnt {

    private static final EntityDataAccessor<Float> HAND_ROLL =
            SynchedEntityData.defineId(ThrownTNTEntity.class, EntityDataSerializers.FLOAT);

    public ThrownTNTEntity(EntityType<? extends PrimedTnt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HAND_ROLL, 0f);
    }

    public void setHandRoll(float deg) {
        this.entityData.set(HAND_ROLL, deg);
    }

    public float getHandRoll() {
        return this.entityData.get(HAND_ROLL);
    }


    @Override
    public void tick() {
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0F, -0.04, 0.0F));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5F, 0.7));
        }

        int i = this.getFuse() - 1;
        this.setFuse(i);
        if (i <= 0) {
            this.discard();
            if (!this.level().isClientSide) {
                this.explode();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide) {
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
        this.level().explode(this, this.getX(), this.getY(0.0625F), this.getZ(), 4.0F, Level.ExplosionInteraction.TNT);
    }
}
