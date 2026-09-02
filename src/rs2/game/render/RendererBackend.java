package rs2.game.render;

import java.util.Locale;

/**
 * Selects the 3D world-rendering backend used by {@link GameRenderer}.
 *
 * <p>
 * Phase 0 deliberately ships only the software implementation. The GPU enum
 * value is reserved now so the runtime/configuration boundary is stable before
 * native OpenGL dependencies are introduced in Phase 1.
 * </p>
 */
public enum RendererBackend {

	/** Revision-377 CPU projection and software rasterization. */
	SOFTWARE,
	/** Native GPU renderer introduced by the later GPU phases. */
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
