package rs2.game.render;

import java.util.Locale;

/**
 * Selects the 3D world-rendering backend used by {@link GameRenderer}.
 *
 * <p>
 * The software backend remains the regression-reference implementation while
 * the GPU backend is developed incrementally behind the same runtime boundary.
 * </p>
 */
public enum RendererBackend {

	/** Revision-377 CPU projection and software rasterization. */
	SOFTWARE,
	/** Native OpenGL renderer. Phase 1 currently supplies its bootstrap/test frame. */
	GPU;

	/** JVM property used to select the renderer at startup. */
	public static final String PROPERTY = "flint.renderer";

	/**
	 * Reads the requested backend from {@value #PROPERTY}.
	 *
	 * <p>
	 * The property defaults to {@code software}. Accepted values are
	 * case-insensitive enum names.
	 * </p>
	 *
	 * @return configured renderer backend
	 * @throws IllegalArgumentException if the property contains an unknown value
	 */
	public static RendererBackend configured() {
		String configured = System.getProperty(PROPERTY, "software").trim();
		try {
			return valueOf(configured.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unknown renderer backend '" + configured + "' for -D" + PROPERTY
					+ ". Expected software or gpu.", exception);
		}
	}
}
