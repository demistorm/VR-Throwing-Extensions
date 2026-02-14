package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import win.demistorm.ConfigHelper;
import win.demistorm.client.VRThrowingExtensionsClient;

// Mod compatibility configuration menu
public final class CompatScreen {

    private CompatScreen() {}

    public static class CompatToggleScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private boolean immersiveMCThrowablesValue = ConfigHelper.CLIENT.immersiveMCThrowables;
        private boolean throwConflictingItemsValue = ConfigHelper.CLIENT.throwConflictingItems;

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
            // ImmersiveMC Throwables toggle
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("ImmersiveMC Throwables: " + (immersiveMCThrowablesValue ? "ON" : "OFF")),
                                    btn -> {
                                        immersiveMCThrowablesValue = !immersiveMCThrowablesValue;
                                        btn.setMessage(Component.literal(
                                                "ImmersiveMC Throwables: " + (immersiveMCThrowablesValue ? "ON" : "OFF")));
                                    })
                            .bounds(width / 2 - 80, height / 6 - 10, 160, 20)
                            .tooltip(Tooltip.create(Component.literal(
                                    """
                                        ON: ImmersiveMC handles throwable projectiles
                                        OFF: For when ImmersiveMC throwing is disabled, allows VTE to throw vanilla projectiles
                                        """)))
                            .build());

            // Throw Conflicting Items toggle
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Throw Conflicting Items: " + (throwConflictingItemsValue ? "ON" : "OFF")),
                                    btn -> {
                                        throwConflictingItemsValue = !throwConflictingItemsValue;
                                        btn.setMessage(Component.literal(
                                                "Throw Conflicting Items: " + (throwConflictingItemsValue ? "ON" : "OFF")));
                                    })
                            .bounds(width / 2 - 80, height / 6 + 11, 160, 20)
                            .tooltip(Tooltip.create(Component.literal(
                                    """
                                        ON: Allow throwing conflicting items (Climbing Claws, bows, etc.) when crouched and holding place/use keybind
                                        OFF: Always block these items from being thrown
                                        """)))
                            .build());

            // Done button
            addRenderableWidget(
                    Button.builder(Component.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.immersiveMCThrowables = immersiveMCThrowablesValue;
                                        ConfigHelper.CLIENT.throwConflictingItems = throwConflictingItemsValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT);

                                        if (client.hasSingleplayerServer()) {
                                            ConfigHelper.ACTIVE.immersiveMCThrowables = ConfigHelper.CLIENT.immersiveMCThrowables;
                                            ConfigHelper.ACTIVE.throwConflictingItems = ConfigHelper.CLIENT.throwConflictingItems;
                                        } else {
                                            // Send config to server for non-authoritative mode
                                            VRThrowingExtensionsClient.sendPlayerConfigToServer();
                                        }
                                        client.setScreen(parent);
                                    })
                            .bounds(width / 2 - 100, height - 30, 200, 20)
                            .build());
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            // Draw title at top
            context.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        }
    }
}
