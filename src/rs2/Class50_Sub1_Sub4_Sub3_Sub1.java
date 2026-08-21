package rs2;

import rs2.cache.media.AnimationSequence;
import rs2.cache.media.SpotAnimation;
import rs2.media.AnimationFrame;
import rs2.media.renderable.Model;
import rs2.media.renderable.Renderable;

public class Class50_Sub1_Sub4_Sub3_Sub1 extends Class50_Sub1_Sub4_Sub3 {

	public Model method569() {
		if (super.anInt1624 >= 0 && super.anInt1627 == 0) {
			int i = AnimationSequence.sequences[super.anInt1624].primaryFrameIds[super.anInt1625];
			int k = -1;
			if (super.anInt1588 >= 0 && super.anInt1588 != super.anInt1634)
				k = AnimationSequence.sequences[super.anInt1588].primaryFrameIds[super.anInt1589];
			return aClass37_1742.method362(i, k, 0, AnimationSequence.sequences[super.anInt1624].interleaveOrder);
		}
		int j = -1;
		if (super.anInt1588 >= 0)
			j = AnimationSequence.sequences[super.anInt1588].primaryFrameIds[super.anInt1589];
		return aClass37_1742.method362(j, -1, 0, null);
	}

	public Model getModel() {
		if (aClass37_1742 == null)
			return null;
		Model class50_sub1_sub4_sub4 = method569();
		if (class50_sub1_sub4_sub4 == null)
			return null;
		super.anInt1594 = ((Renderable) (class50_sub1_sub4_sub4)).modelHeight;
		if (super.anInt1614 != -1 && super.anInt1615 != -1) {
			SpotAnimation class27 = SpotAnimation.definitions[super.anInt1614];
			Model class50_sub1_sub4_sub4_1 = class27.getModel();
			if (class50_sub1_sub4_sub4_1 != null) {
				int i = class27.sequence.primaryFrameIds[super.anInt1615];
				Model class50_sub1_sub4_sub4_2 = new Model(class50_sub1_sub4_sub4_1, false, true,
						AnimationFrame.isNull(i));
				class50_sub1_sub4_sub4_2.translate(0, -super.anInt1618, 0);
				class50_sub1_sub4_sub4_2.createBones();
				class50_sub1_sub4_sub4_2.applyTransformation(i);
				class50_sub1_sub4_sub4_2.triangleGroups = null;
				class50_sub1_sub4_sub4_2.vertexGroups = null;
				if (class27.resizeXY != 128 || class27.resizeZ != 128)
					class50_sub1_sub4_sub4_2.scale(class27.resizeXY, class27.resizeZ, class27.resizeXY);
				class50_sub1_sub4_sub4_2.light(64 + class27.ambient, 850 + class27.contrast, -30, -50, -30, true);
				Model aclass50_sub1_sub4_sub4[] = { class50_sub1_sub4_sub4, class50_sub1_sub4_sub4_2 };
				class50_sub1_sub4_sub4 = new Model(aclass50_sub1_sub4_sub4, 2);
			}
		}
		if (aClass37_1742.aByte642 == 1)
			class50_sub1_sub4_sub4.singleTile = true;
		return class50_sub1_sub4_sub4;
	}

	public boolean method565(int i) {
		if (i != 0)
			throw new NullPointerException();
		return aClass37_1742 != null;
	}


	public Class37 aClass37_1742;
}
