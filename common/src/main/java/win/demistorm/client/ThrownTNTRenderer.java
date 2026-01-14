package win.demistorm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import win.demistorm.ThrownTNTEntity;

/**
 * 1.21.10 submit-pipeline ThrownTNTRenderer
 * - render(...) -> submit(...)
 * - MultiBufferSource -> SubmitNodeCollector
 * - Use ItemModelResolver.updateForNonLiving(...) and ItemStackRenderState#submit(...)
 */
public class ThrownTNTRenderer extends EntityRenderer<ThrownTNTEntity, ThrownTNTRenderer.ThrownTNTRenderState> {
    private final ItemModelResolver itemModelResolver;
    private final float scale;

    public ThrownTNTRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemModelResolver = ctx.getItemModelResolver();
        this.scale = 0.7f;
        this.shadowRadius = 0.0f;
    }

    @Override
    public @NotNull ThrownTNTRenderState createRenderState() {
        return new ThrownTNTRenderState();
    }

    @Override
    public void extractRenderState(ThrownTNTEntity entity, ThrownTNTRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);

        state.velocity = entity.getDeltaMovement();
        state.age = entity.tickCount + tickDelta;
        state.handRollDeg = entity.getHandRoll();

        // Packed light for submit()
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        state.lightCoords = LevelRenderer.getLightColor(entity.level(), pos);

        // Prepare the item's render state (correct 1.21.10 signature)
        // updateForNonLiving(itemState, stack, displayContext, entity)
        itemModelResolver.updateForNonLiving(
                state.item,
                state.itemStack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                entity
        );
    }

    @Override
    public void submit(ThrownTNTRenderState state,
                       PoseStack matrices,
                       SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        super.submit(state, matrices, collector, cameraState);

        if (state.item.isEmpty()) {
            return;
        }

        matrices.pushPose();

        Vec3 vel = state.velocity;

        if (vel.length() > 0.001) {
            // Calculate yaw (horizontal rotation)
            float yaw = (float)(Mth.atan2(vel.z, vel.x) * 180.0 / Math.PI);
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F - yaw));

            // Calculate pitch (vertical rotation)
            float hor = Mth.sqrt((float)(vel.x * vel.x + vel.z * vel.z));
            float pitch = (float)(Mth.atan2(vel.y, hor) * 180.0 / Math.PI);
            matrices.mulPose(Axis.XP.rotationDegrees(-pitch));

            // Add hand tilt
            matrices.mulPose(Axis.ZP.rotationDegrees(-state.handRollDeg));
        }

        // Velocity-based spin speed - full spin or no spin
        float speed = (float) vel.length();
        float spinThreshold = 0.1f; // Above this, spin; below, don't
        float spinSpeed = (speed > spinThreshold) ? 15.0F : 0.0F;

        float spin = (state.age * spinSpeed) % 360F;
        matrices.mulPose(Axis.XP.rotationDegrees(spin));

        // Flip to match item orientation (same as ThrownItemRenderer)
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F));

        // Apply scale
        matrices.scale(scale, scale, scale);

        // Submit prepared TNT item
        state.item.submit(matrices, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

        matrices.popPose();
    }

    public static class ThrownTNTRenderState extends EntityRenderState {
        public final ItemStack itemStack = new ItemStack(Items.TNT);
        public final ItemStackRenderState item = new ItemStackRenderState();

        public Vec3 velocity = Vec3.ZERO;
        public float age = 0.0f;
        public float handRollDeg = 0f;
        public int lightCoords = 0;
        // outlineColor is inherited from EntityRenderState
    }
}
