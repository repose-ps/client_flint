package rs2.sound;

import rs2.net.Buffer;

/**
 * Time-varying cascaded biquad filter used by synthesized sound effects.
 *
 * <p>
 * The two directions form the feed-forward and feedback sides of the final
 * recursive filter. Each side supports up to four pole pairs. Coefficients are
 * calculated in floating point and exported as 16.16 fixed-point integers for
 * the sample loop.
 * </p>
 */
public class SoundFilter {

	/** Number of pole pairs on each side of the recursive filter. */
	public int[] pairCount;

	/** Start and end frequency parameters for each pole pair. */
	public int[][][] pairFrequencies;

	/** Start and end attenuation parameters for each pole pair. */
	public int[][][] pairAttenuations;

	/** Start and end global gain values for the feed-forward side. */
	public int[] unityGain;

	/** Working floating-point coefficients for both filter directions. */
	public static float[][] floatingCoefficients = new float[2][8];

	/** 16.16 fixed-point coefficients consumed by the synthesizer. */
	public static int[][] coefficients = new int[2][8];

	/** Interpolated inverse global gain in floating-point form. */
	public static float floatingInverseUnity;

	/** Interpolated inverse global gain in 16.16 fixed-point form. */
	public static int inverseUnity;

	public SoundFilter() {
		pairCount = new int[2];
		pairFrequencies = new int[2][2][4];
		pairAttenuations = new int[2][2][4];
		unityGain = new int[2];
	}

	private float interpolateAttenuation(int direction, int pair, float interpolation) {
		float attenuation = pairAttenuations[direction][0][pair]
				+ interpolation * (pairAttenuations[direction][1][pair] - pairAttenuations[direction][0][pair]);
		attenuation *= 0.001525879F;
		return 1.0F - (float) Math.pow(10.0D, -attenuation / 20.0F);
	}

	private static float normalizeFrequency(float value) {
		float frequency = 32.7032F * (float) Math.pow(2.0D, value);
		return frequency * 3.141593F / 11025.0F;
	}

	private float interpolateFrequency(int direction, int pair, float interpolation) {
		float frequency = pairFrequencies[direction][0][pair]
				+ interpolation * (pairFrequencies[direction][1][pair] - pairFrequencies[direction][0][pair]);
		frequency *= 0.0001220703F;
		return normalizeFrequency(frequency);
	}

	/**
	 * Computes coefficients for one filter direction at an envelope position.
	 *
	 * @return twice the number of pole pairs, which is the coefficient count
	 */
	public int compute(int direction, float interpolation) {
		if (direction == 0) {
			float gain = unityGain[0] + (unityGain[1] - unityGain[0]) * interpolation;
			gain *= 0.003051758F;
			floatingInverseUnity = (float) Math.pow(0.1D, gain / 20.0F);
			inverseUnity = (int) (floatingInverseUnity * 65536.0F);
		}
		if (pairCount[direction] == 0) {
			return 0;
		}

		float magnitude = interpolateAttenuation(direction, 0, interpolation);
		floatingCoefficients[direction][0] = -2.0F * magnitude
				* (float) Math.cos(interpolateFrequency(direction, 0, interpolation));
		floatingCoefficients[direction][1] = magnitude * magnitude;

		for (int pair = 1; pair < pairCount[direction]; pair++) {
			magnitude = interpolateAttenuation(direction, pair, interpolation);
			float phaseCoefficient = -2.0F * magnitude
					* (float) Math.cos(interpolateFrequency(direction, pair, interpolation));
			float magnitudeSquared = magnitude * magnitude;
			floatingCoefficients[direction][pair * 2 + 1] = floatingCoefficients[direction][pair * 2 - 1]
					* magnitudeSquared;
			floatingCoefficients[direction][pair * 2] = floatingCoefficients[direction][pair * 2 - 1] * phaseCoefficient
					+ floatingCoefficients[direction][pair * 2 - 2] * magnitudeSquared;
			for (int coefficient = pair * 2 - 1; coefficient >= 2; coefficient--) {
				floatingCoefficients[direction][coefficient] += floatingCoefficients[direction][coefficient - 1]
						* phaseCoefficient + floatingCoefficients[direction][coefficient - 2] * magnitudeSquared;
			}
			floatingCoefficients[direction][1] += floatingCoefficients[direction][0] * phaseCoefficient
					+ magnitudeSquared;
			floatingCoefficients[direction][0] += phaseCoefficient;
		}

		if (direction == 0) {
			for (int coefficient = 0; coefficient < pairCount[0] * 2; coefficient++) {
				floatingCoefficients[0][coefficient] *= floatingInverseUnity;
			}
		}
		for (int coefficient = 0; coefficient < pairCount[direction] * 2; coefficient++) {
			coefficients[direction][coefficient] = (int) (floatingCoefficients[direction][coefficient] * 65536.0F);
		}
		return pairCount[direction] * 2;
	}

	/** Decodes pair parameters and any envelope-controlled alternate values. */
	public void decode(SoundTrackEnvelope envelope, Buffer buffer) {
		int packedPairCount = buffer.readUnsignedByte();
		pairCount[0] = packedPairCount >> 4;
		pairCount[1] = packedPairCount & 0xf;
		if (packedPairCount == 0) {
			unityGain[0] = unityGain[1] = 0;
			return;
		}

		unityGain[0] = buffer.readUnsignedShort();
		unityGain[1] = buffer.readUnsignedShort();
		int alternateMask = buffer.readUnsignedByte();
		for (int direction = 0; direction < 2; direction++) {
			for (int pair = 0; pair < pairCount[direction]; pair++) {
				pairFrequencies[direction][0][pair] = buffer.readUnsignedShort();
				pairAttenuations[direction][0][pair] = buffer.readUnsignedShort();
			}
		}
		for (int direction = 0; direction < 2; direction++) {
			for (int pair = 0; pair < pairCount[direction]; pair++) {
				if ((alternateMask & 1 << direction * 4 << pair) != 0) {
					pairFrequencies[direction][1][pair] = buffer.readUnsignedShort();
					pairAttenuations[direction][1][pair] = buffer.readUnsignedShort();
				} else {
					pairFrequencies[direction][1][pair] = pairFrequencies[direction][0][pair];
					pairAttenuations[direction][1][pair] = pairAttenuations[direction][0][pair];
				}
			}
		}
		if (alternateMask != 0 || unityGain[1] != unityGain[0]) {
			envelope.decodeSegments(buffer);
		}
	}
}