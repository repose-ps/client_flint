package rs2.action;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.game.WorldState;
import rs2.ui.ClientLayout;
import rs2.ui.menu.MenuController;
import rs2.ui.menu.MenuState;

/** Applies the revision-377 world-view "Walk here" menu action. */
public final class WalkActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Menu state used to distinguish an open context menu from direct walking. */
    private final MenuController menuController;
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
     * @param menuController menu-state owner
     * @param worldState active world supplier
     * @param layout client layout
     * @param clickX click-X supplier
     * @param clickY click-Y supplier
     */
    public WalkActionHandler(MenuController menuController, Supplier<WorldState> worldState, ClientLayout layout,
            IntSupplier clickX, IntSupplier clickY) {
        this.menuController = menuController;
        this.worldState = worldState;
        this.layout = layout;
        this.clickX = clickX;
        this.clickY = clickY;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
        if (actionId == MenuState.WALK_HERE) {
            if (!menuController.state().open) {
                worldState.get().scene.setClick(clickX.getAsInt() - layout.viewportX(),
                        clickY.getAsInt() - layout.viewportY());
            } else {
                worldState.get().scene.setClick(cmd2 - layout.viewportX(), cmd3 - layout.viewportY());
            }
        }
        return false;
    }
}
