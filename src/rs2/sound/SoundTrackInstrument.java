package rs2.sound;

import rs2.net.Buffer;

/**
 * Procedural instrument used to synthesize one sound-track layer at 22,050 Hz.
 *
 * <p>
 * The instrument combines up to five oscillators with pitch and amplitude
 * envelopes, optional modulation and gating, a feedback delay, and a
 * time-varying recursive filter. Samples are accumulated as integers and
 * clamped to signed 16-bit PCM after processing.
 * </p>
 */
public class SoundTrackInstrument {

	public SoundTrackEnvelope pitchEnvelope;
	public SoundTrackEnvelope volumeEnvelope;
	public SoundTrackEnvelope pitchModulationEnvelope;
	public SoundTrackEnvelope pitchModulationAmplitudeEnvelope;
	public SoundTrackEnvelope volumeModulationEnvelope;
	public SoundTrackEnvelope volumeModulationAmplitudeEnvelope;
	public SoundTrackEnvelope gatingReleaseEnvelope;
	public SoundTrackEnvelope gatingAttackEnvelope;
	public int[] oscillatorVolumes;
	public int[] oscillatorPitchDeltas;
	public int[] oscillatorDelays;
	public int delayTime;
	public int delayFeedback;
	public SoundFilter filter;
	public SoundTrackEnvelope filterEnvelope;
	public int durationMillis;
	public int offsetMillis;

	/** Shared synthesis output; large enough for ten seconds at 22,050 Hz. */
	public static int[] sampleBuffer;

	/** Deterministic-for-session bipolar noise lookup used by waveform four. */
	public static int[] noiseTable;

	/** 14-bit sine lookup covering the instrument's 15-bit phase cycle. */
	public static int[] sineTable;

	public static int[] phases = new int[5];
	public static int[] sampleDelays = new int[5];
	public static int[] volumeSteps = new int[5];
	public static int[] pitchSteps = new int[5];
	public static int[] basePitchSteps = new int[5];

	public SoundTrackInstrument() {
		oscillatorVolumes = new int[5];
		oscillatorPitchDeltas = new int[5];
		oscillatorDelays = new int[5];
		delayFeedback = 100;
		durationMillis = 500;
	}

	/** Initializes the shared noise, sine, and output buffers. */
	public static void initialize() {
		noiseTable = new int[32768];
		for (int index = 0; index < noiseTable.length; index++) {
			noiseTable[index] = Math.random() > 0.5D ? 1 : -1;
		}

		sineTable = new int[32768];
		for (int index = 0; index < sineTable.length; index++) {
			sineTable[index] = (int) (Math.sin(index / 5215.1903000000002D) * 16384.0D);
		}
		sampleBuffer = new int[0x35d54];
	}

	/**
	 * Synthesizes this instrument into the shared sample buffer.
	 *
	 * @param sampleCount    requested output sample count
	 * @param durationMillis playback duration represented by those samples
	 * @return the shared signed-PCM sample buffer
	 */
	public int[] synthesize(int sampleCount, int durationMillis) {
		for (int position = 0; position < sampleCount; position++) {
			sampleBuffer[position] = 0;
		}
		if (durationMillis < 10) {
			return sampleBuffer;
		}

		double samplesPerMillisecond = sampleCount / (durationMillis + 0.0D);
		pitchEnvelope.reset();
		volumeEnvelope.reset();

		int pitchModulationStep = 0;
		int pitchModulationBaseStep = 0;
		int pitchModulationPhase = 0;
		if (pitchModulationEnvelope != null) {
			pitchModulationEnvelope.reset();
			pitchModulationAmplitudeEnvelope.reset();
			pitchModulationStep = (int) (((pitchModulationEnvelope.end - pitchModulationEnvelope.start)
					* 32.768000000000001D) / samplesPerMillisecond);
			pitchModulationBaseStep = (int) ((pitchModulationEnvelope.start * 32.768000000000001D)
					/ samplesPerMillisecond);
		}

		int volumeModulationStep = 0;
		int volumeModulationBaseStep = 0;
		int volumeModulationPhase = 0;
		if (volumeModulationEnvelope != null) {
			volumeModulationEnvelope.reset();
			volumeModulationAmplitudeEnvelope.reset();
			volumeModulationStep = (int) (((volumeModulationEnvelope.end - volumeModulationEnvelope.start)
					* 32.768000000000001D) / samplesPerMillisecond);
			volumeModulationBaseStep = (int) ((volumeModulationEnvelope.start * 32.768000000000001D)
					/ samplesPerMillisecond);
		}

		for (int oscillator = 0; oscillator < 5; oscillator++) {
			if (oscillatorVolumes[oscillator] != 0) {
				phases[oscillator] = 0;
				sampleDelays[oscillator] = (int) (oscillatorDelays[oscillator] * samplesPerMillisecond);
				volumeSteps[oscillator] = (oscillatorVolumes[oscillator] << 14) / 100;
				pitchSteps[oscillator] = (int) (((pitchEnvelope.end - pitchEnvelope.start) * 32.768000000000001D
						* Math.pow(1.0057929410678534D, oscillatorPitchDeltas[oscillator])) / samplesPerMillisecond);
				basePitchSteps[oscillator] = (int) ((pitchEnvelope.start * 32.768000000000001D)
						/ samplesPerMillisecond);
			}
		}

		for (int sample = 0; sample < sampleCount; sample++) {
			int pitch = pitchEnvelope.step(sampleCount);
			int volume = volumeEnvelope.step(sampleCount);
			if (pitchModulationEnvelope != null) {
				int modulation = pitchModulationEnvelope.step(sampleCount);
				int amplitude = pitchModulationAmplitudeEnvelope.step(sampleCount);
				pitch += evaluateWave(amplitude, pitchModulationPhase, pitchModulationEnvelope.waveform) >> 1;
				pitchModulationPhase += (modulation * pitchModulationStep >> 16) + pitchModulationBaseStep;
			}
			if (volumeModulationEnvelope != null) {
				int modulation = volumeModulationEnvelope.step(sampleCount);
				int amplitude = volumeModulationAmplitudeEnvelope.step(sampleCount);
				volume = volume
						* ((evaluateWave(amplitude, volumeModulationPhase, volumeModulationEnvelope.waveform) >> 1)
								+ 32768) >> 15;
				volumeModulationPhase += (modulation * volumeModulationStep >> 16) + volumeModulationBaseStep;
			}

			for (int oscillator = 0; oscillator < 5; oscillator++) {
				if (oscillatorVolumes[oscillator] != 0) {
					int position = sample + sampleDelays[oscillator];
					if (position < sampleCount) {
						sampleBuffer[position] += evaluateWave(volume * volumeSteps[oscillator] >> 15,
								phases[oscillator], pitchEnvelope.waveform);
						phases[oscillator] += (pitch * pitchSteps[oscillator] >> 16) + basePitchSteps[oscillator];
					}
				}
			}
		}

		applyGating(sampleCount);
		applyDelay(sampleCount, samplesPerMillisecond);
		applyFilter(sampleCount);

		for (int position = 0; position < sampleCount; position++) {
			if (sampleBuffer[position] < -32768) {
				sampleBuffer[position] = -32768;
			}
			if (sampleBuffer[position] > 32767) {
				sampleBuffer[position] = 32767;
			}
		}
		return sampleBuffer;
	}

	private void applyGating(int sampleCount) {
		if (gatingReleaseEnvelope == null) {
			return;
		}
		gatingReleaseEnvelope.reset();
		gatingAttackEnvelope.reset();
		int counter = 0;
		boolean muted = true;
		for (int position = 0; position < sampleCount; position++) {
			int releaseStep = gatingReleaseEnvelope.step(sampleCount);
			int attackStep = gatingAttackEnvelope.step(sampleCount);
			int threshold;
			if (muted) {
				threshold = gatingReleaseEnvelope.start
						+ ((gatingReleaseEnvelope.end - gatingReleaseEnvelope.start) * releaseStep >> 8);
			} else {
				threshold = gatingReleaseEnvelope.start
						+ ((gatingReleaseEnvelope.end - gatingReleaseEnvelope.start) * attackStep >> 8);
			}
			counter += 256;
			if (counter >= threshold) {
				counter = 0;
				muted = !muted;
			}
			if (muted) {
				sampleBuffer[position] = 0;
			}
		}
	}

	private void applyDelay(int sampleCount, double samplesPerMillisecond) {
		if (delayTime <= 0 || delayFeedback <= 0) {
			return;
		}
		int delaySamples = (int) (delayTime * samplesPerMillisecond);
		for (int position = delaySamples; position < sampleCount; position++) {
			sampleBuffer[position] += sampleBuffer[position - delaySamples] * delayFeedback / 100;
		}
	}

	private void applyFilter(int sampleCount) {
		if (filter.pairCount[0] == 0 && filter.pairCount[1] == 0) {
			return;
		}
		filterEnvelope.reset();
		int envelopeValue = filterEnvelope.step(sampleCount + 1);
		int feedForwardCount = filter.compute(0, envelopeValue / 65536.0F);
		int feedbackCount = filter.compute(1, envelopeValue / 65536.0F);
		if (sampleCount < feedForwardCount + feedbackCount) {
			return;
		}

		int sample = 0;
		int blockEnd = feedbackCount;
		if (blockEnd > sampleCount - feedForwardCount) {
			blockEnd = sampleCount - feedForwardCount;
		}
		for (; sample < blockEnd; sample++) {
			int output = (int) ((long) sampleBuffer[sample + feedForwardCount] * SoundFilter.inverseUnity >> 16);
			for (int coefficient = 0; coefficient < feedForwardCount; coefficient++) {
				output += (int) ((long) sampleBuffer[sample + feedForwardCount - 1 - coefficient]
						* SoundFilter.coefficients[0][coefficient] >> 16);
			}
			for (int coefficient = 0; coefficient < sample; coefficient++) {
				output -= (int) ((long) sampleBuffer[sample - 1 - coefficient]
						* SoundFilter.coefficients[1][coefficient] >> 16);
			}
			sampleBuffer[sample] = output;
			envelopeValue = filterEnvelope.step(sampleCount + 1);
		}

		int coefficientBlockSize = 128;
		blockEnd = coefficientBlockSize;
		while (true) {
			if (blockEnd > sampleCount - feedForwardCount) {
				blockEnd = sampleCount - feedForwardCount;
			}
			for (; sample < blockEnd; sample++) {
				int output = (int) ((long) sampleBuffer[sample + feedForwardCount] * SoundFilter.inverseUnity >> 16);
				for (int coefficient = 0; coefficient < feedForwardCount; coefficient++) {
					output += (int) ((long) sampleBuffer[sample + feedForwardCount - 1 - coefficient]
							* SoundFilter.coefficients[0][coefficient] >> 16);
				}
				for (int coefficient = 0; coefficient < feedbackCount; coefficient++) {
					output -= (int) ((long) sampleBuffer[sample - 1 - coefficient]
							* SoundFilter.coefficients[1][coefficient] >> 16);
				}
				sampleBuffer[sample] = output;
				envelopeValue = filterEnvelope.step(sampleCount + 1);
			}
			if (sample >= sampleCount - feedForwardCount) {
				break;
			}
			feedForwardCount = filter.compute(0, envelopeValue / 65536.0F);
			feedbackCount = filter.compute(1, envelopeValue / 65536.0F);
			blockEnd += coefficientBlockSize;
		}

		for (; sample < sampleCount; sample++) {
			int output = 0;
			for (int coefficient = sample + feedForwardCount
					- sampleCount; coefficient < feedForwardCount; coefficient++) {
				output += (int) ((long) sampleBuffer[sample + feedForwardCount - 1 - coefficient]
						* SoundFilter.coefficients[0][coefficient] >> 16);
			}
			for (int coefficient = 0; coefficient < feedbackCount; coefficient++) {
				output -= (int) ((long) sampleBuffer[sample - 1 - coefficient]
						* SoundFilter.coefficients[1][coefficient] >> 16);
			}
			sampleBuffer[sample] = output;
			filterEnvelope.step(sampleCount + 1);
		}
	}

	/** Evaluates one of the four waveform tables used by the synthesizer. */
	public int evaluateWave(int amplitude, int phase, int waveform) {
		if (waveform == 1) {
			return (phase & 0x7fff) < 16384 ? amplitude : -amplitude;
		}
		if (waveform == 2) {
			return sineTable[phase & 0x7fff] * amplitude >> 14;
		}
		if (waveform == 3) {
			return ((phase & 0x7fff) * amplitude >> 14) - amplitude;
		}
		if (waveform == 4) {
			return noiseTable[phase / 2607 & 0x7fff] * amplitude;
		}
		return 0;
	}

	/** Decodes the complete instrument definition from the sound-track stream. */
	public void decode(Buffer buffer) {
		pitchEnvelope = new SoundTrackEnvelope();
		pitchEnvelope.decode(buffer);
		volumeEnvelope = new SoundTrackEnvelope();
		volumeEnvelope.decode(buffer);

		int option = buffer.readUnsignedByte();
		if (option != 0) {
			buffer.position--;
			pitchModulationEnvelope = new SoundTrackEnvelope();
			pitchModulationEnvelope.decode(buffer);
			pitchModulationAmplitudeEnvelope = new SoundTrackEnvelope();
			pitchModulationAmplitudeEnvelope.decode(buffer);
		}
		option = buffer.readUnsignedByte();
		if (option != 0) {
			buffer.position--;
			volumeModulationEnvelope = new SoundTrackEnvelope();
			volumeModulationEnvelope.decode(buffer);
			volumeModulationAmplitudeEnvelope = new SoundTrackEnvelope();
			volumeModulationAmplitudeEnvelope.decode(buffer);
		}
		option = buffer.readUnsignedByte();
		if (option != 0) {
			buffer.position--;
			gatingReleaseEnvelope = new SoundTrackEnvelope();
			gatingReleaseEnvelope.decode(buffer);
			gatingAttackEnvelope = new SoundTrackEnvelope();
			gatingAttackEnvelope.decode(buffer);
		}

		for (int oscillator = 0; oscillator < 10; oscillator++) {
			int volume = buffer.readUnsignedSmart();
			if (volume == 0) {
				break;
			}
			oscillatorVolumes[oscillator] = volume;
			oscillatorPitchDeltas[oscillator] = buffer.readSignedSmart();
			oscillatorDelays[oscillator] = buffer.readUnsignedSmart();
		}

		delayTime = buffer.readUnsignedSmart();
		delayFeedback = buffer.readUnsignedSmart();
		durationMillis = buffer.readUnsignedShort();
		offsetMillis = buffer.readUnsignedShort();
		filter = new SoundFilter();
		filterEnvelope = new SoundTrackEnvelope();
		filter.decode(filterEnvelope, buffer);
	}
}
