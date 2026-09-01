package rs2.sound;

import java.util.function.LongSupplier;

import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.sign.Signlink;

/** Revision-377 fixed-capacity queued sound-effect playback state. */
public final class SoundEffectQueue {

	/** Packet delay value selecting the legacy immediate/raw queue path. */
	private static final int PACKET_DELAY_SENTINEL = 0xffff;
	/** Constant value for capacity. */
	private static final int CAPACITY = 50;
	/** Constant value for retry delay. */
	private static final int RETRY_DELAY = -5;
	/** Low 15 bits carrying the sound-effect definition id. */
	private static final int SOUND_ID_MASK = 0x7fff;

	/** Sentinel emitted when failed wave playback should not report a sound id. */
	private static final int NO_SOUND_ID = -1;

	/** Provides sound data provider state and behavior. */
	@FunctionalInterface
	interface SoundDataProvider {
		/**
		 * Returns data.
		 *
		 * @param soundId   the sound ID
		 * @param loopCount the loop count
		 * @return the data
		 */
		Buffer getData(int soundId, int loopCount);
	}

	/** Provides wave backend state and behavior. */
	interface WaveBackend {
		/**
		 * Saves the operation.
		 *
		 * @param data   the data to process
		 * @param length the number of elements or bytes
		 * @return whether save
		 */
		boolean save(byte[] data, int length);

		/**
		 * Replays the most recently saved wave sample.
		 *
		 * @return whether replay
		 */
		boolean replay();

		/**
		 * Returns whether wave-playback failures should be reported.
		 *
		 * @return whether report errors
		 */
		boolean reportErrors();

		/**
		 * Sets volume.
		 *
		 * @param volume the volume
		 */
		void setVolume(int volume);
	}

	/**
	 * Wave backend that delegates legacy sound-effect playback to {@link Signlink}.
	 */
	private static final class SignlinkWaveBackend implements WaveBackend {

		/** Creates a new signlink wave backend with its default client state. */
		private SignlinkWaveBackend() {
		}

		public boolean save(byte[] data, int length) {
			return Signlink.saveWave(data, length);
		}

		public boolean replay() {
			return Signlink.replayWave();
		}

		public boolean reportErrors() {
			return Signlink.reportErrors;
		}

		public void setVolume(int volume) {
			Signlink.setWaveVolume(volume);
		}
	}

	/** Stores sound IDs values. */
	private final int[] soundIds = new int[CAPACITY];
	/** Stores loop counts values. */
	private final int[] loopCounts = new int[CAPACITY];
	/** Stores delays values. */
	private final int[] delays = new int[CAPACITY];
	/** Stores the current data provider. */
	private final SoundDataProvider dataProvider;
	/** Stores the current wave backend. */
	private final WaveBackend waveBackend;
	/** Stores the current clock. */
	private final LongSupplier clock;

	/** Stores the current count. */
	private int count;
	/** Whether enabled is enabled or active. */
	private boolean enabled = true;
	/** Stores the current last played sound ID. */
	private int lastPlayedSoundId = -1;
	/** Stores the current last played loop count. */
	private int lastPlayedLoopCount = -1;
	/** Stores the current last wave length. */
	private int lastWaveLength;
	/** Stores the current last wave start time. */
	private long lastWaveStartTime;

	/**
	 * Creates a new sound effect queue.
	 */
	public SoundEffectQueue() {
		this(SoundTrack::getData, new SignlinkWaveBackend(), System::currentTimeMillis);
	}

	/**
	 * Creates a new sound effect queue.
	 *
	 * @param dataProvider the data provider
	 * @param waveBackend  the wave backend
	 * @param clock        the clock
	 */
	SoundEffectQueue(SoundDataProvider dataProvider, WaveBackend waveBackend, LongSupplier clock) {
		this.dataProvider = dataProvider;
		this.waveBackend = waveBackend;
		this.clock = clock;
	}

	/**
	 * Resets for login.
	 */
	public void resetForLogin() {
		count = 0;
	}

	/**
	 * Applies setting.
	 *
	 * @param setting the setting
	 */
	public void applySetting(int setting) {
		if (setting == 0) {
			enabled = true;
			waveBackend.setVolume(0);
		}
		if (setting == 1) {
			enabled = true;
			waveBackend.setVolume(-400);
		}
		if (setting == 2) {
			enabled = true;
			waveBackend.setVolume(-800);
		}
		if (setting == 3) {
			enabled = true;
			waveBackend.setVolume(-1200);
		}
		if (setting == 4)
			enabled = false;
	}

	/**
	 * Queues packet sound.
	 *
	 * @param soundId   the sound ID
	 * @param loopCount the loop count
	 * @param delay     the delay
	 * @param lowMemory whether low-memory mode is active
	 */
	public void queuePacketSound(int soundId, int loopCount, int delay, boolean lowMemory) {
		if (delay == PACKET_DELAY_SENTINEL) {
			if (count < CAPACITY) {
				soundIds[count] = (short) soundId;
				loopCounts[count] = loopCount;
				delays[count] = 0;
				count++;
			}
		} else if (enabled && !lowMemory && count < CAPACITY) {
			soundIds[count] = soundId;
			loopCounts[count] = loopCount;
			delays[count] = delay + SoundTrack.trackDelays[soundId];
			count++;
		}
	}

	/**
	 * Queues area sound.
	 *
	 * @param soundId     the sound ID
	 * @param loopCount   the loop count
	 * @param radius      the radius
	 * @param tileX       the tile X
	 * @param tileY       the tile Y
	 * @param playerTileX the player tile X
	 * @param playerTileY the player tile Y
	 * @param lowMemory   whether low-memory mode is active
	 */
	public void queueAreaSound(int soundId, int loopCount, int radius, int tileX, int tileY, int playerTileX,
			int playerTileY, boolean lowMemory) {
		if (playerTileX >= tileX - radius && playerTileX <= tileX + radius && playerTileY >= tileY - radius
				&& playerTileY <= tileY + radius && enabled && !lowMemory && count < CAPACITY) {
			soundIds[count] = soundId;
			loopCounts[count] = loopCount;
			delays[count] = SoundTrack.trackDelays[soundId];
			count++;
		}
	}

	/**
	 * Advances queued sound effects, starting or retrying playback and removing
	 * completed entries.
	 * 
	 * @param outgoing the outgoing
	 */
	public void update(Buffer outgoing) {
		for (int index = 0; index < count; index++) {
			if (delays[index] <= 0) {
				boolean retry = false;
				try {
					if (soundIds[index] == lastPlayedSoundId && loopCounts[index] == lastPlayedLoopCount) {
						if (!waveBackend.replay())
							retry = true;
					} else {

						Buffer data = dataProvider.getData(soundIds[index], loopCounts[index]);
						if (clock.getAsLong() + (long) (data.position / 22) > lastWaveStartTime
								+ (long) (lastWaveLength / 22)) {
							lastWaveLength = data.position;
							lastWaveStartTime = clock.getAsLong();
							if (waveBackend.save(data.payload, data.position)) {
								lastPlayedSoundId = soundIds[index];
								lastPlayedLoopCount = loopCounts[index];
							} else {
								retry = true;
							}
						}
					}
				} catch (Exception exception) {
					outgoing.writeOpcode(OutgoingPacketOpcode.SOUND_EFFECT_ERROR);
					outgoing.writeShort(waveBackend.reportErrors() ? soundIds[index] & SOUND_ID_MASK : NO_SOUND_ID);
				}
				if (!retry || delays[index] == RETRY_DELAY)
					remove(index--);
				else
					delays[index] = RETRY_DELAY;
			} else {
				delays[index]--;
			}
		}
	}

	/**
	 * Removes the operation.
	 *
	 * @param index the array or registry index
	 */
	private void remove(int index) {
		count--;
		for (int source = index; source < count; source++) {
			soundIds[source] = soundIds[source + 1];
			loopCounts[source] = loopCounts[source + 1];
			delays[source] = delays[source + 1];
		}
	}

	/**
	 * Returns whether enabled.
	 *
	 * @return whether enabled
	 */
	boolean isEnabled() {
		return enabled;
	}

	/**
	 * Returns the count.
	 *
	 * @return the count
	 */
	int count() {
		return count;
	}

	/**
	 * Returns the sound ID.
	 *
	 * @param index the array or registry index
	 * @return the sound ID
	 */
	int soundId(int index) {
		return soundIds[index];
	}

	/**
	 * Returns the loop count.
	 *
	 * @param index the array or registry index
	 * @return the loop count
	 */
	int loopCount(int index) {
		return loopCounts[index];
	}

	/**
	 * Returns the delay coordinate.
	 *
	 * @param index the array or registry index
	 * @return the delay
	 */
	int delay(int index) {
		return delays[index];
	}

	/**
	 * Returns the last played sound ID.
	 *
	 * @return the last played sound ID
	 */
	int lastPlayedSoundId() {
		return lastPlayedSoundId;
	}

	/**
	 * Returns the last played loop count.
	 *
	 * @return the last played loop count
	 */
	int lastPlayedLoopCount() {
		return lastPlayedLoopCount;
	}
}
