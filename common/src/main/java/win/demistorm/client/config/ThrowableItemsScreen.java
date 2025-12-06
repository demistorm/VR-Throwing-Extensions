package win.demistorm.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import win.demistorm.ConfigHelper;
import win.demistorm.effects.ProjectileEffect;

import java.util.ArrayList;
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
        int listTopY = topY + 60; // More space between controls and list
        int bottomMargin = 70; // Space at bottom for Done button

        // Text input for new items (left side)
        itemIdInput = new EditBox(
            font,
            20,
            topY,
            180,
            18,
            Component.literal("Item ID")
        );
        itemIdInput.setHint(Component.literal("minecraft:snowball"));
        itemIdInput.setMaxLength(100);
        addRenderableWidget(itemIdInput);

        // Add button (next to text input)
        addRenderableWidget(
            Button.builder(
                Component.literal("Add"),
                btn -> addItemId())
            .bounds(205, topY, 40, 18)
            .build());

        // Throwable Projectiles toggle (right side)
        toggleButton = Button.builder(
            Component.literal("Throwable Projectiles: " + (throwableProjectilesEnabled ? "ON" : "OFF")),
            btn -> {
                throwableProjectilesEnabled = !throwableProjectilesEnabled;
                btn.setMessage(Component.literal("Throwable Projectiles: " + (throwableProjectilesEnabled ? "ON" : "OFF")));
                // Update config immediately
                ConfigHelper.setThrowableProjectilesEnabled(throwableProjectilesEnabled);
            })
        .bounds(width - 180, topY, 160, 18)
        .tooltip(Tooltip.create(Component.literal("Enable VR control over vanilla projectile items")))
        .build();
        addRenderableWidget(toggleButton);

        // Create scrollable item list (two columns) - starts below the controls
        int listBottom = height - bottomMargin;
        itemList = new ItemListWidget(client, width, height, listTopY, listBottom);
        itemList.updateEntries();
        addWidget(itemList);

        // Done button at bottom
        addRenderableWidget(
            Button.builder(
                Component.literal("Done"),
                btn -> {
                    // Save any changes to projectile items
                    ProjectileEffect.setProjectileItemsList(projectileItems);
                    client.setScreen(parent);
                })
            .bounds(width / 2 - 50, height - 50, 100, 20)
            .build());
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Render the item list after background and other widgets
        if (itemList != null) {
            itemList.render(context, mouseX, mouseY, delta);
        }

        // Title at top
        context.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        // Label for text input
        context.drawString(font, "Add Item ID:", 20, 28, 0xFFFFFF);

        // Label for item list
        context.drawString(font, "Custom Projectile Items:", 20, listTopY - 10, 0xFFFFFF);

        // Show count of items
        context.drawString(font, "(" + projectileItems.size() + " items)", 180, listTopY - 10, 0xAAAAAA);
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
            super(client, width, bottom - y, y, 20);
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

            public ItemEntry(String leftItem, String rightItem) {
                this.leftItem = leftItem;
                this.rightItem = rightItem;

                // Create remove buttons for each item
                this.leftRemoveButton = Button.builder(
                    Component.literal("×"),
                    btn -> removeItemId(leftItem))
                .bounds(0, 0, 16, 16)
                .tooltip(Tooltip.create(Component.literal("Remove " + leftItem)))
                .build();

                this.rightRemoveButton = rightItem != null ? Button.builder(
                    Component.literal("×"),
                    btn -> removeItemId(rightItem))
                .bounds(0, 0, 16, 16)
                .tooltip(Tooltip.create(Component.literal("Remove " + rightItem)))
                .build() : null;
            }

            @Override
            public void render(GuiGraphics context, int index, int y, int x, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float delta) {
                int columnWidth = width / 2;

                // Left column
                String leftDisplay = leftItem;
                int leftTextWidth = font.width(leftDisplay);
                if (leftTextWidth > columnWidth - 25) {
                    // Truncate if too long
                    leftDisplay = font.plainSubstrByWidth(leftItem, columnWidth - 30) + "...";
                }
                context.drawString(font, leftDisplay, x + 5, y + 4, 0xFFFFFF);

                // Position and render left remove button
                leftRemoveButton.setPosition(x + columnWidth - 20, y + 1);
                leftRemoveButton.render(context, mouseX, mouseY, delta);

                // Right column (if exists)
                if (rightItem != null) {
                    String rightDisplay = rightItem;
                    int rightTextWidth = font.width(rightDisplay);
                    if (rightTextWidth > columnWidth - 25) {
                        rightDisplay = font.plainSubstrByWidth(rightItem, columnWidth - 30) + "...";
                    }
                    context.drawString(font, rightDisplay, x + columnWidth + 5, y + 4, 0xFFFFFF);
                    rightRemoveButton.setPosition(x + width - 20, y + 1);
                    rightRemoveButton.render(context, mouseX, mouseY, delta);
                }
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