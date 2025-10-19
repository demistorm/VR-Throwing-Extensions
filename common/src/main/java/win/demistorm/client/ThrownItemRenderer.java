package win.demistorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;
import win.demistorm.ThrownProjectileEntity;

// Renders the thrown item projectile for the client
public class ThrownItemRenderer extends EntityRenderer<ThrownProjectileEntity> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public ThrownItemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.scale = 0.5f; // Item display scale
        this.shadowStrength = 0.5f; // Shadow opacity
    }

    @Override
    public void render(ThrownProjectileEntity entity,
                       float entityYaw,
                       float tickDelta,
                       PoseStack matrices,
                       MultiBufferSource vcp,
                       int light) {

        matrices.pushPose();

        // Get render state directly from entity
        ItemStack itemStack = entity.getItem();
        Vec3 velocity = entity.getDeltaMovement();
        float age = entity.tickCount + tickDelta;
        float handRollDeg = entity.getHandRoll();
        boolean isCatching = entity.isCatching();
        boolean isBounceActive = entity.isBounceActive();
        boolean isEmbedded = entity.isEmbedded();

        // Freezes rotation when item is embedded
        if (isEmbedded) {
            // Match flight order: yaw -> pitch -> Z tilt (hand roll) -> X roll (settle)
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F - entity.getEmbedYaw()));
            matrices.mulPose(Axis.XP.rotationDegrees(-entity.getEmbedPitch()));
            matrices.mulPose(Axis.ZP.rotationDegrees(entity.getEmbedTilt())); // hand tilt
            matrices.mulPose(Axis.XP.rotationDegrees(entity.getEmbedRoll())); // X settle spin
            matrices.scale(scale, scale, scale);

            // Use renderStatic method
            try {
                itemRenderer.renderStatic(
                        itemStack,
                        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        matrices,
                        vcp,
                        Minecraft.getInstance().level,
                        0
                );
            } catch (Exception e) {
                System.err.println("VR Throwing Extensions: Could not render embedded item: " + e.getMessage());
            }

            matrices.popPose();
            super.render(entity, entityYaw, tickDelta, matrices, vcp, light);
            return;
        }

        // Non-embedded path: existing logic
        if (velocity.length() > 0.001) {
            // Calculate yaw (horizontal rotation)
            float yaw = (float)(Mth.atan2(velocity.z, velocity.x) * 180.0 / Math.PI);
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F - yaw));

            // Calculate pitch (vertical rotation)
            float hor = Mth.sqrt((float)(velocity.x * velocity.x + velocity.z * velocity.z));
            float pitch = (float)(Mth.atan2(velocity.y, hor) * 180.0 / Math.PI);
            matrices.mulPose(Axis.XP.rotationDegrees(-pitch));

            // Add hand tilt
            matrices.mulPose(Axis.ZP.rotationDegrees(-handRollDeg));
        }

        // Spinning behavior based on state
        if (isCatching) {
            // Slower rotation while being caught
            float smoothSpin = (age * 5.0F) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(smoothSpin));

            // Add slight bobbing effect while being magnetized
            float bobOffset = Mth.sin(age * 0.5F) * 0.05F;
            matrices.translate(0, bobOffset, 0);
        } else if (isBounceActive) {
            // Rotation for boomerang return flight
            float returnSpin = (age * 8.0F) % 360F; // Medium speed spin
            matrices.mulPose(Axis.XP.rotationDegrees(returnSpin));

            // Add subtle wobble
            float wobble = Mth.sin(age * 0.35F) * 3.0F;
            matrices.mulPose(Axis.YP.rotationDegrees(wobble));

            // Slight pulsing scale effect
            float pulseScale = 1.0F + Mth.sin(age * 0.4F) * 0.05F;
            matrices.scale(pulseScale, pulseScale, pulseScale);
        } else {
            // Normal fast spinning during forward flight
            float spinSpeed = 15.0F; // Speed of flipping motion
            float spin = (age * spinSpeed) % 360F;
            matrices.mulPose(Axis.XP.rotationDegrees(spin));
        }

        // Apply scale to item
        matrices.scale(scale, scale, scale);

        // Render the model using renderStatic
        try {
            itemRenderer.renderStatic(
                    itemStack,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    matrices,
                    vcp,
                    Minecraft.getInstance().level,
                    0
            );
        } catch (Exception e) {
            System.err.println("VR Throwing Extensions: Could not render thrown item: " + e.getMessage());
        }

        matrices.popPose();
        super.render(entity, entityYaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(ThrownProjectileEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}