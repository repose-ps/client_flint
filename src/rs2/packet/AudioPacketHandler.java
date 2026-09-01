package rs2.packet;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import rs2.cache.ondemand.OnDemandFetcher;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.sound.MusicController;
import rs2.sound.SoundEffectQueue;

/**
 * Applies global sound-effect and music-selection packets.
 *
 * <p>
 * This application-layer domain handler is invoked only after
 * {@link PacketDomainDispatcher} has explicitly routed a recognized
 * revision-377 opcode to it.
 * </p>
 */
final class AudioPacketHandler {

	/** Queues decoded sound effects. */
	private final SoundEffectQueue sounds;
	/** Owns music selection and temporary-track playback. */
	private final MusicController music;
	/** Supplies the asynchronous resource fetcher used for music data. */
	private final Supplier<OnDemandFetcher> resources;
	/** Supplies the current low-memory configuration. */
	private final BooleanSupplier lowMemory;

	/**
	 * Creates the audio packet handler from its exact application capabilities.
	 *
	 * @param sounds    sound-effect queue
	 * @param music     music controller
	 * @param resources on-demand resource supplier
	 * @param lowMemory low-memory configuration supplier
	 */
	AudioPacketHandler(SoundEffectQueue sounds, MusicController music, Supplier<OnDemandFetcher> resources,
			BooleanSupplier lowMemory) {
		this.sounds = sounds;
		this.music = music;
		this.resources = resources;
		this.lowMemory = lowMemory;
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
		if (opcode == IncomingPacketOpcode.PLAY_SOUND_EFFECT) {
			int soundId = buffer.readUnsignedShort();
			int loopCount = buffer.readUnsignedByte();
			int delay = buffer.readUnsignedShort();
			sounds.queuePacketSound(soundId, loopCount, delay, lowMemory.getAsBoolean());
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAY_MUSIC) {
			int trackId = buffer.readUnsignedShortLEAdd();
			music.selectTrack(trackId, lowMemory.getAsBoolean(), resources.get()::request);
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAY_TEMPORARY_MUSIC) {
			int trackId = buffer.readUnsignedShortLE();
			int resumeDelay = buffer.readMediumME();
			music.playTemporaryTrack(trackId, resumeDelay, lowMemory.getAsBoolean(), resources.get()::request);
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a audio packet");
	}
}
