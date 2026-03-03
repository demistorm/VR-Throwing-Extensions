package win.demistorm.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;
import win.demistorm.ThrownTNTEntity;

// Renders thrown primed TNT as an item with velocity-based spinning
public class ThrownTNTRenderer extends EntityRenderer<ThrownTNTEntity> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public ThrownTNTRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.scale = 0.7f;
        this.shadowStrength = 0.0f;
    }

    @Override
    public void render(ThrownTNTEntity entity,
                       float entityYaw,
                       float tickDelta,
                       PoseStack matrices,
                       MultiBufferSource vcp,
                       int light) {

        matrices.pushPose();

        // Get render state directly from entity
        Vec3 velocity = entity.getDeltaMovement();
        float age = entity.tickCount + tickDelta;
        float handRollDeg = entity.getHandRoll();

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

        // Velocity-based spin speed (full spin or no spin)
        float speed = (float) velocity.length();
        float spinThreshold = 0.1f; // Above this, spin; below, don't
        float spinSpeed = (speed > spinThreshold) ? 15.0F : 0.0F;

        float spin = (age * spinSpeed) % 360F;
        matrices.mulPose(Axis.XP.rotationDegrees(spin));

        // Flip to match item orientation (same as ThrownItemRenderer)
        matrices.mulPose(Axis.YP.rotationDegrees(180.0F));

        // Apply scale
        matrices.scale(scale, scale, scale);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Render TNT as an item using item renderer
        ItemStack tntItemStack = new ItemStack(Items.TNT);
        try {
            itemRenderer.renderStatic(
                    tntItemStack,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    matrices,
                    vcp,
                    Minecraft.getInstance().level,
                    0
            );
        } catch (Exception e) {
            System.err.println("VR Throwing Extensions: Could not render thrown TNT: " + e.getMessage());
        }

        matrices.popPose();
        super.render(entity, entityYaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(ThrownTNTEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
