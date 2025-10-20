package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import win.demistorm.ConfigHelper;
import win.demistorm.WeaponEffectType;

// Settings menu
public final class ConfigScreen {

    private ConfigScreen() {}

    public static class SimpleToggleScreen extends Screen {
        private final Screen parent;
        private final Minecraft client = Minecraft.getInstance();
        private WeaponEffectType weaponEffectValue = ConfigHelper.CLIENT.weaponEffect;
        private boolean aimAssistValue = ConfigHelper.CLIENT.aimAssist;
        private boolean bloodEffectValue = ClientOnlyConfig.ACTIVE.bloodEffect;

        protected SimpleToggleScreen(Screen parent) {
            super(Component.literal("VR Throwing Extensions Configuration"));
            this.parent = parent;
        }

        // Create screen for ModMenu
        public static SimpleToggleScreen create(Screen parent) {
            return new SimpleToggleScreen(parent);
        }

        @Override
        protected void init() {
            // Weapon effect button
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Weapon Effect: " + weaponEffectValue.name()),
                                    btn -> {
                                        // Switch between effects
                                        weaponEffectValue = switch (weaponEffectValue) {
                                            case OFF -> WeaponEffectType.BOOMERANG;
                                            case BOOMERANG -> WeaponEffectType.EMBED;
                                            case EMBED -> WeaponEffectType.OFF;
                                        };
                                        btn.setMessage(Component.literal("Weapon Effect: " + weaponEffectValue.name()));
                                    })
                            .bounds(width / 2 - 80, height / 4 + 24, 160, 20)
                            .tooltip(Tooltip.create(Component.literal(
                                    """
                                            OFF: Weapons drop normally
                                            BOOMERANG: Weapons return after hitting (catch them!)
                                            EMBED: Weapons stick in enemies and cause bleeding""")))
                            .build());

            // Aim assist button
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")),
                                    btn -> {
                                        aimAssistValue = !aimAssistValue;
                                        btn.setMessage(Component.literal(
                                                "Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                                    })
                            .bounds(width / 2 - 80, height / 4 + 54, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Helps aim at nearby targets")))
                            .build());

            // Blood effects button
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Blood Effects: " + (bloodEffectValue ? "ON" : "OFF")),
                                    btn -> {
                                        bloodEffectValue = !bloodEffectValue;
                                        btn.setMessage(Component.literal("Blood Effects: " + (bloodEffectValue ? "ON" : "OFF")));
                                    })
                            .bounds(width / 2 - 80, height / 4 + 84, 160, 20)
                            .tooltip(Tooltip.create(Component.literal("Show blood particles when weapons hit")))
                            .build());

            // Done button
            addRenderableWidget(
                    Button.builder(Component.literal("Done"),
                                    btn -> {
                                        ConfigHelper.CLIENT.weaponEffect = weaponEffectValue;
                                        ConfigHelper.CLIENT.aimAssist = aimAssistValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT);

                                        // Save blood effect setting
                                        ClientOnlyConfig.ACTIVE.bloodEffect = bloodEffectValue;
                                        ClientOnlyConfig.write(ClientOnlyConfig.ACTIVE);

                                        if (client.hasSingleplayerServer()) {
                                            ConfigHelper.ACTIVE.weaponEffect = ConfigHelper.CLIENT.weaponEffect;
                                            ConfigHelper.ACTIVE.aimAssist = ConfigHelper.CLIENT.aimAssist;
                                        }
                                        client.setScreen(parent);
                                    })
                            .bounds(width / 2 - 100, height - 27, 200, 20)
                            .build());
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
            // Draw title at top
            context.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        }
    }
}