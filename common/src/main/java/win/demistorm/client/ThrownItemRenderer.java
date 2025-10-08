package win.demistorm.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import win.demistorm.ThrownProjectileEntity;

// Renders the thrown item projectile for the client
@Environment(EnvType.CLIENT)
public class ThrownItemRenderer extends EntityRenderer<ThrownProjectileEntity, ThrownItemRenderer.ThrownItemRenderState> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public ThrownItemRenderer(Context ctx) {
        super(ctx);
        this.itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        this.scale = 0.5f; // Item display scale
        this.shadowOpacity = 0.5f; // Shadow opacity
    }

    @Override
    public ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void updateRenderState(ThrownProjectileEntity entity, ThrownItemRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.itemStack = entity.getStack();
        state.velocity = entity.getVelocity();
        state.age = entity.age + tickDelta;
        state.handRollDeg = entity.getHandRoll();
        state.isCatching = entity.isCatching();
        state.isBounceActive = entity.isBounceActive();

        // Embedding render state
        state.isEmbedded = entity.isEmbedded();
        if (state.isEmbedded) {
            state.embedYawDeg = entity.getEmbedYaw();
            state.embedPitchDeg = entity.getEmbedPitch();
            state.embedRollDeg = entity.getEmbedRoll(); // X settle angle (animated on server)
            state.embedTiltDeg = entity.getEmbedTilt(); // Z roll from controller (constant)
        }
    }

    @Override
    public void render(ThrownItemRenderState state,
                       MatrixStack matrices,
                       VertexConsumerProvider vcp,
                       int light) {
        matrices.push();

        // Freezes rotation when item is embedded
        if (state.isEmbedded) {
            // Match flight order: yaw -> pitch -> Z tilt (hand roll) -> X roll (settle)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F - state.embedYawDeg));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-state.embedPitchDeg));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(state.embedTiltDeg)); // hand tilt
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.embedRollDeg)); // X settle spin
            matrices.scale(scale, scale, scale);
            itemRenderer.renderItem(
                    state.itemStack,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    light,
                    OverlayTexture.DEFAULT_UV,
                    matrices,
                    vcp,
                    null,
                    0
            );
            matrices.pop();
            super.render(state, matrices, vcp, light);
            return;
        }

        // Non-embedded path: existing logic
        Vec3d vel = state.velocity;

        if (vel.length() > 0.001) {
            // Calculate yaw (horizontal rotation)
            float yaw = (float)(MathHelper.atan2(vel.z, vel.x) * 180.0 / Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F - yaw));

            // Calculate pitch (vertical rotation)
            float hor = MathHelper.sqrt((float)(vel.x * vel.x + vel.z * vel.z));
            float pitch = (float)(MathHelper.atan2(vel.y, hor) * 180.0 / Math.PI);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));

            // Add hand tilt
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-state.handRollDeg));
        }

        // Spinning behavior based on state
        if (state.isCatching) {
            // Slower rotation while being caught
            float smoothSpin = (state.age * 5.0F) % 360F;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(smoothSpin));

            // Add slight bobbing effect while being magnetized
            float bobOffset = MathHelper.sin(state.age * 0.5F) * 0.05F;
            matrices.translate(0, bobOffset, 0);
        } else if (state.isBounceActive) {
            // Rotation for boomerang return flight
            float returnSpin = (state.age * 8.0F) % 360F; // Medium speed spin
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(returnSpin));

            // Add subtle wobble
            float wobble = MathHelper.sin(state.age * 0.35F) * 3.0F;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(wobble));

            // Slight pulsing scale effect
            float pulseScale = 1.0F + MathHelper.sin(state.age * 0.4F) * 0.05F;
            matrices.scale(pulseScale, pulseScale, pulseScale);
        } else {
            // Normal fast spinning during forward flight
            float spinSpeed = 15.0F; // Speed of flipping motion
            float spin = (state.age * spinSpeed) % 360F;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        }

        // Apply scale to item
        matrices.scale(scale, scale, scale);

        // Render the model
        itemRenderer.renderItem(
                state.itemStack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vcp,
                null,
                0
        );

        matrices.pop();
        super.render(state, matrices, vcp, light);
    }

    public static class ThrownItemRenderState extends EntityRenderState {
        public ItemStack itemStack = ItemStack.EMPTY;
        public Vec3d velocity = Vec3d.ZERO;
        public float age = 0.0f;
        public float handRollDeg = 0f;
        public boolean isCatching = false;
        public boolean isBounceActive = false;
        public boolean isEmbedded = false;
        public float embedYawDeg = 0f;
        public float embedPitchDeg = 0f;
        public float embedRollDeg = 0f;
        public float embedTiltDeg = 0f;
    }
}
