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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import win.demistorm.ThrownProjectileEntity;

/**
 * 1.21.10 submit-pipeline ThrownItemRenderer
 * - render(...) -> submit(...)
 * - MultiBufferSource -> SubmitNodeCollector
 * - Use ItemModelResolver.updateForNonLiving(...) and ItemStackRenderState#submit(...)
 */
public class ThrownItemRenderer extends EntityRenderer<ThrownProjectileEntity, ThrownItemRenderer.ThrownItemRenderState> {
    private final ItemModelResolver itemModelResolver;
    private final float scale;

    public ThrownItemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemModelResolver = ctx.getItemModelResolver();
        this.scale = 0.7f;        // Item display scale
        this.shadowRadius = 0.0f; // Shadow opacity
    }

    @Override
    public @NotNull ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void extractRenderState(ThrownProjectileEntity entity, ThrownItemRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);

        state.itemStack = entity.getItem();
        state.velocity = entity.getDeltaMovement();
        state.age = entity.tickCount + tickDelta;
        state.handRollDeg = entity.getHandRoll();
        state.isCatching = entity.isCatching();
        state.isBounceActive = entity.isBounceActive();

        // Packed light for submit()
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
        state.lightCoords = LevelRenderer.getLightColor(entity.level(), pos);

        // Embedding render state
        state.isEmbedded = entity.isEmbedded();
        if (state.isEmbedded) {
            state.embedYawDeg = entity.getEmbedYaw();
            state.embedPitchDeg = entity.getEmbedPitch();
            state.embedRollDeg = entity.getEmbedRoll();
            state.embedTiltDeg = entity.getEmbedTilt();
        }

        // Prepare the item’s render state (correct 1.21.10 signature)
        // updateForNonLiving(itemState, stack, displayContext, entity)
        itemModelResolver.updateForNonLiving(
                state.item,
                state.itemStack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                entity
        );
    }

    @Override
    public void submit(ThrownItemRenderState state,
                       PoseStack matrices,
                       SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        super.submit(state, matrices, collector, cameraState);

        if (state.item.isEmpty()) {
            return;
        }

        matrices.pushPose();

        // Embedded orientation
        if (state.isEmbedded) {
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F - state.embedYawDeg));
            matrices.mulPose(Axis.XP.rotationDegrees(-state.embedPitchDeg));
            matrices.mulPose(Axis.ZP.rotationDegrees(state.embedTiltDeg));
            matrices.mulPose(Axis.XP.rotationDegrees(state.embedRollDeg));
            matrices.scale(scale, scale, scale);

            // Attempt a rotation flip instead of a scale hack
            matrices.mulPose(Axis.YP.rotationDegrees(180.0F));

            // Correct 1.21.10 signature: submit(pose, collector, light, overlay, outlineColor)
            state.item.submit(matrices, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

            matrices.popPose();
            return;
        }

        // Flight/spin logic
        Vec3 vel = state.velocity;

        if (vel.length() > 0.001) {
            float yaw = (float)(Mth.atan2(vel.z, vel.x) * 180.0 / Math.PI);
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F - yaw));

            float hor = Mth.sqrt((float)(vel.x * vel.x + vel.z * vel.z));
            float pitch = (float)(Mth.atan2(vel.y, hor) * 180.0 / Math.PI);
            matrices.mulPose(Axis.XP.rotationDegrees(-pitch));

            matrices.mulPose(Axis.ZP.rotationDegrees(-state.handRollDeg));
        }

        if (state.isCatching) {
            float smoothSpin = (state.age * 5.0F) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(smoothSpin));
            float bobOffset = Mth.sin(state.age * 0.5F) * 0.05F;
            matrices.translate(0, bobOffset, 0);
        } else if (state.isBounceActive) {
            float returnSpin = (state.age * 8.0F) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(returnSpin));
            float wobble = Mth.sin(state.age * 0.35F) * 3.0F;
            matrices.mulPose(Axis.YP.rotationDegrees(wobble));
            float pulseScale = 1.0F + Mth.sin(state.age * 0.4F) * 0.05F;
            matrices.scale(pulseScale, pulseScale, pulseScale);
        } else {
            float spinSpeed = 15.0F;
            float spin = (state.age * spinSpeed) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(spin));
        }

        matrices.scale(scale, scale, scale);

        // Attempt a rotation flip instead of a scale hack (worked perfectly wow, why didn't I try this before)
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F));

        // Submit prepared item
        state.item.submit(matrices, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);

        matrices.popPose();
    }

    public static class ThrownItemRenderState extends EntityRenderState {
        public ItemStack itemStack = ItemStack.EMPTY;
        public final ItemStackRenderState item = new ItemStackRenderState();

        public Vec3 velocity = Vec3.ZERO;
        public float age = 0.0f;
        public float handRollDeg = 0f;
        public boolean isCatching = false;
        public boolean isBounceActive = false;
        public boolean isEmbedded = false;
        public float embedYawDeg = 0f;
        public float embedPitchDeg = 0f;
        public float embedRollDeg = 0f;
        public float embedTiltDeg = 0f;

        public int lightCoords = 0;
        // outlineColor is inherited from EntityRenderState
    }
}
