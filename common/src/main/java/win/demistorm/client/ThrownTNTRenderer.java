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
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;
import win.demistorm.ThrownTNTEntity;

// Renders thrown primed TNT as an item with velocity-based spinning
public class ThrownTNTRenderer extends EntityRenderer<ThrownTNTEntity, ThrownTNTRenderer.ThrownTNTRenderState> {
    private final ItemRenderer itemRenderer;
    private final float scale;

    public ThrownTNTRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.scale = 0.7f;
        this.shadowRadius = 0.0f;
    }

    @Override
    public @NotNull ThrownTNTRenderer.ThrownTNTRenderState createRenderState() {
        return new ThrownTNTRenderState();
    }

    @Override
    public void extractRenderState(ThrownTNTEntity entity, ThrownTNTRenderState state, float tickDelta) {
        state.velocity = entity.getDeltaMovement();
        state.age = entity.tickCount + tickDelta;
        state.handRollDeg = entity.getHandRoll();
    }

    @Override
    public void render(ThrownTNTRenderState state,
                       PoseStack matrices,
                       MultiBufferSource vcp,
                       int light) {

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

        // Render TNT as an item using item renderer
        ItemStack tntItemStack = new ItemStack(Items.TNT);
        try {
            itemRenderer.renderStatic(
                    Minecraft.getInstance().player,
                    tntItemStack,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    matrices,
                    vcp,
                    Minecraft.getInstance().level,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    0
            );
        } catch (Exception e) {
            System.err.println("VR Throwing Extensions: Could not render thrown TNT: " + e.getMessage());
        }

        matrices.popPose();
        super.render(state, matrices, vcp, light);
    }

    public static class ThrownTNTRenderState extends EntityRenderState {
        public Vec3 velocity = Vec3.ZERO;
        public float age = 0.0f;
        public float handRollDeg = 0f;
    }
}
