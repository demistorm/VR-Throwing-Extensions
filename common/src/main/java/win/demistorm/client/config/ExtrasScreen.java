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
        private Button onlyLightsButton;

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
            // Place blocks on throw button
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
                        .bounds(width / 2 - 80, height / 4 + 24, 160, 20)
                        .tooltip(Tooltip.create(Component.literal("Place blocks when thrown into other blocks")))
                        .build();
            addRenderableWidget(placeBlocksButton);

            // Only place lights button (initially disabled if place blocks is off)
            onlyLightsButton = Button.builder(
                                    Component.literal("Only Place Lights: " + (onlyPlaceLightsValue ? "ON" : "OFF")),
                                    btn -> {
                                        onlyPlaceLightsValue = !onlyPlaceLightsValue;
                                        btn.setMessage(Component.literal(
                                                "Only Place Lights: " + (onlyPlaceLightsValue ? "ON" : "OFF")));

                                        // Update config immediately
                                        ConfigHelper.setOnlyPlaceLightsEnabled(onlyPlaceLightsValue);
                                    })
                            .bounds(width / 2 - 80, height / 4 + 54, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Only place torches/lanterns when thrown")))
                            .build();
            onlyLightsButton.active = placeBlocksOnThrowValue;
            addRenderableWidget(onlyLightsButton);

            // Done button
            addRenderableWidget(
                    Button.builder(Component.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.placeBlocksOnThrow = placeBlocksOnThrowValue;
                                        ConfigHelper.CLIENT.onlyPlaceLights = onlyPlaceLightsValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT);

                                        if (client.hasSingleplayerServer()) {
                                            ConfigHelper.ACTIVE.placeBlocksOnThrow = ConfigHelper.CLIENT.placeBlocksOnThrow;
                                            ConfigHelper.ACTIVE.onlyPlaceLights = ConfigHelper.CLIENT.onlyPlaceLights;
                                        }
                                        client.setScreen(parent);
                                    })
                            .bounds(width / 2 - 50, height - 50, 100, 20)
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
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);
            // Draw title at top
            context.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        }
    }
}