package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import win.demistorm.ConfigHelper;

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
            // Place blocks on throw button (left side)
            Button placeBlocksButton = Button.builder(
                                Component.literal("Place Blocks on Throw: " + (placeBlocksOnThrowValue ? "ON" : "OFF")),
                                btn -> {
                                    placeBlocksOnThrowValue = !placeBlocksOnThrowValue;
                                    btn.setMessage(Component.literal(
                                            "Place Blocks on Throw: " + (placeBlocksOnThrowValue ? "ON" : "OFF")));

                                    // Update only lights button state
                                    updateOnlyLightsButton();

                                    // Update config immediately
                                    ConfigHelper.setPlaceBlocksOnThrowEnabled(placeBlocksOnThrowValue);
                                })
                        .bounds(width / 2 - 165, height / 6 - 10, 160, 20)
                        .tooltip(Tooltip.create(Component.literal("Place blocks when thrown into other blocks")))
                        .build();
            addRenderableWidget(placeBlocksButton);

            // Only place lights button (right side, initially disabled if place blocks is off)
            onlyLightsButton = Button.builder(
                                    Component.literal("Only Place Lights: " + (onlyPlaceLightsValue ? "ON" : "OFF")),
                                    btn -> {
                                        onlyPlaceLightsValue = !onlyPlaceLightsValue;
                                        btn.setMessage(Component.literal(
                                                "Only Place Lights: " + (onlyPlaceLightsValue ? "ON" : "OFF")));

                                        // Update config immediately
                                        ConfigHelper.setOnlyPlaceLightsEnabled(onlyPlaceLightsValue);
                                    })
                            .bounds(width / 2 + 5, height / 6 - 10, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Only place torches/lanterns when thrown")))
                            .build();
            onlyLightsButton.active = placeBlocksOnThrowValue;
            addRenderableWidget(onlyLightsButton);

            // Crouch behavior button for place blocks (below the side-by-side buttons)
            crouchBehaviorButton = Button.builder(
                                    Component.literal("Crouch Behavior: " + crouchBehaviorPlaceBlocksValue.name()),
                                    btn -> {
                                        // Switch between NORMAL and INVERTED
                                        crouchBehaviorPlaceBlocksValue = crouchBehaviorPlaceBlocksValue == ConfigHelper.CrouchBehavior.NORMAL
                                            ? ConfigHelper.CrouchBehavior.INVERTED
                                            : ConfigHelper.CrouchBehavior.NORMAL;
                                        btn.setMessage(Component.literal(
                                                "Crouch Behavior: " + crouchBehaviorPlaceBlocksValue.name()));
                                        ConfigHelper.setCrouchBehaviorPlaceBlocks(crouchBehaviorPlaceBlocksValue);
                                    })
                            .bounds(width / 2 - 165, height / 6 + 11, 160, 20)
                            .tooltip(Tooltip.create(Component.literal(
                                    """
                                        NORMAL: Crouch throws projectile without effect
                                        INVERTED: Crouch activates effect""")))
                            .build();
            crouchBehaviorButton.active = placeBlocksOnThrowValue;
            addRenderableWidget(crouchBehaviorButton);

            // Blood particles toggle
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Blood Particles: " + (bloodEffectValue ? "ON" : "OFF")),
                                    btn -> {
                                        bloodEffectValue = !bloodEffectValue;
                                        btn.setMessage(Component.literal(
                                                "Blood Particles: " + (bloodEffectValue ? "ON" : "OFF")));
                                    })
                            .bounds(width / 2 - 80, height / 6 + 32, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Show blood particles when weapons hit")))
                            .build());

            // Throwable TNT toggle
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Throwable TNT: " + (throwableTNTValue ? "ON" : "OFF")),
                                    btn -> {
                                        throwableTNTValue = !throwableTNTValue;
                                        btn.setMessage(Component.literal(
                                                "Throwable TNT: " + (throwableTNTValue ? "ON" : "OFF")));
                                        ConfigHelper.setThrowableTNTEnabled(throwableTNTValue);
                                    })
                            .bounds(width / 2 - 80, height / 6 + 53, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Enable/disable immersive TNT throwing")))
                            .build());

            // Compat toggles button
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Compat Toggles..."),
                                    btn -> client.setScreen(new CompatScreen.CompatToggleScreen(this)))
                            .bounds(width / 2 - 80, height / 6 + 74, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Mod Compatibility Settings")))
                            .build());

            // Done button
            addRenderableWidget(
                    Button.builder(Component.literal("Done"),
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
                                    })
                            .bounds(width / 2 - 100, height - 30, 200, 20)
                            .build());
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
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            // Draw title at top
            context.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        }
    }
}