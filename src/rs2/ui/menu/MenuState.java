package rs2.ui.menu;

/**
 * Fixed-size revision-377 context-menu state.
 *
 * The parallel arrays deliberately mirror the original client representation.
 * Menu action IDs are not renumbered: values >= 2000 are the original
 * low-priority variants and are normalized only when an action is dispatched.
 */
public final class MenuState {
    public static final int CAPACITY = 500;
    public static final int CANCEL_ACTION = 1016;

    public String[] actionNames = new String[CAPACITY];
    public int[] actionIds = new int[CAPACITY];
    public int[] actionCmd1 = new int[CAPACITY];
    public int[] actionCmd2 = new int[CAPACITY];
    public int[] actionCmd3 = new int[CAPACITY];
    public int count;

    public boolean open;
    /** 0 = viewport, 1 = sidebar, 2 = chatbox. */
    public int screenArea;
    public int offsetX;
    public int offsetY;
    public int width;
    public int height;

    /** Restores the one-entry default menu used before each rebuild. */
    public void reset() {
        actionNames[0] = "Cancel";
        actionIds[0] = CANCEL_ACTION;
        count = 1;
    }

    /**
     * Preserve the original stable bubble partition: action IDs above 1000 move
     * before action IDs below 1000. The comparison is intentionally strict.
     */
    public void prioritizeActions() {
        for (boolean sorted = false; !sorted;) {
            sorted = true;
            for (int index = 0; index < count - 1; index++) {
                if (actionIds[index] < 1000 && actionIds[index + 1] > 1000) {
                    swap(index, index + 1);
                    sorted = false;
                }
            }
        }
    }

    public boolean isAddFriendAction(int index) {
        if (index < 0)
            return false;
        int actionId = normalizeActionId(actionIds[index]);
        return actionId == 762;
    }

    public static int normalizeActionId(int actionId) {
        return actionId >= 2000 ? actionId - 2000 : actionId;
    }

    /** Match the original quit-time nulling of the five menu arrays. */
    public void clearReferencesForQuit() {
        actionNames = null;
        actionIds = null;
        actionCmd1 = null;
        actionCmd2 = null;
        actionCmd3 = null;
    }

    private void swap(int first, int second) {
        String name = actionNames[first];
        actionNames[first] = actionNames[second];
        actionNames[second] = name;

        int value = actionIds[first];
        actionIds[first] = actionIds[second];
        actionIds[second] = value;

        value = actionCmd1[first];
        actionCmd1[first] = actionCmd1[second];
        actionCmd1[second] = value;

        value = actionCmd2[first];
        actionCmd2[first] = actionCmd2[second];
        actionCmd2[second] = value;

        value = actionCmd3[first];
        actionCmd3[first] = actionCmd3[second];
        actionCmd3[second] = value;
    }
}