package rs2.cache.def;

import rs2.cache.Archive;
import rs2.net.Buffer;

/**
 * Definition of a map floor type loaded from {@code flo.dat}.
 *
 * <p>
 * The cache stores colors as 24-bit RGB. The client converts them to its
 * compact 16-bit HSL palette representation and adds small random variations to
 * reduce visible colour banding between otherwise identical tiles.
 * </p>
 */
public class FloorDefinition {
	/* Cache-format opcode values. */
	/** Opcode for end. */
	private static final int OPCODE_END = 0;
	/** Opcode for rgb color. */
	private static final int OPCODE_RGB_COLOR = 1;
	/** Opcode for texture. */
	private static final int OPCODE_TEXTURE = 2;
	/** Opcode for unknown 3. */
	private static final int OPCODE_UNKNOWN_3 = 3;
	/** Opcode for disable occlusion. */
	private static final int OPCODE_DISABLE_OCCLUSION = 5;
	/** Opcode for name. */
	private static final int OPCODE_NAME = 6;
	/** Opcode for alternate color. */
	private static final int OPCODE_ALTERNATE_COLOR = 7;

	/** Creates a new floor definition with its default client state. */
	public FloorDefinition() {
	}

	/** Legacy definition flag retained from the revision-377 format. */
	public boolean enabled = true;

	/** Number of floor definitions declared by the cache. */
	public static int count;

	/** Definitions indexed by floor identifier. */
	public static FloorDefinition[] definitions;

	/** Stores the current name. */
	public String name;
	/** Stores the current RGB color. */
	public int rgbColor;
	/** Stores the current texture ID. */
	public int textureId = -1;
	/** Whether opcode3 enabled is enabled or active. */
	public boolean opcode3Enabled;
	/** Whether occlude is enabled or active. */
	public boolean occlude = true;

	/** Hue scaled to the range 0 through 255. */
	public int hue;

	/** Saturation scaled to the range 0 through 255. */
	public int saturation;

	/** Lightness scaled to the range 0 through 255. */
	public int lightness;

	/** Hue weighted by {@link #hueMultiplier}. */
	public int weightedHue;

	/** Saturation/lightness-derived multiplier used for hue blending. */
	public int hueMultiplier;

	/** Randomized color encoded for the client's 16-bit HSL palette. */
	public int randomizedPackedHsl;

	/**
	 * Loads all floor definitions from {@code flo.dat}.
	 * 
	 * @param archive the source archive
	 */
	public static void load(Archive archive) {
		Buffer buffer = new Buffer(archive.read("flo.dat"));
		count = buffer.readUnsignedShort();

		if (definitions == null) {
			definitions = new FloorDefinition[count];
		}

		for (int id = 0; id < count; id++) {
			if (definitions[id] == null) {
				definitions[id] = new FloorDefinition();
			}
			definitions[id].decode(buffer);
		}
	}

	/**
	 * Decodes one opcode-delimited floor definition.
	 * 
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			switch (opcode) {
			case OPCODE_END:
				return;
			case OPCODE_RGB_COLOR:
				rgbColor = buffer.readMedium();
				convertRgbToHsl(rgbColor);
				break;
			case OPCODE_TEXTURE:
				textureId = buffer.readUnsignedByte();
				break;
			case OPCODE_UNKNOWN_3:
				opcode3Enabled = true;
				break;
			case OPCODE_DISABLE_OCCLUSION:
				occlude = false;
				break;
			case OPCODE_NAME:
				name = buffer.readString();
				break;
			case OPCODE_ALTERNATE_COLOR:
				decodeAlternateColor(buffer.readMedium());
				break;
			default:
				System.out.println("Error unrecognised config code: " + opcode);
				break;
			}
		}
	}

	/**
	 * Decodes opcode 7's alternate color while retaining the primary HSL values.
	 *
	 * <p>
	 * This deliberately preserves the revision-377 assignment that copies the
	 * restored weighted hue into {@link #hueMultiplier}.
	 * </p>
	 * 
	 * @param alternateRgb the alternate RGB
	 */
	private void decodeAlternateColor(int alternateRgb) {
		int primaryHue = hue;
		int primarySaturation = saturation;
		int primaryLightness = lightness;
		int primaryWeightedHue = weightedHue;

		convertRgbToHsl(alternateRgb);

		hue = primaryHue;
		saturation = primarySaturation;
		lightness = primaryLightness;
		weightedHue = primaryWeightedHue;
		hueMultiplier = primaryWeightedHue;
	}

	/**
	 * Converts a 24-bit RGB value into the client's HSL color representation.
	 * 
	 * @param rgb the RGB color value
	 */
	public void convertRgbToHsl(int rgb) {
		// Magenta is the cache's transparent-color marker.
		if (rgb == 0xff00ff) {
			rgb = 0;
		}

		double red = (double) (rgb >> 16 & 0xff) / 256.0;
		double green = (double) (rgb >> 8 & 0xff) / 256.0;
		double blue = (double) (rgb & 0xff) / 256.0;
		double minimum = Math.min(red, Math.min(green, blue));
		double maximum = Math.max(red, Math.max(green, blue));
		double hueFraction = 0.0;
		double saturationFraction = 0.0;
		double lightnessFraction = (minimum + maximum) / 2.0;

		if (minimum != maximum) {
			if (lightnessFraction < 0.5) {
				saturationFraction = (maximum - minimum) / (maximum + minimum);
			} else {
				saturationFraction = (maximum - minimum) / (2.0 - maximum - minimum);
			}

			if (red == maximum) {
				hueFraction = (green - blue) / (maximum - minimum);
			} else if (green == maximum) {
				hueFraction = 2.0 + (blue - red) / (maximum - minimum);
			} else {
				hueFraction = 4.0 + (red - green) / (maximum - minimum);
			}
		}

		hueFraction /= 6.0;
		hue = (int) (hueFraction * 256.0);
		saturation = clamp((int) (saturationFraction * 256.0), 0, 255);
		lightness = clamp((int) (lightnessFraction * 256.0), 0, 255);

		if (lightnessFraction > 0.5) {
			hueMultiplier = (int) ((1.0 - lightnessFraction) * saturationFraction * 512.0);
		} else {
			hueMultiplier = (int) (lightnessFraction * saturationFraction * 512.0);
		}
		if (hueMultiplier < 1) {
			hueMultiplier = 1;
		}
		weightedHue = (int) (hueFraction * hueMultiplier);

		int randomizedHue = clamp(hue + (int) (Math.random() * 16.0) - 8, 0, 255);
		int randomizedSaturation = clamp(saturation + (int) (Math.random() * 48.0) - 24, 0, 255);
		int randomizedLightness = clamp(lightness + (int) (Math.random() * 48.0) - 24, 0, 255);
		randomizedPackedHsl = packHsl(randomizedHue, randomizedSaturation, randomizedLightness);
	}

	/**
	 * Packs 8-bit HSL components into the client's 16-bit palette index.
	 * 
	 * @param hue        the hue
	 * @param saturation the saturation
	 * @param lightness  the lightness
	 * @return the packed 16-bit HSL palette value
	 */
	public static int packHsl(int hue, int saturation, int lightness) {
		if (lightness > 179) {
			saturation /= 2;
		}
		if (lightness > 192) {
			saturation /= 2;
		}
		if (lightness > 217) {
			saturation /= 2;
		}
		if (lightness > 243) {
			saturation /= 2;
		}
		return (hue / 4 << 10) + (saturation / 32 << 7) + lightness / 2;
	}

	/**
	 * Clamps the operation.
	 *
	 * @param value   the value
	 * @param minimum the minimum
	 * @param maximum the maximum
	 * @return the value constrained to the inclusive range
	 */
	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
