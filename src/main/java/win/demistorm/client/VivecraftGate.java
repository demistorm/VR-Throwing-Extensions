package win.demistorm.client;

// Reflection only check so no Vivecraft classes load here
public final class VivecraftGate {

    private VivecraftGate() {}

    private static final boolean VIVECRAFT_PRESENT = checkPresent();

    private static boolean checkPresent() {
        try {
            Class.forName("org.vivecraft.api.VRAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isVivecraftPresent() {
        return VIVECRAFT_PRESENT;
    }
}
