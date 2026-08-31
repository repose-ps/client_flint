package rs2.ui.menu;

/**
 * One revision-377 context-menu entry.
 *
 * @param text display text shown in the menu
 * @param action numeric revision-377 action identifier, including any priority offset
 * @param argument0 first action argument
 * @param argument1 second action argument
 * @param argument2 third action argument
 */
public record MenuEntry(String text, int action, int argument0, int argument1, int argument2) {

    /**
     * Returns a copy with different display text.
     *
     * @param newText replacement display text
     * @return the updated menu entry
     */
    MenuEntry withText(String newText) {
        return new MenuEntry(newText, action, argument0, argument1, argument2);
    }

    /**
     * Returns a copy with a different action identifier.
     *
     * @param newAction replacement action identifier
     * @return the updated menu entry
     */
    MenuEntry withAction(int newAction) {
        return new MenuEntry(text, newAction, argument0, argument1, argument2);
    }

    /**
     * Returns a copy with a different first argument.
     *
     * @param newArgument replacement first argument
     * @return the updated menu entry
     */
    MenuEntry withArgument0(int newArgument) {
        return new MenuEntry(text, action, newArgument, argument1, argument2);
    }

    /**
     * Returns a copy with a different second argument.
     *
     * @param newArgument replacement second argument
     * @return the updated menu entry
     */
    MenuEntry withArgument1(int newArgument) {
        return new MenuEntry(text, action, argument0, newArgument, argument2);
    }

    /**
     * Returns a copy with a different third argument.
     *
     * @param newArgument replacement third argument
     * @return the updated menu entry
     */
    MenuEntry withArgument2(int newArgument) {
        return new MenuEntry(text, action, argument0, argument1, newArgument);
    }
}
