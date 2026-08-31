package rs2;

import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies cinematic camera, camera shake, reset, and world-hint packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class CameraPacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the camera packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	CameraPacketHandler(Client client) {
		this.client = client;
	}

	/**
	 * Applies one packet already routed to this domain.
	 *
	 * @param opcode decoded revision-377 opcode
	 * @param buffer payload buffer positioned at zero
	 * @param packetSize payload length in bytes
	 * @return always {@code true}; routed domain packets continue processing
	 * @throws IllegalArgumentException if the opcode was routed to the wrong domain
	 */
	boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.SET_HINT_ICON) {
			client.hintIconType = buffer.readUnsignedByte();
			if (client.hintIconType == 1)
				client.hintNpcIndex = buffer.readUnsignedShort();
			if (client.hintIconType >= 2 && client.hintIconType <= 6) {
				if (client.hintIconType == 2) {
					client.hintOffsetX = 64;
					client.hintOffsetY = 64;
				}
				if (client.hintIconType == 3) {
					client.hintOffsetX = 0;
					client.hintOffsetY = 64;
				}
				if (client.hintIconType == 4) {
					client.hintOffsetX = 128;
					client.hintOffsetY = 64;
				}
				if (client.hintIconType == 5) {
					client.hintOffsetX = 64;
					client.hintOffsetY = 0;
				}
				if (client.hintIconType == 6) {
					client.hintOffsetX = 64;
					client.hintOffsetY = 128;
				}
				client.hintIconType = 2;
				client.hintTileX = buffer.readUnsignedShort();
				client.hintTileY = buffer.readUnsignedShort();
				client.hintHeight = buffer.readUnsignedByte();
			}
			if (client.hintIconType == 10)
				client.hintPlayerIndex = buffer.readUnsignedShort();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_CINEMATIC_CAMERA_LOOK_AT) {
			int tileX = buffer.readUnsignedByte();
			int tileY = buffer.readUnsignedByte();
			int heightOffset = buffer.readUnsignedShort();
			int baseSpeed = buffer.readUnsignedByte();
			int scale = buffer.readUnsignedByte();
			client.packetCameraController().setCinematicLookAt(tileX, tileY, heightOffset, baseSpeed, scale, client.packetWorldState(), client.currentPlane);
			return true;
		}
		if (opcode == IncomingPacketOpcode.CAMERA_SHAKE) {
			int shakeIndex = buffer.readUnsignedByte();
			int randomAmplitude = buffer.readUnsignedByte();
			int sineAmplitude = buffer.readUnsignedByte();
			int frequency = buffer.readUnsignedByte();
			client.packetCameraController().configureShake(shakeIndex, randomAmplitude, sineAmplitude, frequency);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_CINEMATIC_CAMERA_POSITION) {
			int tileX = buffer.readUnsignedByte();
			int tileY = buffer.readUnsignedByte();
			int heightOffset = buffer.readUnsignedShort();
			int baseSpeed = buffer.readUnsignedByte();
			int scale = buffer.readUnsignedByte();
			client.packetCameraController().setCinematicPosition(tileX, tileY, heightOffset, baseSpeed, scale, client.packetWorldState(),
					client.currentPlane);
			return true;
		}
		if (opcode == IncomingPacketOpcode.RESET_CAMERA) {
			client.packetCameraController().stopCinematic();
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a camera packet");
	}
}
