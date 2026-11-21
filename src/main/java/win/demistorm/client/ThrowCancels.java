package win.demistorm.client;

// Input cancellation flags (one pair each for VR and flatscreen)
public final class ThrowCancels {

    private ThrowCancels() {}

    private static boolean vrBreaks = false;
    private static boolean vrUse = false;
    private static boolean pancakeBreaks = false;
    private static boolean pancakeUse = false;

    public static void setVr(boolean cancelBreaks, boolean cancelUse) {
        vrBreaks = cancelBreaks;
        vrUse = cancelUse;
    }

    public static void setPancake(boolean cancelBreaks, boolean cancelUse) {
        pancakeBreaks = cancelBreaks;
        pancakeUse = cancelUse;
    }

    public static boolean cancelBreaks() {
        return vrBreaks || pancakeBreaks;
    }

    public static boolean cancelUse() {
        return vrUse || pancakeUse;
    }
}
