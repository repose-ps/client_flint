package rs2.sound;

import rs2.net.Buffer;

/**
 * Piecewise-linear control envelope used by the procedural sound synthesizer.
 *
 * <p>
 * Segment positions are stored as unsigned 16-bit fractions of the target
 * sample period. Segment peaks are interpolated with 15 fractional bits.
 * </p>
 */
public class SoundTrackEnvelope {

	/** Number of decoded envelope segments. */
	public int segmentCount;

	/** Segment end positions expressed as unsigned 16-bit period fractions. */
	public int[] segmentDurations;

	/** Target amplitude at each segment boundary. */
	public int[] segmentPeaks;

	/** Starting value used when this envelope controls a parameter range. */
	public int start;

	/** Ending value used when this envelope controls a parameter range. */
	public int end;

	/** Waveform identifier used when the envelope drives an oscillator. */
	public int waveform;

	/** Absolute sample tick at which the current segment ends. */
	public int segmentEndTick;

	/** Index of the next segment peak to load. */
	public int segmentIndex;

	/** Per-tick change in the 15-bit fixed-point amplitude. */
	public int amplitudeStep;

	/** Current amplitude with 15 fractional bits. */
	public int amplitude;

	/** Number of samples advanced since the most recent reset. */
	public int tick;

	/** Decodes the envelope header followed by its segment shape. */
	public void decode(Buffer buffer) {
		waveform = buffer.readUnsignedByte();
		start = buffer.readInt();
		end = buffer.readInt();
		decodeSegments(buffer);
	}

	/** Decodes only the piecewise-linear segment data. */
	public void decodeSegments(Buffer buffer) {
		segmentCount = buffer.readUnsignedByte();
		segmentDurations = new int[segmentCount];
		segmentPeaks = new int[segmentCount];
		for (int segment = 0; segment < segmentCount; segment++) {
			segmentDurations[segment] = buffer.readUnsignedShort();
			segmentPeaks[segment] = buffer.readUnsignedShort();
		}
	}

	/** Restarts envelope interpolation from its first segment. */
	public void reset() {
		segmentEndTick = 0;
		segmentIndex = 0;
		amplitudeStep = 0;
		amplitude = 0;
		tick = 0;
	}

	/**
	 * Advances the envelope by one sample.
	 *
	 * @param period total number of samples over which durations are scaled
	 * @return the current interpolated amplitude
	 */
	public int step(int period) {
		if (tick >= segmentEndTick) {
			amplitude = segmentPeaks[segmentIndex++] << 15;
			if (segmentIndex >= segmentCount) {
				segmentIndex = segmentCount - 1;
			}
			segmentEndTick = (int) ((segmentDurations[segmentIndex] / 65536.0D) * period);
			if (segmentEndTick > tick) {
				amplitudeStep = ((segmentPeaks[segmentIndex] << 15) - amplitude) / (segmentEndTick - tick);
			}
		}
		amplitude += amplitudeStep;
		tick++;
		return amplitude - amplitudeStep >> 15;
	}
}
