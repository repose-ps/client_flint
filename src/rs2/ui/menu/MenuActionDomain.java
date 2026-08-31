package rs2.ui.menu;

/**
 * Cohesive application domains used to route normalized revision-377 menu actions.
 *
 * <p>This is intentionally a small routing taxonomy rather than an action-type
 * hierarchy. Numeric action IDs remain authoritative in {@link MenuState}; the
 * domain only records which existing application handler owns each action.</p>
 */
public enum MenuActionDomain {
    /** Player-targeted interactions. */
    PLAYER,
    /** NPC-targeted interactions. */
    NPC,
    /** Scene-object interactions. */
    OBJECT,
    /** Ground-item interactions. */
    GROUND_ITEM,
    /** Inventory and inventory-widget item interactions. */
    INVENTORY,
    /** Non-inventory widget actions. */
    WIDGET,
    /** Friend, ignore, messaging, trade, challenge, and report actions. */
    SOCIAL,
    /** Walk-here viewport action. */
    WALK,
    /** Default cancel entry. */
    CANCEL,
    /** Unknown or unsupported action identifier. */
    UNKNOWN
}
