package win.demistorm.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;

// Custom key mappings
public class RemapBindings {

    //? if >=1.21.11 {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("vr_throwing_extensions", "category")
    );
    //?} else {
    /*public static final String CATEGORY = "key.categories.vr_throwing_extensions";*/
    //?}

    // Throw/catch (main hand)
    public static final KeyMapping THROW = new KeyMapping(
            "key.vr_throwing_extensions.throw",
            //? if >=26.3
            //0,  // Uses vanilla left click unless rebound
            //? else
            -1,  // Uses vanilla left click unless rebound
            CATEGORY
    );

    // Throw stack/Null modifier
    public static final KeyMapping THROW_STACK = new KeyMapping(
            "key.vr_throwing_extensions.throw_stack",
            //? if >=26.3
            //0,  // Uses vanilla right click unless rebound
            //? else
            -1,  // Uses vanilla right click unless rebound
            CATEGORY
    );

    // Offhand throw/catch
    public static final KeyMapping THROW_OFFHAND = new KeyMapping(
            "key.vr_throwing_extensions.throw_offhand",
            //? if >=26.3
            //0,
            //? else
            -1,
            CATEGORY
    );

    // Returns custom binding if rebound, otherwise default attack key
    public static boolean isThrowPressed() {
        return !THROW.isDefault() ? THROW.isDown()
                : Minecraft.getInstance().options.keyAttack.isDown();
    }

    // Returns custom binding if rebound, otherwise default use key
    public static boolean isThrowStackPressed() {
        return !THROW_STACK.isDefault() ? THROW_STACK.isDown()
                : Minecraft.getInstance().options.keyUse.isDown();
    }
}
