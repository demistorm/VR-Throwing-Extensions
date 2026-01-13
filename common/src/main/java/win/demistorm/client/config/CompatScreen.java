package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import win.demistorm.ConfigHelper;

import java.util.Arrays;
import java.util.List;

// Mod compatibility configuration menu
public final class CompatScreen {

    private CompatScreen() {}

    public static class CompatToggleScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private boolean immersiveMCThrowablesValue = ConfigHelper.CLIENT.immersiveMCThrowables;
        private boolean throwConflictingItemsValue = ConfigHelper.CLIENT.throwConflictingItems;

        // Button bounds for tooltip detection
        private int immersiveMCButtonX, immersiveMCButtonY, immersiveMCButtonWidth, immersiveMCButtonHeight;
        private int throwConflictingButtonX, throwConflictingButtonY, throwConflictingButtonWidth, throwConflictingButtonHeight;

        protected CompatToggleScreen(Screen parent) {
            super(Component.literal("VR Throwing Extensions - Compat Toggles"));
            this.parent = parent;
        }

        // Create screen for Compat Toggles menu
        public static CompatToggleScreen create(Screen parent) {
            return new CompatToggleScreen(parent);
        }

        @Override
        protected void init() {
            // Store button bounds for tooltip detection
            immersiveMCButtonX = width / 2 - 80;
            immersiveMCButtonY = height / 6 - 10;
            immersiveMCButtonWidth = 160;
            immersiveMCButtonHeight = 20;

            throwConflictingButtonX = width / 2 - 80;
            throwConflictingButtonY = height / 6 + 11;
            throwConflictingButtonWidth = 160;
            throwConflictingButtonHeight = 20;

            // ImmersiveMC Throwables toggle
            addRenderableWidget(
                    new Button(immersiveMCButtonX, immersiveMCButtonY, immersiveMCButtonWidth, immersiveMCButtonHeight,
                            Component.literal("ImmersiveMC Throwables: " + (immersiveMCThrowablesValue ? "ON" : "OFF")),
                            btn -> {
                                immersiveMCThrowablesValue = !immersiveMCThrowablesValue;
                                btn.setMessage(Component.literal(
                                        "ImmersiveMC Throwables: " + (immersiveMCThrowablesValue ? "ON" : "OFF")));
                            }));

            // Throw Conflicting Items toggle
            addRenderableWidget(
                    new Button(throwConflictingButtonX, throwConflictingButtonY, throwConflictingButtonWidth, throwConflictingButtonHeight,
                            Component.literal("Throw Conflicting Items: " + (throwConflictingItemsValue ? "ON" : "OFF")),
                            btn -> {
                                throwConflictingItemsValue = !throwConflictingItemsValue;
                                btn.setMessage(Component.literal(
                                        "Throw Conflicting Items: " + (throwConflictingItemsValue ? "ON" : "OFF")));
                            }));

            // Done button
            addRenderableWidget(
                    new Button(width / 2 - 100, height - 30, 200, 20,
                            Component.literal("Done"),
                            btn -> {
                                ConfigHelper.CLIENT.immersiveMCThrowables = immersiveMCThrowablesValue;
                                ConfigHelper.CLIENT.throwConflictingItems = throwConflictingItemsValue;
                                ConfigHelper.write(ConfigHelper.CLIENT);

                                if (client.hasSingleplayerServer()) {
                                    ConfigHelper.ACTIVE.immersiveMCThrowables = ConfigHelper.CLIENT.immersiveMCThrowables;
                                    ConfigHelper.ACTIVE.throwConflictingItems = ConfigHelper.CLIENT.throwConflictingItems;
                                }
                                client.setScreen(parent);
                            }));
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
            renderBackground(poseStack);
            super.render(poseStack, mouseX, mouseY, delta);
            // Draw title at top
            drawCenteredString(poseStack, font, title, width / 2, 20, 0xFFFFFF);

            // Render tooltips based on mouse position
            renderTooltip(poseStack, mouseX, mouseY);
        }

        private void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
            // Check if mouse is over ImmersiveMC Throwables button
            if (isMouseOverButton(mouseX, mouseY, immersiveMCButtonX, immersiveMCButtonY, immersiveMCButtonWidth, immersiveMCButtonHeight)) {
                List<Component> tooltip = Arrays.asList(
                        Component.literal("ON: ImmersiveMC handles throwable projectiles"),
                        Component.literal("OFF: For when ImmersiveMC throwing is disabled,"),
                        Component.literal("allows VTE to throw vanilla projectiles")
                );
                renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
            }
            // Check if mouse is over Throw Conflicting Items button
            else if (isMouseOverButton(mouseX, mouseY, throwConflictingButtonX, throwConflictingButtonY, throwConflictingButtonWidth, throwConflictingButtonHeight)) {
                List<Component> tooltip = Arrays.asList(
                        Component.literal("ON: Allow throwing conflicting items"),
                        Component.literal("(Climbing Claws, bows, etc.) when crouched"),
                        Component.literal("and holding place/use keybind"),
                        Component.literal("OFF: Always block these items from being thrown")
                );
                renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
            }
        }

        private boolean isMouseOverButton(int mouseX, int mouseY, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
    }
}