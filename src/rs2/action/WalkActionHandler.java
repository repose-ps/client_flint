package rs2.action;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.game.WorldState;
import rs2.ui.ClientLayout;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/** Applies the revision-377 world-view "Walk here" menu action. */
public final class WalkActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Reports whether a context menu is currently open. */
    private final BooleanSupplier menuOpen;
    /** Supplies the active world once startup has initialized it. */
    private final Supplier<WorldState> worldState;
    /** Current client layout. */
    private final ClientLayout layout;
    /** Current shell click X coordinate. */
    private final IntSupplier clickX;
    /** Current shell click Y coordinate. */
    private final IntSupplier clickY;

    /**
     * Creates the walk action handler.
     *
     * @param menuOpen whether a context menu is currently open
     * @param worldState active world supplier
     * @param layout client layout
     * @param clickX click-X supplier
     * @param clickY click-Y supplier
     */
    public WalkActionHandler(BooleanSupplier menuOpen, Supplier<WorldState> worldState, ClientLayout layout,
            IntSupplier clickX, IntSupplier clickY) {
        this.menuOpen = menuOpen;
        this.worldState = worldState;
        this.layout = layout;
        this.clickX = clickX;
        this.clickY = clickY;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, MenuEntry entry) {
        int argument0 = entry.argument0();
        int argument1 = entry.argument1();
        int argument2 = entry.argument2();
        if (actionId == MenuState.WALK_HERE) {
            if (!menuOpen.getAsBoolean()) {
                worldState.get().scene.setClick(clickX.getAsInt() - layout.viewportX(),
                        clickY.getAsInt() - layout.viewportY());
            } else {
                worldState.get().scene.setClick(argument1 - layout.viewportX(), argument2 - layout.viewportY());
            }
        }
        return false;
    }
}
