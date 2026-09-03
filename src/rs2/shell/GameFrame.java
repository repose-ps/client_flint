package rs2.shell;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.DisplayMode;

/** AWT host window for {@link GameShell}. */
public final class GameFrame extends Frame {

	/** Stores the current shell. */
	private final GameShell shell;

	/**
	 * Creates a new game frame.
	 *
	 * @param shell  the shell
	 * @param width  the width in pixels
	 * @param height the height in pixels
	 */
	public GameFrame(GameShell shell, int width, int height) {
		this.shell = shell;
		setLayout(null);
		setTitle("Jagex");
		setResizable(true);
		setVisible(true);
		setClientSize(width, height);
		setMinimumClientSize(width, height);
		toFront();
	}

	/**
	 * Sets the outer frame so that the drawable client area has the requested size.
	 * 
	 * @param width  the width in pixels
	 * @param height the height in pixels
	 */
	public void setClientSize(int width, int height) {
		Insets insets = getInsets();
		setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
	}

	/**
	 * Prevents resizing below the original revision-377 client area.
	 * 
	 * @param width  the width in pixels
	 * @param height the height in pixels
	 */
	private void setMinimumClientSize(int width, int height) {
		Insets insets = getInsets();
		setMinimumSize(new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom));
	}

	/**
	 * Returns client width.
	 *
	 * @return the client width
	 */
	public int getClientWidth() {
		Insets insets = getInsets();
		return Math.max(1, getWidth() - insets.left - insets.right);
	}

	/**
	 * Returns client height.
	 *
	 * @return the client height
	 */
	public int getClientHeight() {
		Insets insets = getInsets();
		return Math.max(1, getHeight() - insets.top - insets.bottom);
	}

	/** Returns the active display refresh rate, or zero when the platform does not report one. */
	public int getDisplayRefreshRate() {
		if (getGraphicsConfiguration() == null || getGraphicsConfiguration().getDevice() == null) {
			return 0;
		}
		DisplayMode mode = getGraphicsConfiguration().getDevice().getDisplayMode();
		if (mode == null || mode.getRefreshRate() == DisplayMode.REFRESH_RATE_UNKNOWN) {
			return 0;
		}
		return Math.max(0, mode.getRefreshRate());
	}

	/**
	 * Converts a frame-relative X coordinate to client-area coordinates.
	 *
	 * @param frameX the frame X
	 * @return the converted value
	 */
	public int toClientX(int frameX) {
		return frameX - getInsets().left;
	}

	/**
	 * Converts a frame-relative Y coordinate to client-area coordinates.
	 *
	 * @param frameY the frame Y
	 * @return the converted value
	 */
	public int toClientY(int frameY) {
		return frameY - getInsets().top;
	}

	/** Installs a child rendering surface above the legacy AWT presentation. */
	public void installRenderOverlay(Component component) {
		add(component);
		component.setVisible(false);
		component.addMouseListener(shell);
		component.addMouseMotionListener(shell);
		component.addKeyListener(shell);
		component.addFocusListener(shell);
		validate();
	}

	/** Positions a child rendering surface in client-area coordinates. */
	public void positionRenderOverlay(Component component, int x, int y, int width, int height) {
		Insets insets = getInsets();
		component.setBounds(insets.left + x, insets.top + y, Math.max(1, width), Math.max(1, height));
		component.setVisible(true);
		component.validate();
	}

	/** Removes a previously installed child rendering surface. */
	public void removeRenderOverlay(Component component) {
		remove(component);
		validate();
		repaint();
	}

	@Override
	public Graphics getGraphics() {
		Graphics graphics = super.getGraphics();
		if (graphics != null) {
			Insets insets = getInsets();
			graphics.translate(insets.left, insets.top);
		}
		return graphics;
	}

	@Override
	public void update(Graphics graphics) {
		shell.update(graphics);
	}

	@Override
	public void paint(Graphics graphics) {
		shell.paint(graphics);
	}
}
