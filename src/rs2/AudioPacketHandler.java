package rs2;

import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies global sound-effect and music-selection packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class AudioPacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the audio packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	AudioPacketHandler(Client client) {
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
		if (opcode == IncomingPacketOpcode.PLAY_SOUND_EFFECT) {
			int soundId = buffer.readUnsignedShort();
			int loopCount = buffer.readUnsignedByte();
			int delay = buffer.readUnsignedShort();
			client.packetSoundEffectQueue().queuePacketSound(soundId, loopCount, delay, client.lowMemory);
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAY_MUSIC) {
			int trackId = buffer.readUnsignedShortLEAdd();
			client.packetMusicController().selectTrack(trackId, client.lowMemory, client.packetOnDemandFetcher()::request);
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAY_TEMPORARY_MUSIC) {
			int trackId = buffer.readUnsignedShortLE();
			int resumeDelay = buffer.readMediumME();
			client.packetMusicController().playTemporaryTrack(trackId, resumeDelay, client.lowMemory, client.packetOnDemandFetcher()::request);
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a audio packet");
	}
}
