package win.demistorm;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
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
}
