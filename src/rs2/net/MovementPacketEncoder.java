package rs2.net;

import rs2.game.Pathfinder;

/** Serializes revision-377 walking routes into the game protocol. */
public final class MovementPacketEncoder {

	/** Constant value for screen. */
	public static final int SCREEN = 0;

	/** Constant value for minimap. */
	public static final int MINIMAP = 1;

	/** Constant value for interaction. */
	public static final int INTERACTION = 2;

	/** Maximum waypoints. */
	private static final int MAX_WAYPOINTS = 25;

	/**
	 * Creates a new movement packet encoder.
	 */
	private MovementPacketEncoder() {
	}

	/**
	 * Writes one compressed revision-377 walking route packet.
	 * @param outgoing the outgoing
	 * @param route the route
	 * @param movementType the movement type
	 * @param baseX the base X
	 * @param baseY the base Y
	 * @param running the running
	 */
	public static void write(Buffer outgoing, Pathfinder.Route route, int movementType, int baseX, int baseY,
			boolean running) {
		int waypointCount = route.getWaypointCount();
		if (waypointCount > MAX_WAYPOINTS) {
			waypointCount = MAX_WAYPOINTS;
		}

		int routeIndex = route.getWaypointCount() - 1;
		int firstX = route.getWaypointX(routeIndex);
		int firstY = route.getWaypointY(routeIndex);

		if (movementType == SCREEN) {
			outgoing.writeOpcode(OutgoingPacketOpcode.WALK_SCREEN);
			outgoing.writeByte(waypointCount + waypointCount + 3);
		}
		if (movementType == MINIMAP) {
			outgoing.writeOpcode(OutgoingPacketOpcode.WALK_MINIMAP);
			outgoing.writeByte(waypointCount + waypointCount + 3 + 14);
		}
		if (movementType == INTERACTION) {
			outgoing.writeOpcode(OutgoingPacketOpcode.WALK_INTERACTION);
			outgoing.writeByte(waypointCount + waypointCount + 3);
		}

		outgoing.writeShortAddLE(firstX + baseX);
		outgoing.writeByte(running ? 1 : 0);
		outgoing.writeShortAddLE(firstY + baseY);

		for (int waypoint = 1; waypoint < waypointCount; waypoint++) {
			routeIndex--;
			outgoing.writeByte(route.getWaypointX(routeIndex) - firstX);
			outgoing.writeByteSub(route.getWaypointY(routeIndex) - firstY);
		}
	}
}
