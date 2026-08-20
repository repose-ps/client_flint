// Decompiled by Jad v1.5.8f. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
package rs2;

import rs2.cache.media.AnimationSequence;
import rs2.cache.media.SpotAnimation;
import rs2.sign.signlink;

public class Class50_Sub1_Sub4_Sub3_Sub1 extends Class50_Sub1_Sub4_Sub3
{

    public Class50_Sub1_Sub4_Sub4 method569(byte byte0)
    {
        if(super.anInt1624 >= 0 && super.anInt1627 == 0)
        {
            int i = AnimationSequence.sequences[super.anInt1624].primaryFrameIds[super.anInt1625];
            int k = -1;
            if(super.anInt1588 >= 0 && super.anInt1588 != super.anInt1634)
                k = AnimationSequence.sequences[super.anInt1588].primaryFrameIds[super.anInt1589];
            return aClass37_1742.method362(i, k, 0, AnimationSequence.sequences[super.anInt1624].interleaveOrder);
        }
        int j = -1;
        if(byte0 != 122)
            aBoolean1741 = !aBoolean1741;
        if(super.anInt1588 >= 0)
            j = AnimationSequence.sequences[super.anInt1588].primaryFrameIds[super.anInt1589];
        return aClass37_1742.method362(j, -1, 0, null);
    }

    public Class50_Sub1_Sub4_Sub4 method561(byte byte0)
    {
        if(byte0 == 3)
            byte0 = 0;
        else
            throw new NullPointerException();
        if(aClass37_1742 == null)
            return null;
        Class50_Sub1_Sub4_Sub4 class50_sub1_sub4_sub4 = method569((byte)122);
        if(class50_sub1_sub4_sub4 == null)
            return null;
        super.anInt1594 = ((Class50_Sub1_Sub4) (class50_sub1_sub4_sub4)).anInt1475;
        if(super.anInt1614 != -1 && super.anInt1615 != -1)
        {
            SpotAnimation class27 = SpotAnimation.definitions[super.anInt1614];
            Class50_Sub1_Sub4_Sub4 class50_sub1_sub4_sub4_1 = class27.getModel();
            if(class50_sub1_sub4_sub4_1 != null)
            {
                int i = class27.sequence.primaryFrameIds[super.anInt1615];
                Class50_Sub1_Sub4_Sub4 class50_sub1_sub4_sub4_2 = new Class50_Sub1_Sub4_Sub4(false, false, true, class50_sub1_sub4_sub4_1, Class21.method239(aBoolean1741, i));
                class50_sub1_sub4_sub4_2.method590(0, 0, false, -super.anInt1618);
                class50_sub1_sub4_sub4_2.method584(7);
                class50_sub1_sub4_sub4_2.method585(i, (byte)6);
                class50_sub1_sub4_sub4_2.anIntArrayArray1679 = null;
                class50_sub1_sub4_sub4_2.anIntArrayArray1678 = null;
                if(class27.resizeXY != 128 || class27.resizeZ != 128)
                    class50_sub1_sub4_sub4_2.method593(class27.resizeZ, class27.resizeXY, 9, class27.resizeXY);
                class50_sub1_sub4_sub4_2.method594(64 + class27.ambient, 850 + class27.contrast, -30, -50, -30, true);
                Class50_Sub1_Sub4_Sub4 aclass50_sub1_sub4_sub4[] = {
                    class50_sub1_sub4_sub4, class50_sub1_sub4_sub4_2
                };
                class50_sub1_sub4_sub4 = new Class50_Sub1_Sub4_Sub4(2, true, 0, aclass50_sub1_sub4_sub4);
            }
        }
        if(aClass37_1742.aByte642 == 1)
            class50_sub1_sub4_sub4.aBoolean1680 = true;
        return class50_sub1_sub4_sub4;
    }

    public boolean method565(int i)
    {
        if(i != 0)
            throw new NullPointerException();
        return aClass37_1742 != null;
    }

    public Class50_Sub1_Sub4_Sub3_Sub1()
    {
        aBoolean1741 = true;
    }

    public boolean aBoolean1741;
    public Class37 aClass37_1742;
}
