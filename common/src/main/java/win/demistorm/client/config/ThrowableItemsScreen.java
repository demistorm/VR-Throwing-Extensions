package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import win.demistorm.ConfigHelper;
import win.demistorm.effects.ProjectileEffect;

import java.util.Arrays;
import java.util.List;

// Screen for managing throwable projectile items
public class ThrowableItemsScreen extends Screen {
    private final Screen parent;
    private final Minecraft client = Minecraft.getInstance();

    // UI Components
    private EditBox itemIdInput;
    private ItemListWidget itemList;
    private Button toggleButton;

    // Layout
    private int listTopY;

    // Data
    private List<String> projectileItems;
    private boolean throwableProjectilesEnabled;
    private ConfigHelper.CrouchBehavior crouchBehaviorValue = ConfigHelper.CLIENT.crouchBehaviorProjectiles;

    // Button bounds for tooltip detection
    private int toggleButtonX, toggleButtonY, toggleButtonWidth, toggleButtonHeight;
    private int crouchBehaviorButtonX, crouchBehaviorButtonY, crouchBehaviorButtonWidth, crouchBehaviorButtonHeight;

    protected ThrowableItemsScreen(Screen parent) {
        super(Component.literal("Configure Throwable Projectiles"));
        this.parent = parent;

        // Load current data
        this.projectileItems = ProjectileEffect.getProjectileItemsList();
        this.throwableProjectilesEnabled = ConfigHelper.CLIENT.throwableProjectiles;
    }

    @Override
    protected void init() {
        int topY = 40;
        int listTopY = topY + 80; // Start list right after controls
        int bottomMargin = 70; // Space at bottom for Done button (prevents overlap)

        // Store button bounds for tooltip detection
        toggleButtonX = width - 180;
        toggleButtonY = topY;
        toggleButtonWidth = 160;
        toggleButtonHeight = 18;

        crouchBehaviorButtonX = width - 180;
        crouchBehaviorButtonY = topY + 23;
        crouchBehaviorButtonWidth = 160;
        crouchBehaviorButtonHeight = 18;

        // Text input for new items (left side)
        itemIdInput = new EditBox(
                font,
                20,
                topY,
                180,
                18,
                Component.literal("Item ID")
        );
        itemIdInput.setSuggestion("minecraft:snowball");
        itemIdInput.setMaxLength(100);
        addRenderableWidget(itemIdInput);

        // Add button (next to text input)
        addRenderableWidget(
                new Button(205, topY, 40, 18,
                        Component.literal("Add"),
                        btn -> addItemId()));

        // Throwable Projectiles toggle (right side)
        toggleButton = new Button(toggleButtonX, toggleButtonY, toggleButtonWidth, toggleButtonHeight,
                Component.literal("Throwable Projectiles: " + (throwableProjectilesEnabled ? "ON" : "OFF")),
                btn -> {
                    throwableProjectilesEnabled = !throwableProjectilesEnabled;
                    btn.setMessage(Component.literal("Throwable Projectiles: " + (throwableProjectilesEnabled ? "ON" : "OFF")));
                    // Update config immediately
                    ConfigHelper.setThrowableProjectilesEnabled(throwableProjectilesEnabled);
                });
        addRenderableWidget(toggleButton);

        // Crouch behavior toggle (below Throwable Projectiles toggle)
        addRenderableWidget(
                new Button(crouchBehaviorButtonX, crouchBehaviorButtonY, crouchBehaviorButtonWidth, crouchBehaviorButtonHeight,
                        Component.literal("Crouch Behavior: " + crouchBehaviorValue.name()),
                        btn -> {
                            // Switch between NORMAL and INVERTED
                            crouchBehaviorValue = crouchBehaviorValue == ConfigHelper.CrouchBehavior.NORMAL
                                    ? ConfigHelper.CrouchBehavior.INVERTED
                                    : ConfigHelper.CrouchBehavior.NORMAL;
                            btn.setMessage(Component.literal("Crouch Behavior: " + crouchBehaviorValue.name()));
                            ConfigHelper.setCrouchBehaviorProjectiles(crouchBehaviorValue);
                        }));

        // Create scrollable item list (two columns) - starts below the controls
        int listBottom = height - bottomMargin;
        itemList = new ItemListWidget(client, width, height, listTopY, listBottom);
        itemList.updateEntries();
        addWidget(itemList);

        // Done button at bottom
        addRenderableWidget(
                new Button(width / 2 - 100, height - 30, 200, 20,
                        Component.literal("Done"),
                        btn -> {
                            // Save crouch behavior
                            ConfigHelper.CLIENT.crouchBehaviorProjectiles = crouchBehaviorValue;
                            ConfigHelper.write(ConfigHelper.CLIENT);

                            // Save any changes to projectile items
                            ProjectileEffect.setProjectileItemsList(projectileItems);
                            client.setScreen(parent);
                        }));
    }

    private void addItemId() {
        String text = itemIdInput.getValue().trim();
        if (!text.isEmpty() && !projectileItems.contains(text)) {
            projectileItems.add(text);
            itemIdInput.setValue("");
            itemList.updateEntries();

            // Save immediately
            ProjectileEffect.setProjectileItemsList(projectileItems);
        }
    }

    private void removeItemId(String itemId) {
        projectileItems.remove(itemId);
        itemList.updateEntries();

        // Save immediately
        ProjectileEffect.setProjectileItemsList(projectileItems);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        renderBackground(poseStack);

        // Render the item list after background and other widgets
        if (itemList != null) {
            itemList.render(poseStack, mouseX, mouseY, delta);
        }

        super.render(poseStack, mouseX, mouseY, delta);

        // Title at top
        drawCenteredString(poseStack, font, title, width / 2, 10, 0xFFFFFF);

        // Label for text input
        drawString(poseStack, font, "Add Item ID:", 20, 28, 0xFFFFFF);

        // Label for item list
        drawString(poseStack, font, "Custom Projectile Items:", 20, listTopY + 105, 0xFFFFFF);

        // Show count of items
        drawString(poseStack, font, "(" + projectileItems.size() + " items)", 180, listTopY + 105, 0xAAAAAA);

        // Render tooltips based on mouse position
        renderTooltip(poseStack, mouseX, mouseY);
    }

    private void renderTooltip(PoseStack poseStack, int mouseX, int mouseY) {
        // Check if mouse is over throwable projectiles toggle button
        if (isMouseOverButton(mouseX, mouseY, toggleButtonX, toggleButtonY, toggleButtonWidth, toggleButtonHeight)) {
            renderTooltip(poseStack, Component.literal("Enable VR control over vanilla projectile items"), mouseX, mouseY);
        }
        // Check if mouse is over crouch behavior button
        else if (isMouseOverButton(mouseX, mouseY, crouchBehaviorButtonX, crouchBehaviorButtonY, crouchBehaviorButtonWidth, crouchBehaviorButtonHeight)) {
            List<Component> tooltip = Arrays.asList(
                    Component.literal("NORMAL: Crouch throws projectile without effect"),
                    Component.literal("INVERTED: Crouch activates effect")
            );
            renderComponentTooltip(poseStack, tooltip, mouseX, mouseY);
        }
    }

    private boolean isMouseOverButton(int mouseX, int mouseY, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let the screen handle mouse events normally
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle Enter key in text field
        if (itemIdInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            addItemId();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Custom list widget for displaying items in two columns
    private class ItemListWidget extends ObjectSelectionList<ItemListWidget.ItemEntry> {

        public ItemListWidget(Minecraft client, int width, int height, int y, int bottom) {
            super(client, width, height, y, bottom, 20);
        }

        public void updateEntries() {
            clearEntries();

            // Add items in pairs for two-column layout
            for (int i = 0; i < projectileItems.size(); i += 2) {
                String leftItem = projectileItems.get(i);
                String rightItem = i + 1 < projectileItems.size() ? projectileItems.get(i + 1) : null;
                addEntry(new ItemEntry(leftItem, rightItem));
            }
        }

        @Override
        public int getRowWidth() {
            return width - 60;
        }

        // Entry representing a row with two items
        public class ItemEntry extends ObjectSelectionList.Entry<ItemEntry> {
            private final String leftItem;
            private final String rightItem;
            private final Button leftRemoveButton;
            private final Button rightRemoveButton;

            // Button bounds for tooltip detection
            private int leftButtonX, leftButtonY;
            private int rightButtonX, rightButtonY;
            private static final int BUTTON_SIZE = 16;

            public ItemEntry(String leftItem, String rightItem) {
                this.leftItem = leftItem;
                this.rightItem = rightItem;

                // Create remove buttons for each item
                this.leftRemoveButton = new Button(0, 0, BUTTON_SIZE, BUTTON_SIZE,
                        Component.literal("×"),
                        btn -> removeItemId(leftItem));

                this.rightRemoveButton = rightItem != null ? new Button(0, 0, BUTTON_SIZE, BUTTON_SIZE,
                        Component.literal("×"),
                        btn -> removeItemId(rightItem)) : null;
            }

            @Override
            public void render(PoseStack poseStack, int index, int y, int x, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float delta) {
                int columnWidth = width / 2;

                // Left column
                String leftDisplay = leftItem;
                int leftTextWidth = font.width(leftDisplay);
                if (leftTextWidth > columnWidth - 25) {
                    // Truncate if too long
                    leftDisplay = font.plainSubstrByWidth(leftItem, columnWidth - 30) + "...";
                }
                drawString(poseStack, font, leftDisplay, x + 5, y + 4, 0xFFFFFF);

                // Position and render left remove button
                leftButtonX = x + columnWidth - 20;
                leftButtonY = y + 1;
                leftRemoveButton.x = leftButtonX;
                leftRemoveButton.y = leftButtonY;
                leftRemoveButton.render(poseStack, mouseX, mouseY, delta);

                // Right column (if exists)
                if (rightItem != null) {
                    String rightDisplay = rightItem;
                    int rightTextWidth = font.width(rightDisplay);
                    if (rightTextWidth > columnWidth - 25) {
                        rightDisplay = font.plainSubstrByWidth(rightItem, columnWidth - 30) + "...";
                    }
                    drawString(poseStack, font, rightDisplay, x + columnWidth + 5, y + 4, 0xFFFFFF);
                    rightButtonX = x + width - 20;
                    rightButtonY = y + 1;
                    rightRemoveButton.x = rightButtonX;
                    rightRemoveButton.y = rightButtonY;
                    rightRemoveButton.render(poseStack, mouseX, mouseY, delta);
                }

                // Render tooltips for remove buttons
                if (isMouseOverButton(mouseX, mouseY, leftButtonX, leftButtonY, BUTTON_SIZE, BUTTON_SIZE)) {
                    ThrowableItemsScreen.this.renderTooltip(poseStack, Component.literal("Remove " + leftItem), mouseX, mouseY);
                } else if (rightItem != null && isMouseOverButton(mouseX, mouseY, rightButtonX, rightButtonY, BUTTON_SIZE, BUTTON_SIZE)) {
                    ThrowableItemsScreen.this.renderTooltip(poseStack, Component.literal("Remove " + rightItem), mouseX, mouseY);
                }
            }

            private boolean isMouseOverButton(int mouseX, int mouseY, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
                return mouseX >= buttonX && mouseX <= buttonX + buttonWidth &&
                        mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (leftRemoveButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (rightRemoveButton != null && rightRemoveButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return Component.literal(leftItem + (rightItem != null ? " and " + rightItem : ""));
            }
        }
    }
}