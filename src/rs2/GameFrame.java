package rs2;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;

/** AWT host window for {@link GameShell}. */
public final class GameFrame extends Frame {

    private final GameShell shell;

    public GameFrame(GameShell shell, int width, int height) {
        this.shell = shell;
        setTitle("Jagex");
        setResizable(true);
        setVisible(true);
        setClientSize(width, height);
        setMinimumClientSize(width, height);
        toFront();
    }

    /** Sets the outer frame so that the drawable client area has the requested size. */
    public void setClientSize(int width, int height) {
        Insets insets = getInsets();
        setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    /** Prevents resizing below the original revision-377 client area. */
    private void setMinimumClientSize(int width, int height) {
        Insets insets = getInsets();
        setMinimumSize(new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom));
    }

    public int getClientWidth() {
        Insets insets = getInsets();
        return Math.max(1, getWidth() - insets.left - insets.right);
    }

    public int getClientHeight() {
        Insets insets = getInsets();
        return Math.max(1, getHeight() - insets.top - insets.bottom);
    }

    public int toClientX(int frameX) {
        return frameX - getInsets().left;
    }

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
