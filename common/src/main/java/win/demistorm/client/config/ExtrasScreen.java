package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import win.demistorm.ConfigHelper;

import java.util.Arrays;
import java.util.List;

// Extras configuration menu
public final class ExtrasScreen {

    private ExtrasScreen() {}

    public static class ExtrasToggleScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private boolean placeBlocksOnThrowValue = ConfigHelper.CLIENT.placeBlocksOnThrow;
        private boolean onlyPlaceLightsValue = ConfigHelper.CLIENT.onlyPlaceLights;
        private boolean bloodEffectValue = ClientOnlyConfig.ACTIVE.bloodEffect;
        private boolean throwableTNTValue = ConfigHelper.CLIENT.throwableTNT;
        private ConfigHelper.CrouchBehavior crouchBehaviorPlaceBlocksValue = ConfigHelper.CLIENT.crouchBehaviorPlaceBlocks;
        private Button onlyLightsButton;
        private Button crouchBehaviorButton;

        // Button bounds for tooltip detection
        private int placeBlocksButtonX, placeBlocksButtonY, placeBlocksButtonWidth, placeBlocksButtonHeight;
        private int onlyLightsButtonX, onlyLightsButtonY, onlyLightsButtonWidth, onlyLightsButtonHeight;
        private int crouchBehaviorButtonX, crouchBehaviorButtonY, crouchBehaviorButtonWidth, crouchBehaviorButtonHeight;
        private int bloodParticlesButtonX, bloodParticlesButtonY, bloodParticlesButtonWidth, bloodParticlesButtonHeight;
        private int throwableTNTButtonX, throwableTNTButtonY, throwableTNTButtonWidth, throwableTNTButtonHeight;
        private int compatTogglesButtonX, compatTogglesButtonY, compatTogglesButtonWidth, compatTogglesButtonHeight;

        protected ExtrasToggleScreen(Screen parent) {
            super(Component.literal("VR Throwing Extensions - Extras"));
            this.parent = parent;
        }

        // Create screen for Extras menu
        public static ExtrasToggleScreen create(Screen parent) {
            return new ExtrasToggleScreen(parent);
        }

        @Override
        protected void init() {
            // Store button bounds for tooltip detection
            placeBlocksButtonX = width / 2 - 165;
            placeBlocksButtonY = height / 6 - 10;
            placeBlocksButtonWidth = 160;
            placeBlocksButtonHeight = 20;

            onlyLightsButtonX = width / 2 + 5;
            onlyLightsButtonY = height / 6 - 10;
            onlyLightsButtonWidth = 160;
            onlyLightsButtonHeight = 20;

            crouchBehaviorButtonX = width / 2 - 165;
            crouchBehaviorButtonY = height / 6 + 11;
            crouchBehaviorButtonWidth = 160;
            crouchBehaviorButtonHeight = 20;

            bloodParticlesButtonX = width / 2 - 80;
            bloodParticlesButtonY = height / 6 + 32;
            bloodParticlesButtonWidth = 160;
            bloodParticlesButtonHeight = 20;

            throwableTNTButtonX = width / 2 - 80;
            throwableTNTButtonY = height / 6 + 53;
            throwableTNTButtonWidth = 160;
            throwableTNTButtonHeight = 20;

            compatTogglesButtonX = width / 2 - 80;
            compatTogglesButtonY = height / 6 + 74;
            compatTogglesButtonWidth = 160;
            compatTogglesButtonHeight = 20;

            // Place blocks on throw button (left side)
            Button placeBlocksButton = new Button(placeBlocksButtonX, placeBlocksButtonY, placeBlocksButtonWidth, placeBlocksButtonHeight,
                    Component.literal("Place Blocks on Throw: " + (placeBlocksOnThrowValue ? "ON" : "OFF")),
                    btn -> {
                        placeBlocksOnThrowValue = !placeBlocksOnThrowValue;
                        btn.setMessage(Component.literal(
                                "Place Blocks on Throw: " + (placeBlocksOnThrowValue ? "ON" : "OFF")));

                        // Update only lights button state
                        updateOnlyLightsButton();

                        // Update config immediately
                        ConfigHelper.setPlaceBlocksOnThrowEnabled(placeBlocksOnThrowValue);
                    });
            addRenderableWidget(placeBlocksButton);

            // Only place lights button (right side, initially disabled if place blocks is off)
            onlyLightsButton = new Button(onlyLightsButtonX, onlyLightsButtonY, onlyLightsButtonWidth, onlyLightsButtonHeight,
                    Component.literal("Only Place Lights: " + (onlyPlaceLightsValue ? "ON" : "OFF")),
                    btn -> {
                        onlyPlaceLightsValue = !onlyPlaceLightsValue;
                        btn.setMessage(Component.literal(
                                "Only Place Lights: " + (onlyPlaceLightsValue ? "ON" : "OFF")));

                        // Update config immediately
                        ConfigHelper.setOnlyPlaceLightsEnabled(onlyPlaceLightsValue);
                    });
            onlyLightsButton.active = placeBlocksOnThrowValue;
            addRenderableWidget(onlyLightsButton);

            // Crouch behavior button for place blocks (below the side-by-side buttons)
            crouchBehaviorButton = new Button(crouchBehaviorButtonX, crouchBehaviorButtonY, crouchBehaviorButtonWidth, crouchBehaviorButtonHeight,
                    Component.literal("Crouch Behavior: " + crouchBehaviorPlaceBlocksValue.name()),
                    btn -> {
                        // Switch between NORMAL and INVERTED
                        crouchBehaviorPlaceBlocksValue = crouchBehaviorPlaceBlocksValue == ConfigHelper.CrouchBehavior.NORMAL
                                ? ConfigHelper.CrouchBehavior.INVERTED
                                : ConfigHelper.CrouchBehavior.NORMAL;
                        btn.setMessage(Component.literal(
                                "Crouch Behavior: " + crouchBehaviorPlaceBlocksValue.name()));
                        ConfigHelper.setCrouchBehaviorPlaceBlocks(crouchBehaviorPlaceBlocksValue);
                    });
            crouchBehaviorButton.active = placeBlocksOnThrowValue;
            addRenderableWidget(crouchBehaviorButton);

            // Blood particles toggle
            addRenderableWidget(
                    new Button(bloodParticlesButtonX, bloodParticlesButtonY, bloodParticlesButtonWidth, bloodParticlesButtonHeight,
                            Component.literal("Blood Particles: " + (bloodEffectValue ? "ON" : "OFF")),
                            btn -> {
                                bloodEffectValue = !bloodEffectValue;
                                btn.setMessage(Component.literal(
                                        "Blood Particles: " + (bloodEffectValue ? "ON" : "OFF")));
                            }));

            // Throwable TNT toggle
            addRenderableWidget(
                    new Button(throwableTNTButtonX, throwableTNTButtonY, throwableTNTButtonWidth, throwableTNTButtonHeight,
                            Component.literal("Throwable TNT: " + (throwableTNTValue ? "ON" : "OFF")),
                            btn -> {
                                throwableTNTValue = !throwableTNTValue;
                                btn.setMessage(Component.literal(
                                        "Throwable TNT: " + (throwableTNTValue ? "ON" : "OFF")));
                                ConfigHelper.setThrowableTNTEnabled(throwableTNTValue);
                            }));

            // Compat toggles button
            addRenderableWidget(
                    new Button(compatTogglesButtonX, compatTogglesButtonY, compatTogglesButtonWidth, compatTogglesButtonHeight,
                            Component.literal("Compat Toggles..."),
                            btn -> client.setScreen(new CompatScreen.CompatToggleScreen(this))));

            // Done button
            addRenderableWidget(
                    new Button(width / 2 - 100, height - 30, 200, 20,
                            Component.literal("Done"),
                            btn -> {
                                ConfigHelper.CLIENT.placeBlocksOnThrow = placeBlocksOnThrowValue;
                                ConfigHelper.CLIENT.onlyPlaceLights = onlyPlaceLightsValue;
                                ConfigHelper.CLIENT.crouchBehaviorPlaceBlocks = crouchBehaviorPlaceBlocksValue;
                                ConfigHelper.write(ConfigHelper.CLIENT);

                                // Save blood effect setting
                                ClientOnlyConfig.ACTIVE.bloodEffect = bloodEffectValue;
                                ClientOnlyConfig.write(ClientOnlyConfig.ACTIVE);

                                if (client.hasSingleplayerServer()) {
                                    ConfigHelper.ACTIVE.placeBlocksOnThrow = ConfigHelper.CLIENT.placeBlocksOnThrow;
                                    ConfigHelper.ACTIVE.onlyPlaceLights = ConfigHelper.CLIENT.onlyPlaceLights;
                                    ConfigHelper.ACTIVE.crouchBehaviorPlaceBlocks = ConfigHelper.CLIENT.crouchBehaviorPlaceBlocks;
                                }
                                client.setScreen(parent);
                            }));
        }

        // Update the state of the only lights button when place blocks setting changes
        private void updateOnlyLightsButton() {
            if (onlyLightsButton != null) {
                onlyLightsButton.active = placeBlocksOnThrowValue;
                if (!placeBlocksOnThrowValue) {
                    // Turn off only lights when place blocks is turned off
                    onlyPlaceLightsValue = false;
                    onlyLightsButton.setMessage(Component.literal("Only Place Lights: OFF"));
                    ConfigHelper.setOnlyPlaceLightsEnabled(false);
                }
            }

            if (crouchBehaviorButton != null) {
                crouchBehaviorButton.active = placeBlocksOnThrowValue;
            }
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
            // Check if mouse is over place blocks button
            if (isMouseOverButton(mouseX, mouseY, placeBlocksButtonX, placeBlocksButtonY, placeBlocksButtonWidth, placeBlocksButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Place blocks when thrown into other blocks"), mouseX, mouseY);
            }
            // Check if mouse is over only lights button
            else if (isMouseOverButton(mouseX, mouseY, onlyLightsButtonX, onlyLightsButtonY, onlyLightsButtonWidth, onlyLightsButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Only place torches/lanterns when thrown"), mouseX, mouseY);
            }
            // Check if mouse is over crouch behavior button
            else if (isMouseOverButton(mouseX, mouseY, crouchBehaviorButtonX, crouchBehaviorButtonY, crouchBehaviorButtonWidth, crouchBehaviorButtonHeight)) {
                List<Component> tooltip = Arrays.asList(
                        Component.literal("NORMAL: Crouch throws projectile without effect"),
                        Component.literal("INVERTED: Crouch activates effect")
                );
                renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
            }
            // Check if mouse is over blood particles button
            else if (isMouseOverButton(mouseX, mouseY, bloodParticlesButtonX, bloodParticlesButtonY, bloodParticlesButtonWidth, bloodParticlesButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Show blood particles when weapons hit"), mouseX, mouseY);
            }
            // Check if mouse is over throwable TNT button
            else if (isMouseOverButton(mouseX, mouseY, throwableTNTButtonX, throwableTNTButtonY, throwableTNTButtonWidth, throwableTNTButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Enable/disable immersive TNT throwing"), mouseX, mouseY);
            }
            // Check if mouse is over compat toggles button
            else if (isMouseOverButton(mouseX, mouseY, compatTogglesButtonX, compatTogglesButtonY, compatTogglesButtonWidth, compatTogglesButtonHeight)) {
                renderTooltip(poseStack, Component.literal("Mod Compatibility Settings"), mouseX, mouseY);
            }
        }

        private boolean isMouseOverButton(int mouseX, int mouseY, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
            return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        }
    }
}