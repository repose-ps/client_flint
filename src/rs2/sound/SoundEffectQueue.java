package rs2.sound;

import java.util.function.LongSupplier;

import rs2.net.Buffer;
import rs2.sign.Signlink;

/** Revision-377 fixed-capacity queued sound-effect playback state. */
public final class SoundEffectQueue {
	private static final int CAPACITY = 50;
	private static final int RETRY_DELAY = -5;

	@FunctionalInterface
	interface SoundDataProvider {
		Buffer getData(int soundId, int loopCount);
	}

	interface WaveBackend {
		boolean save(byte[] data, int length);

		boolean replay();

		boolean reportErrors();

		void setVolume(int volume);
	}

	private static final class SignlinkWaveBackend implements WaveBackend {
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
			Signlink.waveVolume = volume;
		}
	}

	private final int[] soundIds = new int[CAPACITY];
	private final int[] loopCounts = new int[CAPACITY];
	private final int[] delays = new int[CAPACITY];
	private final SoundDataProvider dataProvider;
	private final WaveBackend waveBackend;
	private final LongSupplier clock;

	private int count;
	private boolean enabled = true;
	private int lastPlayedSoundId = -1;
	private int lastPlayedLoopCount = -1;
	private int lastWaveLength;
	private long lastWaveStartTime;

	public SoundEffectQueue() {
		this(SoundTrack::getData, new SignlinkWaveBackend(), System::currentTimeMillis);
	}

	SoundEffectQueue(SoundDataProvider dataProvider, WaveBackend waveBackend, LongSupplier clock) {
		this.dataProvider = dataProvider;
		this.waveBackend = waveBackend;
		this.clock = clock;
	}

	public void resetForLogin() {
		count = 0;
	}

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

	public void queuePacketSound(int soundId, int loopCount, int delay, boolean lowMemory) {
		if (delay == 65535) {
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

	/*
	 * Legacy client.method152(int i), sound-effect portion: i -> removed fixed
	 * -23763 sentinel; the only caller supplied -23763.
	 *
	 * Legacy queue fields moved here: anInt1035 -> count anIntArray1090 -> soundIds
	 * anIntArray1321 -> loopCounts anIntArray1259 -> delays anInt1272 ->
	 * lastPlayedSoundId anInt935 -> lastPlayedLoopCount anInt1179 -> lastWaveLength
	 * aLong1250 -> lastWaveStartTime
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
						/*
						 * Legacy method152 used the pre-refactor SoundTrack positional order
						 * (loopCount, soundId). The semantic API is now (soundId, loopCount).
						 */
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
					outgoing.writeOpcode(80);
					outgoing.writeShort(waveBackend.reportErrors() ? soundIds[index] & 0x7fff : -1);
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

	private void remove(int index) {
		count--;
		for (int source = index; source < count; source++) {
			soundIds[source] = soundIds[source + 1];
			loopCounts[source] = loopCounts[source + 1];
			delays[source] = delays[source + 1];
		}
	}

	boolean isEnabled() {
		return enabled;
	}

	int count() {
		return count;
	}

	int soundId(int index) {
		return soundIds[index];
	}

	int loopCount(int index) {
		return loopCounts[index];
	}

	int delay(int index) {
		return delays[index];
	}

	int lastPlayedSoundId() {
		return lastPlayedSoundId;
	}

	int lastPlayedLoopCount() {
		return lastPlayedLoopCount;
	}
}