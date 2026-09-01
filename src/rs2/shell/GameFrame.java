package rs2.shell;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;

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
