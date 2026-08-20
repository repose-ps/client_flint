package rs2;

import java.awt.Frame;
import java.awt.Graphics;

public class Frame_Sub1 extends Frame {

	public Frame_Sub1(int i, int j, Applet_Sub1 applet_sub1, int k) {
		anApplet_Sub1_37 = applet_sub1;
		setTitle("Jagex");
		setResizable(false);
		show();
		if (i != 3) {
			throw new NullPointerException();
		} else {
			toFront();
			resize(k + 8, j + 28);
			return;
		}
	}

	public Graphics getGraphics() {
		Graphics g = super.getGraphics();
		g.translate(4, 24);
		return g;
	}

	public void update(Graphics g) {
		anApplet_Sub1_37.update(g);
	}

	public void paint(Graphics g) {
		anApplet_Sub1_37.paint(g);
	}

	public Applet_Sub1 anApplet_Sub1_37;
}
