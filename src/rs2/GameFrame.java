package rs2;

import java.awt.Frame;
import java.awt.Graphics;

/**
 * Fixed-size AWT host window for {@link GameShell}.
 *
 * <p>
 * The hard-coded frame dimensions and graphics translation are preserved from
 * the revision-377 standalone client rather than replaced with dynamic inset
 * calculations.
 * </p>
 */
public final class GameFrame extends Frame {

	private final GameShell shell;

	public GameFrame(GameShell shell, int width, int height) {
		this.shell = shell;
		setTitle("Jagex");
		setResizable(false);
		setVisible(true);
		toFront();
		setSize(width + 8, height + 28);
	}

	@Override
	public Graphics getGraphics() {
		Graphics graphics = super.getGraphics();
		if (graphics != null)
			graphics.translate(4, 24);
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
