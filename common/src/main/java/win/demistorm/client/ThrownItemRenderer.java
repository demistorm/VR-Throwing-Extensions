package win.demistorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;
import win.demistorm.ThrownProjectileEntity;

// Renders the thrown item projectile for the client
public class ThrownItemRenderer extends EntityRenderer<ThrownProjectileEntity, ThrownItemRenderer.ThrownItemRenderState> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public ThrownItemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.scale = 0.5f; // Item display scale
        this.shadowRadius = 0.5f; // Shadow opacity
    }

    @Override
    public @NotNull ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void extractRenderState(ThrownProjectileEntity entity, ThrownItemRenderState state, float tickDelta) {
        state.itemStack = entity.getItem();
        state.velocity = entity.getDeltaMovement();
        state.age = entity.tickCount + tickDelta;
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
                       PoseStack matrices,
                       MultiBufferSource vcp,
                       int light) {

        matrices.pushPose();

        // Freezes rotation when item is embedded
        if (state.isEmbedded) {
            // Match flight order: yaw -> pitch -> Z tilt (hand roll) -> X roll (settle)
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F - state.embedYawDeg));
            matrices.mulPose(Axis.XP.rotationDegrees(-state.embedPitchDeg));
            matrices.mulPose(Axis.ZP.rotationDegrees(state.embedTiltDeg)); // hand tilt
            matrices.mulPose(Axis.XP.rotationDegrees(state.embedRollDeg)); // X settle spin
            matrices.scale(scale, scale, scale);

            // Use renderStatic method
            try {
                itemRenderer.renderStatic(
                        Minecraft.getInstance().player,
                        state.itemStack,
                        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        matrices,
                        vcp,
                        Minecraft.getInstance().level,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        0
                );
            } catch (Exception e) {
                System.err.println("VR Throwing Extensions: Could not render embedded item: " + e.getMessage());
            }

            matrices.popPose();
            super.render(state, matrices, vcp, light);
            return;
        }

        // Non-embedded path: existing logic
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

        // Spinning behavior based on state
        if (state.isCatching) {
            // Slower rotation while being caught
            float smoothSpin = (state.age * 5.0F) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(smoothSpin));

            // Add slight bobbing effect while being magnetized
            float bobOffset = Mth.sin(state.age * 0.5F) * 0.05F;
            matrices.translate(0, bobOffset, 0);
        } else if (state.isBounceActive) {
            // Rotation for boomerang return flight
            float returnSpin = (state.age * 8.0F) % 360F; // Medium speed spin
            matrices.mulPose(Axis.XP.rotationDegrees(returnSpin));

            // Add subtle wobble
            float wobble = Mth.sin(state.age * 0.35F) * 3.0F;
            matrices.mulPose(Axis.YP.rotationDegrees(wobble));

            // Slight pulsing scale effect
            float pulseScale = 1.0F + Mth.sin(state.age * 0.4F) * 0.05F;
            matrices.scale(pulseScale, pulseScale, pulseScale);
        } else {
            // Normal fast spinning during forward flight
            float spinSpeed = 15.0F; // Speed of flipping motion
            float spin = (state.age * spinSpeed) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(spin));
        }

        // Apply scale to item
        matrices.scale(scale, scale, scale);

        // Render the model using renderStatic
        try {
            itemRenderer.renderStatic(
                    Minecraft.getInstance().player,
                    state.itemStack,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    matrices,
                    vcp,
                    Minecraft.getInstance().level,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    0
            );
        } catch (Exception e) {
            System.err.println("VR Throwing Extensions: Could not render thrown item: " + e.getMessage());
        }

        matrices.popPose();
        super.render(state, matrices, vcp, light);
    }

    public static class ThrownItemRenderState extends EntityRenderState {
        public ItemStack itemStack = ItemStack.EMPTY;
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
    }
}