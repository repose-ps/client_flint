package rs2.net;

import rs2.game.Pathfinder;

/** Serializes revision-377 walking routes into the game protocol. */
public final class MovementPacketEncoder {

	public static final int SCREEN = 0;

	public static final int MINIMAP = 1;

	public static final int INTERACTION = 2;

	private static final int MAX_WAYPOINTS = 25;

	private MovementPacketEncoder() {
	}

	/** Writes one compressed revision-377 walking route packet. */
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
			outgoing.writeOpcode(28);
			outgoing.writeByte(waypointCount + waypointCount + 3);
		}
		if (movementType == MINIMAP) {
			outgoing.writeOpcode(213);
			outgoing.writeByte(waypointCount + waypointCount + 3 + 14);
		}
		if (movementType == INTERACTION) {
			outgoing.writeOpcode(247);
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
