package rs2;

import rs2.cache.media.SpotAnimation;
import rs2.media.AnimationFrame;

public class Class50_Sub1_Sub4_Sub2 extends Class50_Sub1_Sub4 {

	public void method562(int i, int j, int k, int l, int i1) {
		if (!aBoolean1575) {
			double d = i - anInt1576;
			double d2 = j - anInt1577;
			double d3 = Math.sqrt(d * d + d2 * d2);
			aDouble1555 = (double) anInt1576 + (d * (double) anInt1559) / d3;
			aDouble1556 = (double) anInt1577 + (d2 * (double) anInt1559) / d3;
			aDouble1557 = anInt1578;
		}
		double d1 = (anInt1566 + 1) - l;
		aDouble1569 = ((double) i - aDouble1555) / d1;
		aDouble1570 = ((double) j - aDouble1556) / d1;
		if (i1 != 0)
			return;
		aDouble1571 = Math.sqrt(aDouble1569 * aDouble1569 + aDouble1570 * aDouble1570);
		if (!aBoolean1575)
			aDouble1572 = -aDouble1571 * Math.tan((double) anInt1558 * 0.02454369D);
		aDouble1574 = (2D * ((double) k - aDouble1557 - aDouble1572 * d1)) / (d1 * d1);
	}

	public void method563(int i, boolean flag) {
		aBoolean1575 = true;
		aDouble1555 += aDouble1569 * (double) i;
		if (flag) {
			for (int j = 1; j > 0; j++)
				;
		}
		aDouble1556 += aDouble1570 * (double) i;
		aDouble1557 += aDouble1572 * (double) i + 0.5D * aDouble1574 * (double) i * (double) i;
		aDouble1572 += aDouble1574 * (double) i;
		anInt1562 = (int) (Math.atan2(aDouble1569, aDouble1570) * 325.94900000000001D) + 1024 & 0x7ff;
		anInt1563 = (int) (Math.atan2(aDouble1572, aDouble1571) * 325.94900000000001D) & 0x7ff;
		if (aClass27_1553.sequence != null)
			for (anInt1568 += i; anInt1568 > aClass27_1553.sequence.getFrameLength(anInt1567);) {
				anInt1568 -= aClass27_1553.sequence.getFrameLength(anInt1567);
				anInt1567++;
				if (anInt1567 >= aClass27_1553.sequence.frameCount)
					anInt1567 = 0;
			}

	}

	public Class50_Sub1_Sub4_Sub4 method561(byte byte0) {
		Class50_Sub1_Sub4_Sub4 class50_sub1_sub4_sub4 = aClass27_1553.getModel();
		if (class50_sub1_sub4_sub4 == null)
			return null;
		int i = -1;
		if (aClass27_1553.sequence != null)
			i = aClass27_1553.sequence.primaryFrameIds[anInt1567];
		Class50_Sub1_Sub4_Sub4 class50_sub1_sub4_sub4_1 = new Class50_Sub1_Sub4_Sub4(false, false, true,
				class50_sub1_sub4_sub4, AnimationFrame.isNull(i));
		if (i != -1) {
			class50_sub1_sub4_sub4_1.method584(7);
			class50_sub1_sub4_sub4_1.method585(i, (byte) 6);
			class50_sub1_sub4_sub4_1.anIntArrayArray1679 = null;
			class50_sub1_sub4_sub4_1.anIntArrayArray1678 = null;
		}
		if (aClass27_1553.resizeXY != 128 || aClass27_1553.resizeZ != 128)
			class50_sub1_sub4_sub4_1.method593(aClass27_1553.resizeZ, aClass27_1553.resizeXY, 9,
					aClass27_1553.resizeXY);
		class50_sub1_sub4_sub4_1.method589(anInt1563, 341);
		class50_sub1_sub4_sub4_1.method594(64 + aClass27_1553.ambient, 850 + aClass27_1553.contrast, -30, -50, -30,
				true);
		if (byte0 == 3)
			byte0 = 0;
		else
			aBoolean1561 = !aBoolean1561;
		return class50_sub1_sub4_sub4_1;
	}

	public Class50_Sub1_Sub4_Sub2(int i, int j, int k, int l, int i1, int j1, int k1, int l1, byte byte0, int i2,
			int j2, int k2) {
		aBoolean1561 = false;
		aByte1564 = -41;
		aBoolean1575 = false;
		aClass27_1553 = SpotAnimation.definitions[i1];
		anInt1554 = i;
		anInt1576 = j2;
		anInt1577 = l;
		anInt1578 = i2;
		anInt1565 = k2;
		anInt1566 = j1;
		if (byte0 != aByte1564) {
			throw new NullPointerException();
		} else {
			anInt1558 = k1;
			anInt1559 = k;
			anInt1560 = l1;
			anInt1579 = j;
			aBoolean1575 = false;
			return;
		}
	}

	public SpotAnimation aClass27_1553;
	public int anInt1554;
	public double aDouble1555;
	public double aDouble1556;
	public double aDouble1557;
	public int anInt1558;
	public int anInt1559;
	public int anInt1560;
	public boolean aBoolean1561;
	public int anInt1562;
	public int anInt1563;
	public byte aByte1564;
	public int anInt1565;
	public int anInt1566;
	public int anInt1567;
	public int anInt1568;
	public double aDouble1569;
	public double aDouble1570;
	public double aDouble1571;
	public double aDouble1572;
	public double aDouble1574;
	public boolean aBoolean1575;
	public int anInt1576;
	public int anInt1577;
	public int anInt1578;
	public int anInt1579;
}
