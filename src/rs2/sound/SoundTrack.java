package rs2.sound;

import rs2.net.Buffer;

/**
 * Cache-backed sound effect composed of up to ten procedural instruments.
 *
 * <p>
 * Tracks are mixed as unsigned 8-bit mono PCM at 22,050 Hz and returned in a
 * reusable RIFF/WAVE buffer. Optional loop boundaries can be repeated while
 * encoding without resynthesizing the instruments.
 * </p>
 */
public class SoundTrack {

	/** Archive track-list terminator. */
	private static final int TRACK_LIST_TERMINATOR = 0xffff;

	/** Constant value for sample rate. */
	private static final int SAMPLE_RATE = 22050;
	/** Constant value for wav header size. */
	private static final int WAV_HEADER_SIZE = 44;
	/** Maximum tracks. */
	private static final int MAX_TRACKS = 5000;
	/** Maximum instruments. */
	private static final int MAX_INSTRUMENTS = 10;
	/** Performs this client operation. */
	private static final byte PCM_SILENCE = (byte) 0x80;

	/** Decoded tracks indexed by their cache identifier. */
	public static SoundTrack[] tracks = new SoundTrack[MAX_TRACKS];

	/** Leading-silence reductions, measured in 20-millisecond units. */
	public static int[] trackDelays = new int[MAX_TRACKS];

	/** Shared WAV output storage. */
	public static byte[] outputData;

	/** Buffer view over {@link #outputData}. */
	public static Buffer outputBuffer;

	/** Instrument layers mixed into this track. */
	public SoundTrackInstrument[] instruments;

	/** Loop start position in milliseconds. */
	public int loopBegin;

	/** Loop end position in milliseconds. */
	public int loopEnd;

	/**
	 * Creates a new sound track.
	 */
	public SoundTrack() {
		instruments = new SoundTrackInstrument[MAX_INSTRUMENTS];
	}

	/**
	 * Loads all track definitions from a cache sound archive buffer.
	 * @param buffer the source buffer
	 */
	public static void load(Buffer buffer) {
		outputData = new byte[0x6baa8];
		outputBuffer = new Buffer(outputData);
		SoundTrackInstrument.initialize();

		while (true) {
			int trackId = buffer.readUnsignedShort();
			if (trackId == TRACK_LIST_TERMINATOR) {
				return;
			}
			SoundTrack track = new SoundTrack();
			track.decode(buffer);
			tracks[trackId] = track;
			trackDelays[trackId] = track.trimStart();
		}
	}

	/**
	 * Returns an encoded WAV buffer for a decoded track, or {@code null}.
	 * @param trackId the track ID
	 * @param loopCount the loop count
	 * @return the data
	 */
	public static Buffer getData(int trackId, int loopCount) {
		SoundTrack track = tracks[trackId];
		return track == null ? null : track.encode(loopCount);
	}

	/**
	 * Decodes instrument slots and loop boundaries for this track.
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		for (int instrument = 0; instrument < MAX_INSTRUMENTS; instrument++) {
			int active = buffer.readUnsignedByte();
			if (active != 0) {
				buffer.position--;
				instruments[instrument] = new SoundTrackInstrument();
				instruments[instrument].decode(buffer);
			}
		}
		loopBegin = buffer.readUnsignedShort();
		loopEnd = buffer.readUnsignedShort();
	}

	/**
	 * Removes common leading silence from the instruments and loop markers.
	 *
	 * @return the removed delay in 20-millisecond units
	 */
	public int trimStart() {
		int delay = 0x98967f;
		for (SoundTrackInstrument instrument : instruments) {
			if (instrument != null && instrument.offsetMillis / 20 < delay) {
				delay = instrument.offsetMillis / 20;
			}
		}
		if (loopBegin < loopEnd && loopBegin / 20 < delay) {
			delay = loopBegin / 20;
		}
		if (delay == 0x98967f || delay == 0) {
			return 0;
		}

		for (SoundTrackInstrument instrument : instruments) {
			if (instrument != null) {
				instrument.offsetMillis -= delay * 20;
			}
		}
		if (loopBegin < loopEnd) {
			loopBegin -= delay * 20;
			loopEnd -= delay * 20;
		}
		return delay;
	}

	/**
	 * Encodes the mixed track with a standard 44-byte PCM WAV header.
	 * @param loopCount the loop count
	 * @return a buffer containing the RIFF/WAVE data for the mixed track
	 */
	public Buffer encode(int loopCount) {
		int dataLength = mix(loopCount);
		outputBuffer.position = 0;
		outputBuffer.writeInt(0x52494646); // RIFF
		outputBuffer.writeIntLE(36 + dataLength);
		outputBuffer.writeInt(0x57415645); // WAVE
		outputBuffer.writeInt(0x666d7420); // fmt
		outputBuffer.writeIntLE(16); // PCM format chunk size
		outputBuffer.writeShortLE(1); // linear PCM
		outputBuffer.writeShortLE(1); // mono
		outputBuffer.writeIntLE(SAMPLE_RATE);
		outputBuffer.writeIntLE(SAMPLE_RATE); // 8-bit mono byte rate
		outputBuffer.writeShortLE(1); // block alignment
		outputBuffer.writeShortLE(8); // bits per sample
		outputBuffer.writeInt(0x64617461); // data
		outputBuffer.writeIntLE(dataLength);
		outputBuffer.position += dataLength;
		return outputBuffer;
	}

	/**
	 * Synthesizes and mixes all instruments into {@link #outputData}.
	 *
	 * @param loopCount total number of times the configured loop region appears
	 * @return PCM data length, excluding the WAV header
	 */
	public int mix(int loopCount) {
		int durationMillis = 0;
		for (SoundTrackInstrument instrument : instruments) {
			if (instrument != null && instrument.durationMillis + instrument.offsetMillis > durationMillis) {
				durationMillis = instrument.durationMillis + instrument.offsetMillis;
			}
		}
		if (durationMillis == 0) {
			return 0;
		}

		int sampleCount = SAMPLE_RATE * durationMillis / 1000;
		int loopBeginSample = SAMPLE_RATE * loopBegin / 1000;
		int loopEndSample = SAMPLE_RATE * loopEnd / 1000;
		if (loopBeginSample < 0 || loopBeginSample > sampleCount || loopEndSample < 0 || loopEndSample > sampleCount
				|| loopBeginSample >= loopEndSample) {
			/*
			 * Historical revision-377 behavior sets the requested loop count to zero
			 * rather than one. Cached track 1592 has loopEnd beyond its synthesized
			 * duration, so this can produce a negative output length. Independent
			 * historical Track sources contain the same arithmetic; keep it as a known
			 * cache/live-data quirk rather than silently changing the mixer.
			 */
			loopCount = 0;
		}

		int outputLength = sampleCount + (loopEndSample - loopBeginSample) * (loopCount - 1);
		for (int position = WAV_HEADER_SIZE; position < outputLength + WAV_HEADER_SIZE; position++) {
			outputData[position] = PCM_SILENCE;
		}

		for (SoundTrackInstrument instrument : instruments) {
			if (instrument == null) {
				continue;
			}
			int instrumentSamples = instrument.durationMillis * SAMPLE_RATE / 1000;
			int offsetSamples = instrument.offsetMillis * SAMPLE_RATE / 1000;
			int[] samples = instrument.synthesize(instrumentSamples, instrument.durationMillis);
			for (int sample = 0; sample < instrumentSamples; sample++) {
				int mixed = (outputData[sample + offsetSamples + WAV_HEADER_SIZE] & 0xff) + (samples[sample] >> 8);
				if ((mixed & 0xffffff00) != 0) {
					mixed = ~(mixed >> 31);
				}
				outputData[sample + offsetSamples + WAV_HEADER_SIZE] = (byte) mixed;
			}
		}

		if (loopCount > 1) {
			loopBeginSample += WAV_HEADER_SIZE;
			loopEndSample += WAV_HEADER_SIZE;
			sampleCount += WAV_HEADER_SIZE;
			int offset = (outputLength += WAV_HEADER_SIZE) - sampleCount;
			for (int position = sampleCount - 1; position >= loopEndSample; position--) {
				outputData[position + offset] = outputData[position];
			}
			for (int loop = 1; loop < loopCount; loop++) {
				offset = (loopEndSample - loopBeginSample) * loop;
				for (int position = loopBeginSample; position < loopEndSample; position++) {
					outputData[position + offset] = outputData[position];
				}
			}
			outputLength -= WAV_HEADER_SIZE;
		}
		return outputLength;
	}
}
