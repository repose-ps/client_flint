package rs2.packet;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.game.CameraController;
import rs2.game.WorldState;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies cinematic camera, camera shake, reset, and world-hint packets.
 *
 * <p>
 * This application-layer domain handler is invoked only after
 * {@link PacketDomainDispatcher} has explicitly routed a recognized
 * revision-377 opcode to it.
 * </p>
 */
final class CameraPacketHandler {

	/** Camera owner receiving cinematic and shake packets. */
	private final CameraController camera;
	/** Supplies world state used for terrain-height camera positioning. */
	private final Supplier<WorldState> world;
	/** Supplies the current scene plane. */
	private final IntSupplier currentPlane;
	/** Receives hint-target packet effects. */
	private final PacketDomainDispatcher.HintSink hints;

	/**
	 * Creates the camera packet handler from its exact application capabilities.
	 *
	 * @param camera       camera owner
	 * @param world        world-state supplier
	 * @param currentPlane current-plane supplier
	 * @param hints        hint-target sink
	 */
	CameraPacketHandler(CameraController camera, Supplier<WorldState> world, IntSupplier currentPlane,
			PacketDomainDispatcher.HintSink hints) {
		this.camera = camera;
		this.world = world;
		this.currentPlane = currentPlane;
		this.hints = hints;
	}

	/**
	 * Applies one packet already routed to this domain.
	 *
	 * @param opcode     decoded revision-377 opcode
	 * @param buffer     payload buffer positioned at zero
	 * @param packetSize payload length in bytes
	 * @return always {@code true}; routed domain packets continue processing
	 * @throws IllegalArgumentException if the opcode was routed to the wrong domain
	 */
	boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.SET_HINT_ICON) {
			int hintType = buffer.readUnsignedByte();
			hints.setType(hintType);
			if (hintType == 1)
				hints.setNpcIndex(buffer.readUnsignedShort());
			if (hintType >= 2 && hintType <= 6) {
				int offsetX = 64;
				int offsetY = 64;
				if (hintType == 3)
					offsetX = 0;
				if (hintType == 4)
					offsetX = 128;
				if (hintType == 5)
					offsetY = 0;
				if (hintType == 6)
					offsetY = 128;
				int tileX = buffer.readUnsignedShort();
				int tileY = buffer.readUnsignedShort();
				int height = buffer.readUnsignedByte();
				hints.setTileHint(tileX, tileY, height, offsetX, offsetY);
			}
			if (hintType == 10)
				hints.setPlayerIndex(buffer.readUnsignedShort());
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_CINEMATIC_CAMERA_LOOK_AT) {
			int tileX = buffer.readUnsignedByte();
			int tileY = buffer.readUnsignedByte();
			int heightOffset = buffer.readUnsignedShort();
			int baseSpeed = buffer.readUnsignedByte();
			int scale = buffer.readUnsignedByte();
			camera.setCinematicLookAt(tileX, tileY, heightOffset, baseSpeed, scale, world.get(),
					currentPlane.getAsInt());
			return true;
		}
		if (opcode == IncomingPacketOpcode.CAMERA_SHAKE) {
			int shakeIndex = buffer.readUnsignedByte();
			int randomAmplitude = buffer.readUnsignedByte();
			int sineAmplitude = buffer.readUnsignedByte();
			int frequency = buffer.readUnsignedByte();
			camera.configureShake(shakeIndex, randomAmplitude, sineAmplitude, frequency);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_CINEMATIC_CAMERA_POSITION) {
			int tileX = buffer.readUnsignedByte();
			int tileY = buffer.readUnsignedByte();
			int heightOffset = buffer.readUnsignedShort();
			int baseSpeed = buffer.readUnsignedByte();
			int scale = buffer.readUnsignedByte();
			camera.setCinematicPosition(tileX, tileY, heightOffset, baseSpeed, scale, world.get(),
					currentPlane.getAsInt());
			return true;
		}
		if (opcode == IncomingPacketOpcode.RESET_CAMERA) {
			camera.stopCinematic();
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a camera packet");
	}
}
