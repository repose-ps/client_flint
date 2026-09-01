/**
 * Contains revision-377 context-menu state, construction, and mouse
 * interaction.
 *
 * <p>
 * {@link rs2.ui.menu.MenuState} stores fixed-capacity
 * {@link rs2.ui.menu.MenuEntry} records. {@link rs2.ui.menu.MenuController}
 * builds world, widget, inventory, social, and chat menu entries and owns
 * open-menu geometry. {@link rs2.ui.menu.MenuActionDomain} records the
 * application routing boundary; network/gameplay effects of selected actions
 * remain deliberately dispatched outside this package.
 * </p>
 */
package rs2.ui.menu;
